package com.dfdx047.phoenixemu.emulator

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    nucleo.iniciar(holder.surface)
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    nucleo.parar()
                                }
                            })
                        }
                    },
                    modifier = Modifier.aspectRatio(aspectRatio)
                )

                BasicText(
                    text = displayMessage,
                    style = TextStyle(color = Color.White, fontSize = 10.sp),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
            }
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
