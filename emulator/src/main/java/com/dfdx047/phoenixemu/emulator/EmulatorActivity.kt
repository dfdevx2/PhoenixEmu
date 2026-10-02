package com.dfdx047.phoenixemu.emulator

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import java.io.File

/**
 * Host da emulacao.
 *
 * ESTADO: esqueleto (Fase 4a). Ela ja nasce com as tres decisoes que sao
 * caras de mudar depois:
 *
 *  1. **Processo separado** (`android:process=":emu"` no manifesto). Nucleos
 *     libretro tem estado global e `dlclose` nao o limpa de forma confiavel.
 *     Um processo novo por sessao garante inicio limpo, e um crash do nucleo
 *     nao derruba a biblioteca.
 *  2. **SurfaceView, nao TextureView.** TextureView passa por uma composicao
 *     extra e acrescenta latencia.
 *  3. **O overlay de toque e Compose POR CIMA da SurfaceView**, nunca
 *     dentro dela.
 */
class EmulatorActivity : ComponentActivity() {

    private val nucleo = NucleoLibretro()
    private var aspectRatio by mutableFloatStateOf(4f / 3f)
    private var activeSurfaceHolder: SurfaceHolder? = null
    
    private var isPaused by mutableStateOf(false)
    private var keyMask = 0
    private var axisMask = 0
    private val keyToBit = mutableMapOf<Int, Int>()

