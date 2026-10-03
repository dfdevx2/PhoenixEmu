package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import org.json.JSONObject
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.dfdx047.phoenixemu.Acabamento
import com.dfdx047.phoenixemu.TemaApp
import com.dfdx047.phoenixemu.ui.theme.PhoenixEmuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

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
    private var activeSurfaceView: SurfaceView? = null
    private var lastCapturedBitmap: Bitmap? = null

    private var isPaused by mutableStateOf(false)
    private var menuState by mutableStateOf(MenuState.MAIN)
    private var mensagemFeedback by mutableStateOf("")
    private var slotsInfo by mutableStateOf<List<SlotData>>(emptyList())

    private var keyMask = 0
    private var axisMask = 0
    private val keyToBit = mutableMapOf<Int, Int>()

    private var srmPath: String? = null
    private var isJogoReal = false
    private var precisaAvisoAutoload = false
    private var autosavePendente = false

    private val atalhos = mutableMapOf<Acao, List<Int>>()
    private val teclasPressionadas = mutableSetOf<Int>()
    private val teclasConsumidas = mutableSetOf<Int>()
    private val acoesDisparadas = mutableSetOf<Acao>()

    private var leftTriggerPressed = false
    private var rightTriggerPressed = false
    private var dpadUpPressed = false
    private var dpadDownPressed = false
    private var dpadLeftPressed = false
    private var dpadRightPressed = false

    private var avisoTexto by mutableStateOf("")
    private var avisoJob: kotlinx.coroutines.Job? = null
    private var slotAtual = 1
    private var estadoPendingJob: kotlinx.coroutines.Job? = null
    
    private var armazemDeEstados: ArmazemDeEstados? = null

    private var ffJob: kotlinx.coroutines.Job? = null
    private var ffSpeed by mutableStateOf(1)
    private var statsText by mutableStateOf("")

    private var rewindJob: kotlinx.coroutines.Job? = null
    private var rewindActive = false
    private var rewindSecs by mutableStateOf(0f)

    private fun setFfActive(active: Boolean) {
        val shouldBeActive = active && !rewindActive
        if (shouldBeActive && ffSpeed == 1) {
            ffSpeed = 2
            nucleo.definirAvancoRapido(2)
            ffJob?.cancel()
            ffJob = lifecycleScope.launch {
                delay(1500)
                if (ffSpeed > 1) {
                    ffSpeed = 4
                    nucleo.definirAvancoRapido(4)
                }
                delay(1500)
                if (ffSpeed > 1) {
                    ffSpeed = 8
                    nucleo.definirAvancoRapido(8)
                }
            }
        } else if (!shouldBeActive && ffSpeed > 1) {
            ffJob?.cancel()
            ffJob = null
            ffSpeed = 1
            nucleo.definirAvancoRapido(1)
        }
    }

    private fun setRewindActive(active: Boolean) {
        if (active && !rewindActive) {
            rewindActive = true
            nucleo.definirRewind(true)
            setFfActive(false)
            
            rewindJob?.cancel()
            rewindJob = lifecycleScope.launch {
                while (true) {
                    rewindSecs = nucleo.obterRewindSegundos()
                    delay(250)
                }
            }
        } else if (!active && rewindActive) {
            rewindActive = false
            nucleo.definirRewind(false)
            rewindJob?.cancel()
            rewindJob = null
        }
    }

    private fun processarEntradaVirtual(keyCode: Int, isDown: Boolean) {
        if (isDown) {
            teclasPressionadas.add(keyCode)
        } else {
            teclasPressionadas.remove(keyCode)
            teclasConsumidas.remove(keyCode)
        }
        verificarAtalhos()
    }

    private fun verificarAtalhos() {
        if (isPaused) return

        val acoesAtivas = mutableSetOf<Acao>()

        for ((acao, combo) in atalhos) {
            if (combo.isEmpty()) continue
            if (teclasPressionadas.containsAll(combo)) {
                acoesAtivas.add(acao)
            }
        }

        for (acao in acoesAtivas) {
            val combo = atalhos[acao] ?: continue
            if (acao != Acao.AVANCAR && acao != Acao.VOLTAR) {
                if (!acoesDisparadas.contains(acao)) {
                    acoesDisparadas.add(acao)
                    Log.i("PhoenixInput", "atalho=${acao.name}")
                    executarAcao(acao)
                    combo.forEach { teclasConsumidas.add(it) }
                }
            } else {
                combo.forEach { teclasConsumidas.add(it) }
            }
        }

        val toRemove = mutableListOf<Acao>()
        for (acao in acoesDisparadas) {
            val combo = atalhos[acao] ?: continue
            if (!teclasPressionadas.containsAll(combo)) {
                toRemove.add(acao)
            }
        }
        acoesDisparadas.removeAll(toRemove)

        val voltarAtivo = acoesAtivas.contains(Acao.VOLTAR)
        val avancarAtivo = acoesAtivas.contains(Acao.AVANCAR)
        setRewindActive(voltarAtivo)
        setFfActive(avancarAtivo)

        var newKeyMask = 0
        for (key in teclasPressionadas) {
            if (!teclasConsumidas.contains(key)) {
                keyToBit[key]?.let { newKeyMask = newKeyMask or it }
            }
        }
        if (newKeyMask != keyMask) {
            keyMask = newKeyMask
            nucleo.definirBotoes(0, keyMask or axisMask)
        }
    }

    private fun mostrarAviso(texto: String) {
        avisoTexto = texto
        avisoJob?.cancel()
        avisoJob = lifecycleScope.launch {
            delay(1500)
            avisoTexto = ""
        }
    }

    private fun executarAcao(acao: Acao) {
        when (acao) {
            Acao.MENU -> alternarMenu()
            Acao.REINICIAR -> {
                nucleo.reiniciar()
                mostrarAviso("Reiniciado")
            }
            Acao.SLOT_ANTERIOR -> {
                slotAtual = if (slotAtual > 1) slotAtual - 1 else 4
                mostrarAviso("Slot $slotAtual")
            }
            Acao.SLOT_PROXIMO -> {
                slotAtual = if (slotAtual < 4) slotAtual + 1 else 1
                mostrarAviso("Slot $slotAtual")
            }
            Acao.SALVAR_ESTADO -> {
                val savesDir = srmPath?.let { File(it).parentFile }
                if (savesDir != null) {
                    capturarMiniatura {
                        val fileState = armazemDeEstados?.arquivoDoEstado(slotAtual)
                        if (fileState != null && nucleo.pedirEstado(1, fileState.absolutePath)) {
                            estadoPendingJob?.cancel()
                            estadoPendingJob = lifecycleScope.launch {
                                var result = 0
                                for (i in 0 until 40) { // 40 * 50ms = 2s
                                    delay(50)
                                    result = nucleo.resultadoEstado()
                                    if (result != 0) break
                                }
                                if (result == 1) {
                                    mostrarAviso("Estado salvo no slot $slotAtual")
                                    val currentBmp = lastCapturedBitmap
                                    if (currentBmp != null) {
                                        withContext(Dispatchers.IO) {
                                            armazemDeEstados?.gravarMiniatura(slotAtual, currentBmp)
                                        }
                                    }
                                } else {
                                    mostrarAviso("Falha ao salvar")
                                }
                            }
                        } else {
                            mostrarAviso("Falha ao salvar")
                        }
                    }
                }
            }
            Acao.CARREGAR_ESTADO -> {
                val savesDir = srmPath?.let { File(it).parentFile }
                if (savesDir != null) {
                    val fileState = armazemDeEstados?.arquivoDoEstado(slotAtual)
                    if (armazemDeEstados?.temEstado(slotAtual) != true) {
                        mostrarAviso("Slot $slotAtual vazio")
                    } else {
                        if (fileState != null && nucleo.pedirEstado(2, fileState.absolutePath)) {
                            estadoPendingJob?.cancel()
                            estadoPendingJob = lifecycleScope.launch {
                                var result = 0
                                for (i in 0 until 40) {
                                    delay(50)
                                    result = nucleo.resultadoEstado()
                                    if (result != 0) break
                                }
                                if (result == 2) {
                                    mostrarAviso("Estado carregado do slot $slotAtual")
                                } else {
                                    mostrarAviso("Falha ao carregar")
                                }
                            }
                        } else {
                            mostrarAviso("Falha ao carregar")
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun resetMotorAtalhos() {
        teclasPressionadas.clear()
        teclasConsumidas.clear()
        acoesDisparadas.clear()
        leftTriggerPressed = false
        rightTriggerPressed = false
        dpadUpPressed = false
        dpadDownPressed = false
        dpadLeftPressed = false
        dpadRightPressed = false
        setRewindActive(false)
        setFfActive(false)
        estadoPendingJob?.cancel()
        estadoPendingJob = null
        keyMask = 0
        axisMask = 0
        nucleo.definirBotoes(0, 0)
    }

    private fun bindKey(keyCode: Int, bit: Int) {
        keyToBit[keyCode] = (keyToBit[keyCode] ?: 0) or bit
    }

    private fun carregarSlotsInfo() {
        slotsInfo = armazemDeEstados?.listarSlots() ?: List(4) { slot -> SlotData(slot + 1, false, "vazio", null) }
    }

    private fun capturarMiniatura(onDone: () -> Unit) {
        val sv = activeSurfaceView
        if (sv != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sv.holder.surface.isValid) {
            val width = 160
            val height = if (sv.width > 0 && sv.height > 0) (160 * sv.height / sv.width).coerceAtLeast(1) else 120
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(sv, bmp, { result ->
                    lastCapturedBitmap = if (result == PixelCopy.SUCCESS) bmp else null
                    onDone()
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                lastCapturedBitmap = null
                onDone()
            }
        } else {
            lastCapturedBitmap = null
            onDone()
        }
    }

    private fun pausarJogo() {
        capturarMiniatura {
            menuState = MenuState.MAIN
            mensagemFeedback = ""
            isPaused = true
        }
    }

    private fun alternarMenu() {
        if (isPaused) {
            if (menuState != MenuState.MAIN) {
                menuState = MenuState.MAIN
            } else {
                isPaused = false
            }
        } else {
            pausarJogo()
        }
    }

    private fun executarSalvarEstado(slot: Int) {
        val bytes = nucleo.salvarEstado()
        if (bytes == null || bytes.isEmpty()) {
            mensagemFeedback = "Falha ao salvar"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                armazemDeEstados?.gravarBytes(slot, bytes)
                val currentBmp = lastCapturedBitmap
                if (currentBmp != null) {
                    armazemDeEstados?.gravarMiniatura(slot, currentBmp)
                }
                withContext(Dispatchers.Main) {
                    mensagemFeedback = "Estado salvo no slot $slot"
                    carregarSlotsInfo()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mensagemFeedback = "Falha ao salvar"
                }
            }
        }
    }

    private fun executarCarregarEstado(slot: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = armazemDeEstados?.lerBytes(slot)

            withContext(Dispatchers.Main) {
                if (bytes == null || bytes.isEmpty()) {
                    mensagemFeedback = "Falha ao carregar estado"
                } else {
                    val ok = nucleo.carregarEstado(bytes)
                    if (ok) {
                        mensagemFeedback = ""
                        isPaused = false
                    } else {
                        mensagemFeedback = "Falha ao carregar estado"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.i("PhoenixInput", "voltar do sistema")
                alternarMenu()
            }
        })

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

        atalhos[Acao.AVANCAR] = listOf(KeyEvent.KEYCODE_BUTTON_R2)
        atalhos[Acao.VOLTAR] = listOf(KeyEvent.KEYCODE_BUTTON_L2)
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            atalhos[Acao.SALVAR_ESTADO] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_R1)
            atalhos[Acao.CARREGAR_ESTADO] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1)
            atalhos[Acao.SLOT_ANTERIOR] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_DPAD_LEFT)
            atalhos[Acao.SLOT_PROXIMO] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_DPAD_RIGHT)
            atalhos[Acao.MENU] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)
            atalhos[Acao.REINICIAR] = emptyList()
        } else {
            atalhos[Acao.SALVAR_ESTADO] = emptyList()
            atalhos[Acao.CARREGAR_ESTADO] = emptyList()
            atalhos[Acao.SLOT_ANTERIOR] = emptyList()
            atalhos[Acao.SLOT_PROXIMO] = emptyList()
            atalhos[Acao.MENU] = emptyList()
            atalhos[Acao.REINICIAR] = emptyList()
        }

        val atalhosJson = intent.getStringExtra(EXTRA_ATALHOS)
        if (atalhosJson != null) {
            try {
                val json = JSONObject(atalhosJson)
                Acao.entries.forEach { acao ->
                    if (json.has(acao.name)) {
                        val arr = json.getJSONArray(acao.name)
                        val list = mutableListOf<Int>()
                        for (i in 0 until arr.length()) list.add(arr.getInt(i))
                        atalhos[acao] = list
                    }
                }
            } catch (e: Exception) {}
        }

        val nucleoName = intent.getStringExtra(EXTRA_NUCLEO) ?: ""
        val libraryPath = "${applicationInfo.nativeLibraryDir}/$nucleoName"
        val file = File(libraryPath)
        val romUriString = intent.getStringExtra(EXTRA_ROM)
        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"

        val temaNome = intent.getStringExtra(EXTRA_TEMA)
        val tema = TemaApp.entries.firstOrNull { it.name == temaNome } ?: TemaApp.DINAMICO

        val acabamentoNome = intent.getStringExtra(EXTRA_ACABAMENTO)
        val acabamento = Acabamento.entries.firstOrNull { it.name == acabamentoNome } ?: Acabamento.VIDRO

        val reduzirEfeitos = intent.getBooleanExtra(EXTRA_REDUZIR_EFEITOS, false)
        val amoled = intent.getBooleanExtra(EXTRA_AMOLED, false)
        val sombras = intent.getBooleanExtra(EXTRA_SOMBRAS, false)

        var infoMessage = ""
        var loadError: String? = null

        if (!file.exists()) {
            loadError = "Núcleo não encontrado: $libraryPath"
        } else {
            val systemDir = File(filesDir, "system").apply { mkdirs() }
            val savesDir = File(filesDir, "saves").apply { mkdirs() }
            srmPath = File(savesDir, "$nomeSave.srm").absolutePath
            armazemDeEstados = ArmazemDeEstados(savesDir, nomeSave)

            try {
                nucleo.definirPastas(systemDir.absolutePath, savesDir.absolutePath)
                if (!nucleo.carregar(libraryPath)) {
                    loadError = "Falha ao carregar o núcleo."
                } else {
                    var romBytes: ByteArray? = null
                    var romPath: String? = null
                    
                    if (romUriString != null) {
                        val uri = Uri.parse(romUriString)
                        val resolver = contentResolver
                        
                        var romDisplayName = "rom.bin"
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                romDisplayName = cursor.getString(0) ?: "rom.bin"
                            }
                        }
                        
                        var effectiveRomName = romDisplayName
                        
                        var inputStream = resolver.openInputStream(uri)
                        
                        if (inputStream != null) {
                            if (romDisplayName.lowercase().endsWith(".zip")) {
                                val zis = ZipInputStream(inputStream)
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    val name = entry.name.lowercase()
                                    if (name.endsWith(".nes") || name.endsWith(".sfc") || name.endsWith(".smc")) {
                                        romBytes = zis.readBytes()
                                        effectiveRomName = entry.name.substringAfterLast('/')
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                                zis.close()
                            } else {
                                romBytes = inputStream.readBytes()
                                inputStream.close()
                            }
                        }
                        
                        val needFullpath = nucleo.precisaDeFullPath()
                        romPath = File(cacheDir, effectiveRomName).absolutePath
                        
                        if (needFullpath && romBytes != null) {
                            File(romPath).writeBytes(romBytes!!)
                        }
                        
                        Log.i("PhoenixLibretro", "Núcleo: $nucleoName, ROM: $effectiveRomName, need_fullpath: $needFullpath, bytes: ${romBytes?.size ?: 0}")
                    }
                    
                    if (romUriString != null && romBytes == null) {
                        loadError = "Falha ao ler a ROM."
                    } else if (!nucleo.carregarJogo(romBytes, romPath)) {
                        loadError = "Falha ao carregar o jogo."
                    } else {
                        if (romUriString != null) {
                            isJogoReal = true
                            autosavePendente = true
                        }
                        infoMessage = nucleo.info()
                        aspectRatio = nucleo.obterAspectRatio()
                        
                        srmPath?.let {
                            nucleo.carregarSram(it)
                            nucleo.definirCaminhoSram(it)
                        }

                        if (isJogoReal && intent.getBooleanExtra(EXTRA_AUTOCARREGAR, false)) {
                            val autoFile = File(savesDir, "$nomeSave.auto")
                            if (autoFile.exists() && autoFile.length() > 0) {
                                try {
                                    val bytes = autoFile.readBytes()
                                    if (nucleo.carregarEstado(bytes)) {
                                        precisaAvisoAutoload = true
                                        Log.i("PhoenixLibretro", "autoload: ok")
                                    } else {
                                        Log.w("PhoenixLibretro", "autoload: falhou")
                                    }
                                } catch (e: Exception) {
                                    Log.w("PhoenixLibretro", "autoload: falhou")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                loadError = "Erro: ${e.message}"
            }
        }

        setContent {
            val focusRequester = remember { FocusRequester() }

            PhoenixEmuTheme(
                temaAtual = tema,
                reduzirEfeitos = reduzirEfeitos,
                amoled = amoled,
                acabamento = acabamento,
                sombras = sombras
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    if (loadError != null) {
                        BasicText(
                        text = loadError!!,
                        style = TextStyle(color = Color.White, fontSize = 18.sp),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    AndroidView(
                        factory = { context ->
                            SurfaceView(context).apply {
                                activeSurfaceView = this
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        activeSurfaceHolder = holder
                                        nucleo.iniciar(holder.surface)
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        activeSurfaceHolder = null
                                        activeSurfaceView = null
                                        nucleo.parar()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.aspectRatio(aspectRatio)
                    )

                    if (rewindActive) {
                        BasicText(
                            text = "◀◀ ${rewindSecs.roundToInt()}s",
                            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(top = 16.dp)
                        )
                    } else if (ffSpeed > 1) {
                        BasicText(
                            text = "▶▶ ${ffSpeed}x",
                            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(top = 16.dp)
                        )
                    }

                    if (statsText.isNotEmpty() && !isPaused && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                        BasicText(
                            text = statsText,
                            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(16.dp)
                        )
                    }

                    if (avisoTexto.isNotEmpty()) {
                        BasicText(
                            text = avisoTexto,
                            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(top = 48.dp)
                        )
                    }

                    if (isPaused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (infoMessage.isNotEmpty()) {
                                    BasicText(
                                        text = infoMessage,
                                        style = TextStyle(color = Color.White, fontSize = 12.sp)
                                    )
                                }

                                if (mensagemFeedback.isNotEmpty()) {
                                    BasicText(
                                        text = mensagemFeedback,
                                        style = TextStyle(color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    )
                                }

                                when (menuState) {
                                    MenuState.MAIN -> {
                                        Button(
                                            onClick = { isPaused = false },
                                            modifier = Modifier.focusRequester(focusRequester)
                                        ) { Text("Continuar") }

                                        Button(onClick = {
                                            carregarSlotsInfo()
                                            menuState = MenuState.SAVE_SLOTS
                                        }) { Text("Salvar estado") }

                                        Button(onClick = {
                                            carregarSlotsInfo()
                                            menuState = MenuState.LOAD_SLOTS
                                        }) { Text("Carregar estado") }

                                        Button(onClick = { 
                                            isPaused = false
                                            nucleo.reiniciar()
                                        }) { Text("Reiniciar") }

                                        Button(onClick = { finish() }) { Text("Sair") }
                                    }

                                    MenuState.SAVE_SLOTS, MenuState.LOAD_SLOTS -> {
                                        val isSaving = (menuState == MenuState.SAVE_SLOTS)
                                        Text(
                                            text = if (isSaving) "Salvar estado" else "Carregar estado",
                                            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        )

                                        slotsInfo.forEachIndexed { index, slot ->
                                            val isFirst = (index == 0)
                                            Button(
                                                onClick = {
                                                    if (isSaving) {
                                                        executarSalvarEstado(slot.slotNumber)
                                                    } else {
                                                        executarCarregarEstado(slot.slotNumber)
                                                    }
                                                },
                                                enabled = if (isSaving) true else slot.exists,
                                                modifier = if (isFirst) Modifier.focusRequester(focusRequester) else Modifier
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.fillMaxWidth(0.6f)
                                                ) {
                                                    if (slot.bitmap != null) {
                                                        Image(
                                                            bitmap = slot.bitmap.asImageBitmap(),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(width = 80.dp, height = 60.dp)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(width = 80.dp, height = 60.dp)
                                                                .background(Color.DarkGray),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = if (slot.exists) "Sem img" else "Vazio",
                                                                style = TextStyle(color = Color.LightGray, fontSize = 12.sp)
                                                            )
                                                        }
                                                    }
                                                    Column {
                                                        Text(
                                                            text = "Slot ${slot.slotNumber}",
                                                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                        )
                                                        Text(
                                                            text = slot.dateText,
                                                            style = TextStyle(fontSize = 12.sp, color = Color.LightGray)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Button(onClick = { menuState = MenuState.MAIN }) {
                                            Text("Voltar")
                                        }
                                    }
                                }
                            }
                        }

                        LaunchedEffect(menuState) {
                            try { focusRequester.requestFocus() } catch (e: Exception) {}
                        }
                    }
                }
            }

            if (loadError == null) {
                if (precisaAvisoAutoload) {
                    LaunchedEffect(Unit) {
                        mostrarAviso("Jogo retomado")
                        precisaAvisoAutoload = false
                    }
                }

                if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    LaunchedEffect(isPaused) {
                        if (!isPaused) {
                            while(true) {
                                delay(500)
                                val stats = nucleo.obterStats()
                                if (stats.size >= 3) {
                                    val fpsStr = String.format(Locale.US, "%.1f", stats[0])
                                    val msStr = String.format(Locale.US, "%.1f", stats[1])
                                    val maxStr = String.format(Locale.US, "%.1f", stats[2])
                                    statsText = "$fpsStr fps | $msStr ms (max $maxStr)"
                                }
                            }
                        } else {
                            statsText = ""
                        }
                    }
                }

                LaunchedEffect(isPaused) {
                    resetMotorAtalhos()

                    if (isPaused) {
                        nucleo.parar()
                        srmPath?.let { nucleo.salvarSram(it) }
                        carregarSlotsInfo()
                    } else {
                        activeSurfaceHolder?.surface?.let {
                            if (it.isValid) nucleo.iniciar(it)
                        }
                    }
                }
                
                LaunchedEffect(Unit) {
                    while(true) {
                        delay(30_000)
                        nucleo.pedirSalvarSram()
                    }
                }
            }
        }
    }
}

    private fun tentarAutosave() {
        if (!isJogoReal || !autosavePendente) return
        if (!intent.getBooleanExtra(EXTRA_AUTOSALVAR, false)) return

        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"
        val savesDir = srmPath?.let { File(it).parentFile } ?: return

        try {
            val bytes = nucleo.salvarEstado()
            if (bytes != null && bytes.isNotEmpty()) {
                val autoFile = File(savesDir, "$nomeSave.auto")
                val tmpFile = File(savesDir, "$nomeSave.auto.tmp")
                tmpFile.writeBytes(bytes)
                if (!tmpFile.renameTo(autoFile)) {
                    tmpFile.copyTo(autoFile, overwrite = true)
                    tmpFile.delete()
                }
                Log.i("PhoenixLibretro", "autosave: ok (${bytes.size} bytes)")
                autosavePendente = false
            } else {
                Log.i("PhoenixLibretro", "autosave: falhou")
            }
        } catch (e: Exception) {
            Log.i("PhoenixLibretro", "autosave: falhou")
        }
    }

    override fun onPause() {
        super.onPause()
        nucleo.parar()
        resetMotorAtalhos()
        srmPath?.let { nucleo.salvarSram(it) }
        tentarAutosave()
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
        resetMotorAtalhos()
        srmPath?.let { nucleo.salvarSram(it) }
        tentarAutosave()
        nucleo.descarregar()
        if (isFinishing) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.i("PhoenixInput", "tecla=" + KeyEvent.keyCodeToString(event.keyCode) + " acao=" + event.action)
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                alternarMenu()
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

        val ehMapeado = keyToBit.containsKey(event.keyCode)
        val ehAtalho = atalhos.values.any { it.contains(event.keyCode) }

        if (ehMapeado || ehAtalho) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                processarEntradaVirtual(event.keyCode, true)
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                processarEntradaVirtual(event.keyCode, false)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isPaused) return super.dispatchGenericMotionEvent(event)

        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && event.action == MotionEvent.ACTION_MOVE) {
            val ltrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            val brake = event.getAxisValue(MotionEvent.AXIS_BRAKE)
            val newL2 = ltrigger > 0.5f || brake > 0.5f
            if (newL2 != leftTriggerPressed) {
                leftTriggerPressed = newL2
                processarEntradaVirtual(KeyEvent.KEYCODE_BUTTON_L2, newL2)
            }

            val rtrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            val gas = event.getAxisValue(MotionEvent.AXIS_GAS)
            val newR2 = rtrigger > 0.5f || gas > 0.5f
            if (newR2 != rightTriggerPressed) {
                rightTriggerPressed = newR2
                processarEntradaVirtual(KeyEvent.KEYCODE_BUTTON_R2, newR2)
            }

            val hatx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val haty = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            val newDpadLeft = hatx < -0.5f
            if (newDpadLeft != dpadLeftPressed) {
                dpadLeftPressed = newDpadLeft
                processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_LEFT, newDpadLeft)
            }
            val newDpadRight = hatx > 0.5f
            if (newDpadRight != dpadRightPressed) {
                dpadRightPressed = newDpadRight
                processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_RIGHT, newDpadRight)
            }
            val newDpadUp = haty < -0.5f
            if (newDpadUp != dpadUpPressed) {
                dpadUpPressed = newDpadUp
                processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_UP, newDpadUp)
            }
            val newDpadDown = haty > 0.5f
            if (newDpadDown != dpadDownPressed) {
                dpadDownPressed = newDpadDown
                processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_DOWN, newDpadDown)
            }

            val xaxis = event.getAxisValue(MotionEvent.AXIS_X)
            val yaxis = event.getAxisValue(MotionEvent.AXIS_Y)

            var newAxisMask = 0
            if (xaxis < -0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.ESQUERDA
            if (xaxis > 0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.DIREITA
            if (yaxis < -0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.CIMA
            if (yaxis > 0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.BAIXO

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

        const val EXTRA_ATALHOS = "phoenix.atalhos"
        const val EXTRA_AUTOSALVAR = "phoenix.autosalvar"
        const val EXTRA_AUTOCARREGAR = "phoenix.autocarregar"
        
        const val EXTRA_TEMA = "phoenix.tema"
        const val EXTRA_ACABAMENTO = "phoenix.acabamento"
        const val EXTRA_REDUZIR_EFEITOS = "phoenix.reduzir_efeitos"
        const val EXTRA_AMOLED = "phoenix.amoled"
        const val EXTRA_SOMBRAS = "phoenix.sombras"

        val ORDEM_DO_MAPEAMENTO = listOf(
            "CIMA", "BAIXO", "ESQUERDA", "DIREITA",
            "A", "B", "X", "Y", "L", "R", "SELECT", "START"
        )
    }
}

