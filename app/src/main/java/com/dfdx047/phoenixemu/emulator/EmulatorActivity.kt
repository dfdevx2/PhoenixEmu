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
import com.dfdx047.phoenixemu.R
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
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider

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
    private var abaAtual by mutableStateOf(AbaDoMenu.JOGO)
    private var fundoDoMenu by mutableStateOf<Bitmap?>(null)
    private var mensagemFeedback by mutableStateOf("")
    private var slotsInfo by mutableStateOf<List<SlotData>>(emptyList())
    private var nomeDoJogo by mutableStateOf("")

    private var srmPath: String? = null
    private var isJogoReal = false
    private var precisaAvisoAutoload = false
    private var autosavePendente = false
    
    private lateinit var motorDeAtalhos: MotorDeAtalhos
    private var contextoLocalizado: Context? = null

    private var avisoTexto by mutableStateOf("")
    private var avisoJob: kotlinx.coroutines.Job? = null
    private var slotAtual = 1
    private var armazemDeEstados: ArmazemDeEstados? = null
    private var gerenciadorDeEstados: GerenciadorDeEstados? = null

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

    private fun mostrarAviso(texto: String) {
        avisoTexto = texto
        avisoJob?.cancel()
        avisoJob = lifecycleScope.launch {
            delay(1500)
            avisoTexto = ""
        }
    }

    private fun executarAcao(acao: Acao) {
        val ctx = contextoLocalizado ?: this
        when (acao) {
            Acao.MENU -> alternarMenu()
            Acao.REINICIAR -> {
                nucleo.reiniciar()
                mostrarAviso(ctx.getString(R.string.jogo_aviso_reiniciado))
            }
            Acao.SLOT_ANTERIOR -> {
                slotAtual = if (slotAtual > 1) slotAtual - 1 else 4
                mostrarAviso(ctx.getString(R.string.jogo_aviso_slot, slotAtual))
            }
            Acao.SLOT_PROXIMO -> {
                slotAtual = if (slotAtual < 4) slotAtual + 1 else 1
                mostrarAviso(ctx.getString(R.string.jogo_aviso_slot, slotAtual))
            }
            Acao.SALVAR_ESTADO -> {
                gerenciadorDeEstados?.salvar(slotAtual, capturarAntes = true) { res ->
                    if (res == ResultadoDoEstado.OK) {
                        mostrarAviso(ctx.getString(R.string.jogo_aviso_estado_salvo, slotAtual))
                    } else {
                        mostrarAviso(ctx.getString(R.string.jogo_aviso_falha_salvar))
                    }
                }
            }
            Acao.CARREGAR_ESTADO -> {
                gerenciadorDeEstados?.carregar(slotAtual) { res ->
                    when (res) {
                        ResultadoDoEstado.OK -> mostrarAviso(ctx.getString(R.string.jogo_aviso_estado_carregado, slotAtual))
                        ResultadoDoEstado.VAZIO -> mostrarAviso(ctx.getString(R.string.jogo_aviso_slot_vazio, slotAtual))
                        ResultadoDoEstado.FALHA -> mostrarAviso(ctx.getString(R.string.jogo_aviso_falha_carregar))
                    }
                }
            }
            else -> {}
        }
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
            abaAtual = AbaDoMenu.JOGO
            fundoDoMenu = lastCapturedBitmap
            mensagemFeedback = ""
            isPaused = true
        }
    }

    private fun alternarMenu() {
        if (isPaused) {
            if (abaAtual != AbaDoMenu.JOGO) {
                abaAtual = AbaDoMenu.JOGO
            } else {
                isPaused = false
                fundoDoMenu = null
            }
        } else {
            pausarJogo()
        }
    }


    private fun carregarSlotsInfo() {
        val ctx = contextoLocalizado ?: this
        slotsInfo = armazemDeEstados?.listarSlots() ?: List(4) { slot -> SlotData(slot + 1, false, ctx.getString(R.string.jogo_slot_vazio_text), "", null) }
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
        
        motorDeAtalhos = MotorDeAtalhos(
            estaPausado = { isPaused },
            aoExecutarAcao = ::executarAcao,
            aoMudarAvanco = ::setFfActive,
            aoMudarRewind = ::setRewindActive,
            aoMudarBotoes = { mascara -> nucleo.definirBotoes(0, mascara) }
        )

        val mapKeys = intent.getIntArrayExtra(EXTRA_MAPEAMENTO)
        if (mapKeys != null && mapKeys.size >= 12) {
            motorDeAtalhos.bindKey(mapKeys[0], NucleoLibretro.Botao.CIMA)
            motorDeAtalhos.bindKey(mapKeys[1], NucleoLibretro.Botao.BAIXO)
            motorDeAtalhos.bindKey(mapKeys[2], NucleoLibretro.Botao.ESQUERDA)
            motorDeAtalhos.bindKey(mapKeys[3], NucleoLibretro.Botao.DIREITA)
            motorDeAtalhos.bindKey(mapKeys[4], NucleoLibretro.Botao.A)
            motorDeAtalhos.bindKey(mapKeys[5], NucleoLibretro.Botao.B)
            motorDeAtalhos.bindKey(mapKeys[6], NucleoLibretro.Botao.X)
            motorDeAtalhos.bindKey(mapKeys[7], NucleoLibretro.Botao.Y)
            motorDeAtalhos.bindKey(mapKeys[8], NucleoLibretro.Botao.L)
            motorDeAtalhos.bindKey(mapKeys[9], NucleoLibretro.Botao.R)
            motorDeAtalhos.bindKey(mapKeys[10], NucleoLibretro.Botao.SELECT)
            motorDeAtalhos.bindKey(mapKeys[11], NucleoLibretro.Botao.START)
        }

        motorDeAtalhos.definirPadroes((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        motorDeAtalhos.carregarDoJson(intent.getStringExtra(EXTRA_ATALHOS))

        val nucleoName = intent.getStringExtra(EXTRA_NUCLEO) ?: ""
        val romUriString = intent.getStringExtra(EXTRA_ROM)
        val nomeSave = intent.getStringExtra(EXTRA_NOME_SAVE) ?: "save"

        val temaNome = intent.getStringExtra(EXTRA_TEMA)
        val tema = TemaApp.entries.firstOrNull { it.name == temaNome } ?: TemaApp.DINAMICO

        val acabamentoNome = intent.getStringExtra(EXTRA_ACABAMENTO)
        val acabamento = Acabamento.entries.firstOrNull { it.name == acabamentoNome } ?: Acabamento.VIDRO

        val reduzirEfeitos = intent.getBooleanExtra(EXTRA_REDUZIR_EFEITOS, false)
        val amoled = intent.getBooleanExtra(EXTRA_AMOLED, false)
        val sombras = intent.getBooleanExtra(EXTRA_SOMBRAS, false)

        val carregador = CarregadorDeJogo(this, nucleo)
        val resultado = carregador.carregar(
            nomeDoNucleo = nucleoName,
            romUri = romUriString,
            nomeSave = nomeSave,
            autoCarregar = intent.getBooleanExtra(EXTRA_AUTOCARREGAR, false)
        )

        var infoMessage = resultado.info
        var loadError: String? = resultado.erro

        srmPath = resultado.srmPath
        isJogoReal = resultado.jogoReal
        aspectRatio = resultado.aspectRatio
        precisaAvisoAutoload = resultado.autoloadAplicado
        nomeDoJogo = if (isJogoReal) {
            val tituloExtra = intent.getStringExtra(EXTRA_TITULO)
            if (!tituloExtra.isNullOrBlank()) tituloExtra else (romUriString ?: "")
        } else ""

        // Novos extras para o cabeçalho do menu de pausa
        val capaLocal = intent.getStringExtra(EXTRA_CAPA)
        val tempoJogadoMs = intent.getLongExtra(EXTRA_TEMPO_JOGADO_MS, 0L)
        val plataforma = intent.getStringExtra(EXTRA_PLATAFORMA) ?: ""
        if (isJogoReal) {
            autosavePendente = true
        }

        srmPath?.let {
            val ctx = this@EmulatorActivity
            armazemDeEstados = ArmazemDeEstados(File(it).parentFile, nomeSave, ctx.getString(R.string.jogo_slot_vazio_text))
            gerenciadorDeEstados = GerenciadorDeEstados(
                nucleo = nucleo,
                armazem = armazemDeEstados!!,
                escopo = lifecycleScope,
                capturarMiniatura = ::capturarMiniatura,
                miniaturaAtual = { lastCapturedBitmap },
            )
        }

        // --- Aplicar idioma escolhido nos Ajustes (se houver) ---
        val idiomaTag = intent.getStringExtra(EXTRA_IDIOMA) ?: ""
        val config = if (idiomaTag.isNotBlank()) {
            val locale = Locale.forLanguageTag(idiomaTag)
            Locale.setDefault(locale)
            Configuration(resources.configuration).apply { setLocale(locale) }
        } else {
            Configuration(resources.configuration)
        }
        contextoLocalizado = createConfigurationContext(config)

        setContent {
            val focusRequester = remember { FocusRequester() }

            CompositionLocalProvider(
                LocalContext provides (contextoLocalizado ?: this@EmulatorActivity),
                LocalConfiguration provides config
            ) {
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

                    HudDoJogo(
                        rewindAtivo = rewindActive,
                        rewindSegundos = rewindSecs,
                        velocidadeAvanco = ffSpeed,
                        statsTexto = statsText,
                        mostrarStats = !isPaused && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                        avisoTexto = avisoTexto
                    )

                    if (isPaused) {
                        MenuDePausa(
                            visivel = isPaused,
                            fundo = fundoDoMenu,
                            nomeDoJogo = nomeDoJogo,
                            tempoJogadoMinutos = 0,
                            slots = slotsInfo,
                            slotSelecionado = slotAtual,
                            abaAtual = abaAtual,
                            capaLocalPath = capaLocal,
                            tempoJogadoMs = tempoJogadoMs,
                            plataforma = plataforma,
                            aoFechar = {
                                mensagemFeedback = ""
                                isPaused = false
                                fundoDoMenu = null
                            },
                            aoSalvarEstado = { slot ->
                                gerenciadorDeEstados?.salvar(slot, capturarAntes = true) { res ->
                                    val ctx = contextoLocalizado ?: this@EmulatorActivity
                                    if (res == ResultadoDoEstado.OK) {
                                        mensagemFeedback = ctx.getString(R.string.jogo_aviso_salvo)
                                        carregarSlotsInfo()
                                    } else {
                                        mensagemFeedback = ctx.getString(R.string.jogo_aviso_falha_salvar)
                                    }
                                }
                            },
                            aoCarregarEstado = { slot ->
                                gerenciadorDeEstados?.carregar(slot) { res ->
                                    val ctx = contextoLocalizado ?: this@EmulatorActivity
                                    if (res == ResultadoDoEstado.OK) {
                                        mensagemFeedback = ""
                                        isPaused = false
                                        fundoDoMenu = null
                                    } else {
                                        mensagemFeedback = ctx.getString(R.string.jogo_aviso_falha_carregar)
                                    }
                                }
                            },
                            aoContinuar = {
                                mensagemFeedback = ""
                                isPaused = false
                                fundoDoMenu = null
                            },
                            aoReiniciar = {
                                val ctx = contextoLocalizado ?: this@EmulatorActivity
                                nucleo.reiniciar()
                                mostrarAviso(ctx.getString(R.string.jogo_aviso_reiniciado))
                            },
                            aoSair = { finish() },
                            aoMudarSlot = { slot -> slotAtual = slot },
                            aoMudarAba = { aba -> abaAtual = aba },
                        )
                    }
                }
            }

            if (loadError == null) {
                if (precisaAvisoAutoload) {
                    LaunchedEffect(Unit) {
                        val ctx = contextoLocalizado ?: this@EmulatorActivity
                        mostrarAviso(ctx.getString(R.string.jogo_aviso_jogo_retomado))
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
                    motorDeAtalhos.resetar()
                    gerenciadorDeEstados?.cancelarPendentes()

                    if (isPaused) {
                        nucleo.definirPausa(true)
                        srmPath?.let { nucleo.salvarSram(it) }
                        carregarSlotsInfo()
                    } else {
                        nucleo.definirPausa(false)
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
        motorDeAtalhos.resetar()
        gerenciadorDeEstados?.cancelarPendentes()
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
        motorDeAtalhos.resetar()
        gerenciadorDeEstados?.cancelarPendentes()
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

        val ehMapeado = motorDeAtalhos.keyToBit.containsKey(event.keyCode)
        val ehAtalho = motorDeAtalhos.atalhos.values.any { it.contains(event.keyCode) }

        if (ehMapeado || ehAtalho) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                motorDeAtalhos.processarEntradaVirtual(event.keyCode, true)
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                motorDeAtalhos.processarEntradaVirtual(event.keyCode, false)
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
            if (newL2 != motorDeAtalhos.leftTriggerPressed) {
                motorDeAtalhos.leftTriggerPressed = newL2
                motorDeAtalhos.processarEntradaVirtual(KeyEvent.KEYCODE_BUTTON_L2, newL2)
            }

            val rtrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            val gas = event.getAxisValue(MotionEvent.AXIS_GAS)
            val newR2 = rtrigger > 0.5f || gas > 0.5f
            if (newR2 != motorDeAtalhos.rightTriggerPressed) {
                motorDeAtalhos.rightTriggerPressed = newR2
                motorDeAtalhos.processarEntradaVirtual(KeyEvent.KEYCODE_BUTTON_R2, newR2)
            }

            val hatx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val haty = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            val newDpadLeft = hatx < -0.5f
            if (newDpadLeft != motorDeAtalhos.dpadLeftPressed) {
                motorDeAtalhos.dpadLeftPressed = newDpadLeft
                motorDeAtalhos.processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_LEFT, newDpadLeft)
            }
            val newDpadRight = hatx > 0.5f
            if (newDpadRight != motorDeAtalhos.dpadRightPressed) {
                motorDeAtalhos.dpadRightPressed = newDpadRight
                motorDeAtalhos.processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_RIGHT, newDpadRight)
            }
            val newDpadUp = haty < -0.5f
            if (newDpadUp != motorDeAtalhos.dpadUpPressed) {
                motorDeAtalhos.dpadUpPressed = newDpadUp
                motorDeAtalhos.processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_UP, newDpadUp)
            }
            val newDpadDown = haty > 0.5f
            if (newDpadDown != motorDeAtalhos.dpadDownPressed) {
                motorDeAtalhos.dpadDownPressed = newDpadDown
                motorDeAtalhos.processarEntradaVirtual(KeyEvent.KEYCODE_DPAD_DOWN, newDpadDown)
            }

            val xaxis = event.getAxisValue(MotionEvent.AXIS_X)
            val yaxis = event.getAxisValue(MotionEvent.AXIS_Y)

            var newAxisMask = 0
            if (xaxis < -0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.ESQUERDA
            if (xaxis > 0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.DIREITA
            if (yaxis < -0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.CIMA
            if (yaxis > 0.5f) newAxisMask = newAxisMask or NucleoLibretro.Botao.BAIXO

            motorDeAtalhos.atualizarAxisMask(newAxisMask)
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
        /** Chave do Intent extra para o código de idioma (ex: "pt-BR"). */
        const val EXTRA_IDIOMA = "phoenix.idioma"

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

        /** Nome legível do jogo (do banco). Se vazio, a Activity decodifica do ROM URI. */
        const val EXTRA_TITULO = "phoenix.titulo"

        /** Caminho de arquivo LOCAL da capa. Vazio se só existir URL remota. */
        const val EXTRA_CAPA = "phoenix.capa"

        /** Tempo acumulado ANTES desta sessão, em milissegundos (Long). */
        const val EXTRA_TEMPO_JOGADO_MS = "phoenix.tempo_jogado_ms"

        /** Plataforma: "NES" ou "SNES". */
        const val EXTRA_PLATAFORMA = "phoenix.plataforma"

        val ORDEM_DO_MAPEAMENTO = listOf(
            "CIMA", "BAIXO", "ESQUERDA", "DIREITA",
            "A", "B", "X", "Y", "L", "R", "SELECT", "START"
        )
    }
}