    private fun bindKey(keyCode: Int, bit: Int) {
        keyToBit[keyCode] = (keyToBit[keyCode] ?: 0) or bit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        
        val mapKeys = intent.getIntArrayExtra(EXTRA_MAPEAMENTO)
        if (mapKeys != null && mapKeys.size >= 12) {
            bindKey(mapKeys[0], NucleoLibretro.Botao.CIMA)
            bindKey(mapKeys[1], NucleoLibretro.Botao.BAIXO)
            bindKey(mapKeys[2], NucleoLibretro.Botao.ESQUERDA)
            bindKey(mapKeys[3], NucleoLibretro.Botao.DIREITA)
            bindKey(mapKeys[4], NucleoLibretro.Botao.A)
            bindKey(mapKeys[5], NucleoLibretro.Botao.B)
            bindKey(mapKeys[6], NucleoLibretro.Botao.X)
            bindKey(mapKeys[7], NucleoLibretro.Botao.Y)
            bindKey(mapKeys[8], NucleoLibretro.Botao.L)
            bindKey(mapKeys[9], NucleoLibretro.Botao.R)
            bindKey(mapKeys[10], NucleoLibretro.Botao.SELECT)
            bindKey(mapKeys[11], NucleoLibretro.Botao.START)
        }

        val nucleoName = intent.getStringExtra(EXTRA_NUCLEO) ?: ""
        val libraryPath = "${applicationInfo.nativeLibraryDir}/$nucleoName"
        val file = File(libraryPath)

        var displayMessage = ""

        if (!file.exists()) {
            displayMessage = "Núcleo não encontrado: $libraryPath"
        } else {
            val systemDir = File(filesDir, "system").apply { mkdirs() }
            val savesDir = File(filesDir, "saves").apply { mkdirs() }

            try {
                nucleo.definirPastas(systemDir.absolutePath, savesDir.absolutePath)
                if (!nucleo.carregar(libraryPath)) {
                    displayMessage = "Falha ao carregar o núcleo."
                } else if (!nucleo.carregarJogo(null)) {
                    displayMessage = "Falha ao carregar o jogo."
                } else {
                    displayMessage = nucleo.info()
                    aspectRatio = nucleo.obterAspectRatio()
                }
            } catch (e: Exception) {
                displayMessage = "Erro: ${e.message}"
            }
        }

        setContent {
            val focusRequester = remember { FocusRequester() }

            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    activeSurfaceHolder = holder
                                    nucleo.iniciar(holder.surface)
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    activeSurfaceHolder = null
                                    nucleo.parar()
                                }
                            })
                        }
                    },
                    modifier = Modifier.aspectRatio(aspectRatio)
                )

                if (isPaused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BasicText(
                                text = displayMessage,
                                style = TextStyle(color = Color.White, fontSize = 12.sp)
                            )
                            Button(
                                onClick = { isPaused = false },
                                modifier = Modifier.focusRequester(focusRequester)
                            ) { Text("Continuar") }
                            Button(onClick = { 
                                isPaused = false
                                nucleo.reiniciar()
                            }) { Text("Reiniciar") }
                            Button(onClick = { finish() }) { Text("Sair") }
                        }
                    }
                    LaunchedEffect(Unit) {
                        try { focusRequester.requestFocus() } catch (e: Exception) {}
                    }
                }
            }

            LaunchedEffect(isPaused) {
                keyMask = 0
                axisMask = 0
                nucleo.definirBotoes(0, 0)

                if (isPaused) {
                    nucleo.parar()
                } else {
                    activeSurfaceHolder?.surface?.let {
                        if (it.isValid) nucleo.iniciar(it)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nucleo.parar()
    }

    override fun onResume() {
        super.onResume()
        if (!isPaused) {
            activeSurfaceHolder?.surface?.let {
                if (it.isValid) {
                    nucleo.iniciar(it)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nucleo.parar()
        nucleo.descarregar()
        if (isFinishing) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                isPaused = !isPaused
            }
            return true
        }

        if (isPaused) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                val newEvent = KeyEvent(event.action, KeyEvent.KEYCODE_DPAD_CENTER)
                return super.dispatchKeyEvent(newEvent)
            }
            return super.dispatchKeyEvent(event)
        }

        val bit = keyToBit[event.keyCode]
        if (bit != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                keyMask = keyMask or bit
                nucleo.definirBotoes(0, keyMask or axisMask)
            } else if (event.action == KeyEvent.ACTION_UP) {
                keyMask = keyMask and bit.inv()
                nucleo.definirBotoes(0, keyMask or axisMask)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isPaused) return super.dispatchGenericMotionEvent(event)

        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            val xaxis = event.getAxisValue(MotionEvent.AXIS_X)
            val yaxis = event.getAxisValue(MotionEvent.AXIS_Y)
            val hatx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val haty = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            var newAxisMask = 0
            if (xaxis < -0.5f || hatx < -0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.ESQUERDA
            if (xaxis > 0.5f || hatx > 0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.DIREITA
            if (yaxis < -0.5f || haty < -0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.CIMA
            if (yaxis > 0.5f || haty > 0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.BAIXO

            if (newAxisMask != axisMask) {
                axisMask = newAxisMask
                nucleo.definirBotoes(0, keyMask or axisMask)
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * O contrato com o app. O app nao ve este modulo por dentro nem o
     * contrario, entao tudo o que a Activity precisa chega por aqui.
     *
     * NAO RENOMEIE estes nomes: o app (`emulacao/Emulador.kt`) monta a Intent
     * com eles, e a ordem de ORDEM_DO_MAPEAMENTO e conferida la por nome.
     */
    companion object {
        /** Nome do arquivo .so dentro de nativeLibraryDir. Ex.: "libmesen.so". */
        const val EXTRA_NUCLEO = "phoenix.nucleo"

        /** URI (content://) da ROM. Ausente = rodar sem jogo (nucleo falso). */
        const val EXTRA_ROM = "phoenix.rom"

        /** Nome-base para os arquivos de save (.srm e save states). */
        const val EXTRA_NOME_SAVE = "phoenix.nome_save"

        /** IntArray de 12 keycodes, na ordem de ORDEM_DO_MAPEAMENTO. */
        const val EXTRA_MAPEAMENTO = "phoenix.mapeamento"

        /**
         * JSON do controle na tela, no formato:
         * {"visivel":true,"opacidade":0.55,"escala":1.0,
         *  "posicoes":[{"botao":"CIMA","x":0.12,"y":0.58}, ...]}
         * x e y sao fracoes (0..1) da tela inteira. Leia com org.json.
         */
        const val EXTRA_OVERLAY = "phoenix.overlay"

        val ORDEM_DO_MAPEAMENTO = listOf(
            "CIMA", "BAIXO", "ESQUERDA", "DIREITA",
            "A", "B", "X", "Y", "L", "R", "SELECT", "START"
        )
    }
}
