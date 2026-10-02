package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

private enum class MenuState {
    MAIN,
    SAVE_SLOTS,
    LOAD_SLOTS
}

private data class SlotData(
    val slotNumber: Int,
    val exists: Boolean,
    val dateText: String,
    val bitmap: Bitmap?
)

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

    private var r2KeyPressed = false
    private var r2AxisPressed = false
    private var ffJob: kotlinx.coroutines.Job? = null
    private var ffSpeed by mutableStateOf(1)
    private var statsText by mutableStateOf("")

    private var l2KeyPressed = false
    private var l2AxisPressed = false
    private var rewindJob: kotlinx.coroutines.Job? = null
    private var rewindActive = false
    private var rewindSecs by mutableStateOf(0f)

    private fun updateFastForward() {
        val active = (r2KeyPressed || r2AxisPressed) && !rewindActive
        if (active && ffSpeed == 1) {
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
        } else if (!active && ffSpeed > 1) {
            ffJob?.cancel()
            ffJob = null
            ffSpeed = 1
            nucleo.definirAvancoRapido(1)
        }
    }

    private fun updateRewind() {
        val active = l2KeyPressed || l2AxisPressed
        if (active && !rewindActive) {
            rewindActive = true
            nucleo.definirRewind(true)
            updateFastForward()
            
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
            
            updateFastForward()
        }
    }

    private fun resetAllSpeedModifiers() {
        r2KeyPressed = false
        r2AxisPressed = false
        l2KeyPressed = false
        l2AxisPressed = false
        updateRewind()
        updateFastForward()
    }

    private fun bindKey(keyCode: Int, bit: Int) {
        keyToBit[keyCode] = (keyToBit[keyCode] ?: 0) or bit
    }

    private fun carregarSlotsInfo() {
        val savesDir = srmPath?.let { File(it).parentFile }
        val list = mutableListOf<SlotData>()
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"

        for (slot in 1..4) {
            if (savesDir != null) {
                val stateFile = File(savesDir, "$nomeSave.state$slot")
                val pngFile = File(savesDir, "$nomeSave.state$slot.png")

                if (stateFile.exists() && stateFile.length() > 0) {
                    val dateStr = dateFormat.format(Date(stateFile.lastModified()))
                    val bmp = if (pngFile.exists()) {
                        try {
                            BitmapFactory.decodeFile(pngFile.absolutePath)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    list.add(SlotData(slot, true, dateStr, bmp))
                } else {
                    list.add(SlotData(slot, false, "vazio", null))
                }
            } else {
                list.add(SlotData(slot, false, "vazio", null))
            }
        }
        slotsInfo = list
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
        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"
        val savesDir = srmPath?.let { File(it).parentFile } ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stateFile = File(savesDir, "$nomeSave.state$slot")
                val tmpStateFile = File(savesDir, "$nomeSave.state$slot.tmp")
                tmpStateFile.writeBytes(bytes)
                if (!tmpStateFile.renameTo(stateFile)) {
                    tmpStateFile.copyTo(stateFile, overwrite = true)
                    tmpStateFile.delete()
                }

                val currentBmp = lastCapturedBitmap
                if (currentBmp != null) {
                    val pngFile = File(savesDir, "$nomeSave.state$slot.png")
                    val tmpPngFile = File(savesDir, "$nomeSave.state$slot.png.tmp")
                    FileOutputStream(tmpPngFile).use { out ->
                        currentBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (!tmpPngFile.renameTo(pngFile)) {
                        tmpPngFile.copyTo(pngFile, overwrite = true)
                        tmpPngFile.delete()
                    }
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
        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"
        val savesDir = srmPath?.let { File(it).parentFile } ?: return
        val stateFile = File(savesDir, "$nomeSave.state$slot")

        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = if (stateFile.exists()) {
                try { stateFile.readBytes() } catch (e: Exception) { null }
            } else null

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

        val nucleoName = intent.getStringExtra(EXTRA_NUCLEO) ?: ""
        val libraryPath = "${applicationInfo.nativeLibraryDir}/$nucleoName"
        val file = File(libraryPath)
        val romUriString = intent.getStringExtra(EXTRA_ROM)
        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"

        var infoMessage = ""
        var loadError: String? = null

        if (!file.exists()) {
            loadError = "Núcleo não encontrado: $libraryPath"
        } else {
            val systemDir = File(filesDir, "system").apply { mkdirs() }
            val savesDir = File(filesDir, "saves").apply { mkdirs() }
            srmPath = File(savesDir, "$nomeSave.srm").absolutePath

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
                        infoMessage = nucleo.info()
                        aspectRatio = nucleo.obterAspectRatio()
                        
                        srmPath?.let {
                            nucleo.carregarSram(it)
                            nucleo.definirCaminhoSram(it)
                        }
                    }
                }
            } catch (e: Exception) {
                loadError = "Erro: ${e.message}"
            }
        }

        setContent {
            val focusRequester = remember { FocusRequester() }

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
                    keyMask = 0
                    axisMask = 0
                    nucleo.definirBotoes(0, 0)
                    resetAllSpeedModifiers()

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

    override fun onPause() {
        super.onPause()
        nucleo.parar()
        resetAllSpeedModifiers()
        srmPath?.let { nucleo.salvarSram(it) }
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
        resetAllSpeedModifiers()
        srmPath?.let { nucleo.salvarSram(it) }
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

        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                l2KeyPressed = true
                updateRewind()
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                l2KeyPressed = false
                updateRewind()
                return true
            }
        }

        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            if (rewindActive) return true
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                r2KeyPressed = true
                updateFastForward()
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                r2KeyPressed = false
                updateFastForward()
                return true
            }
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
            val ltrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            val brake = event.getAxisValue(MotionEvent.AXIS_BRAKE)
            val newL2Axis = ltrigger > 0.5f || brake > 0.5f
            if (newL2Axis != l2AxisPressed) {
                l2AxisPressed = newL2Axis
                updateRewind()
            }

            val rtrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            val gas = event.getAxisValue(MotionEvent.AXIS_GAS)
            val newR2Axis = rtrigger > 0.5f || gas > 0.5f
            if (newR2Axis != r2AxisPressed) {
                r2AxisPressed = newR2Axis
                updateFastForward()
            }

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

