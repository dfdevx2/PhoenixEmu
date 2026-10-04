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
import org.json.JSONArray
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.AtalhoDaAcao
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.ConfigDeAtalhos
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.ui.theme.PhoenixEmuTheme
import com.google.gson.Gson
import android.content.Intent
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
import androidx.compose.ui.platform.LocalDensity
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
    private var idDoJogoAtual = ""
    private var ajustes by mutableStateOf(AjustesDeJogo())
    
    private fun aplicarAjustesNoEmulador() {
        val volumeReal = if (ajustes.mudo) 0f else ajustes.volume
        nucleo.definirVolume(volumeReal)
        // O FF será usado quando for ativado;
        // Escala e Proporção aplicamos na UI via Modifier;
        // FPS ativado altera a UI
    }
    
    private fun salvarAjuste(chave: String, valor: Any, tipo: String, limpar: Boolean = false) {
        val escopo = if (ajustes.isSomenteEsteJogo()) "JOGO" else "GLOBAL"
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("idDoJogo", idDoJogoAtual)
            if (limpar) {
                putExtra("acao", "limpar")
            } else {
                putExtra("escopo", escopo)
                putExtra("chave", chave)
                putExtra("tipo", tipo)
                when (valor) {
                    is String -> putExtra("valor", valor)
                    is Int -> putExtra("valor", valor)
                    is Float -> putExtra("valor", valor)
                    is Boolean -> putExtra("valor", valor)
                }
            }
        }
        sendBroadcast(intent)
        aplicarAjustesNoEmulador()
    }
    
    private lateinit var motorDeAtalhos: MotorDeAtalhos
    private var atalhosCfg by mutableStateOf(ConfigDeAtalhos.padrao())
    private var capturaAtalho by mutableStateOf<String?>(null)
    private var eixoPrecisaSoltar = false
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
    var mapeamentoAtual by mutableStateOf(IntArray(12))
    
    private var overlayConfig by mutableStateOf(com.dfdx047.phoenixemu.data.OverlayConfigNova())
    private var controleOcultouOverlay by mutableStateOf(false)

    private fun setFfActive(active: Boolean) {
        val shouldBeActive = active && !rewindActive
        if (shouldBeActive && ffSpeed == 1) {
            ffSpeed = ajustes.velocidadeFF
            nucleo.definirAvancoRapido(ffSpeed)
        } else if (!shouldBeActive && ffSpeed > 1) {
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

    fun definirAtalhoNoJogo(acao: AcaoAtalho, tecla: Int) {
        atalhosCfg = atalhosCfg.comAtalho(acao, tecla)
        aplicarNoMotor()
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("acao", "atalho_definir")
            putExtra("acaoAtalho", acao.name)
            putExtra("tecla", tecla)
        }
        sendBroadcast(intent)
        Log.d("PhoenixAjustes", "atalho ${acao.name} tecla=$tecla combo=${atalhosCfg.combo(acao)}")
    }

    fun alternarUsarHotkey(acao: AcaoAtalho, usar: Boolean) {
        atalhosCfg = atalhosCfg.comUsarHotkey(acao, usar)
        aplicarNoMotor()
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("acao", "atalho_usar_hotkey")
            putExtra("acaoAtalho", acao.name)
            putExtra("usar", usar)
        }
        sendBroadcast(intent)
        Log.d("PhoenixAjustes", "atalho ${acao.name} tecla=${atalhosCfg.combo(acao)} usarHotkey=$usar")
    }

    private fun concluirCapturaPorEixo(codigo: Int) {
        val alvo = capturaAtalho ?: return
        when {
            alvo == "HOTKEY" -> definirHotkeyNoJogo(codigo)
            alvo.startsWith("BTN_") -> runCatching { BotaoVirtual.valueOf(alvo.removePrefix("BTN_")) }
                .onSuccess { definirBotaoNoJogo(it, codigo) }
            else -> runCatching { AcaoAtalho.valueOf(alvo) }
                .onSuccess { definirAtalhoNoJogo(it, codigo) }
        }
        capturaAtalho = null
        eixoPrecisaSoltar = true
        Log.d("PhoenixAjustes", "captura por eixo codigo=$codigo alvo=$alvo")
    }

    fun definirHotkeyNoJogo(tecla: Int) {
        atalhosCfg = atalhosCfg.comHotkey(tecla)
        aplicarNoMotor()
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("acao", "atalho_hotkey")
            putExtra("tecla", tecla)
        }
        sendBroadcast(intent)
        Log.d("PhoenixAjustes", "hotkey=$tecla")
    }

    fun limparAtalho(acao: AcaoAtalho) {
        definirAtalhoNoJogo(acao, 0)
    }

    fun restaurarAtalhosNoJogo() {
        atalhosCfg = ConfigDeAtalhos.padrao()
        aplicarNoMotor()
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("acao", "atalho_restaurar")
        }
        sendBroadcast(intent)
        Log.d("PhoenixAjustes", "atalhos restaurados")
    }

    private fun aplicarMapeamentoNoMotor() {
        motorDeAtalhos.limparBotoes()
        val botoes = arrayOf(
            NucleoLibretro.Botao.CIMA,
            NucleoLibretro.Botao.BAIXO,
            NucleoLibretro.Botao.ESQUERDA,
            NucleoLibretro.Botao.DIREITA,
            NucleoLibretro.Botao.A,
            NucleoLibretro.Botao.B,
            NucleoLibretro.Botao.X,
            NucleoLibretro.Botao.Y,
            NucleoLibretro.Botao.L,
            NucleoLibretro.Botao.R,
            NucleoLibretro.Botao.SELECT,
            NucleoLibretro.Botao.START
        )
        for (i in mapeamentoAtual.indices) {
            val codigo = mapeamentoAtual[i]
            if (codigo != 0) {
                motorDeAtalhos.bindKey(codigo, botoes[i])
            }
        }
        motorDeAtalhos.resetar()
    }

    fun definirBotaoNoJogo(botao: BotaoVirtual, tecla: Int) {
        val arr = mapeamentoAtual.copyOf()
        val i = botao.ordinal
        val oldCode = arr[i]
        for (j in arr.indices) {
            if (j != i && arr[j] == tecla) {
                arr[j] = oldCode
            }
        }
        arr[i] = tecla
        mapeamentoAtual = arr
        aplicarMapeamentoNoMotor()
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("acao", "botao_definir")
            putExtra("botao", botao.name)
            putExtra("tecla", tecla)
        }
        sendBroadcast(intent)
        Log.d("PhoenixAjustes", "botao ${botao.name} tecla=$tecla")
    }

    fun restaurarBotoesNoJogo() {
        mapeamentoAtual = IntArray(12) { BotaoVirtual.entries[it].padrao }
        aplicarMapeamentoNoMotor()
        val intent = Intent("com.dfdx047.phoenixemu.AJUSTE_MUDOU").apply {
            setPackage(packageName)
            putExtra("acao", "botao_restaurar")
        }
        sendBroadcast(intent)
        Log.d("PhoenixAjustes", "botoes restaurados")
    }

    fun aplicarNoMotor() {
        val json = org.json.JSONObject()
        for (acao in AcaoAtalho.entries) {
            val combo = atalhosCfg.combo(acao)
            val arr = org.json.JSONArray()
            for (kc in combo) arr.put(kc)
            json.put(acao.name, arr)
        }
        motorDeAtalhos.carregarDoJson(json.toString())
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
        
        val menuVoltar = intent.getBooleanExtra("phoenix.menu_voltar", true)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.i("PhoenixInput", "voltar do sistema")
                if (menuVoltar) {
                    alternarMenu()
                } else {
                    finish()
                }
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
            mapeamentoAtual = mapKeys.copyOf()
            aplicarMapeamentoNoMotor()
        }

        motorDeAtalhos.definirPadroes((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        var atalhosJson = intent.getStringExtra(EXTRA_ATALHOS) ?: ""
        motorDeAtalhos.carregarDoJson(atalhosJson)

        overlayConfig = try {
            val cfgJson = intent.getStringExtra(EXTRA_OVERLAY)
            if (!cfgJson.isNullOrBlank()) {
                Gson().fromJson(cfgJson, com.dfdx047.phoenixemu.data.OverlayConfigNova::class.java)
            } else {
                com.dfdx047.phoenixemu.data.OverlayConfigNova()
            }
        } catch (_: Exception) {
            com.dfdx047.phoenixemu.data.OverlayConfigNova()
        }

        atalhosCfg = try {
            val cfgJson = intent.getStringExtra(EXTRA_ATALHOS_CFG)
            if (!cfgJson.isNullOrBlank()) {
                Gson().fromJson(cfgJson, ConfigDeAtalhos::class.java)
            } else {
                ConfigDeAtalhos.padrao()
            }
        } catch (_: Exception) {
            ConfigDeAtalhos.padrao()
        }
        aplicarNoMotor()

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
        
        val temaCorPrimaria = intent.getIntExtra("phoenix.cor_primaria", 0)
        val temaCorSuperficie = intent.getIntExtra("phoenix.cor_superficie", 0)
        val temaCorTexto = intent.getIntExtra("phoenix.cor_texto", 0)
        
        val menuEstilo = intent.getStringExtra("phoenix.menu_estilo") ?: "VIDRO"
        val menuDesfoque = intent.getFloatExtra("phoenix.menu_desfoque", 20f)
        val menuOpacidade = intent.getFloatExtra("phoenix.menu_opacidade", 1.0f)
        val menuLado = intent.getStringExtra("phoenix.menu_lado") ?: "ESQUERDA"
        val menuTema = intent.getBooleanExtra("phoenix.menu_tema", true)
        val menuAlca = intent.getBooleanExtra("phoenix.menu_alca", true)
        val menuGesto = intent.getBooleanExtra("phoenix.menu_gesto", true)

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
        
        val ajustesStr = intent.getStringExtra("phoenix.ajustes")
        if (!ajustesStr.isNullOrEmpty()) {
            ajustes = Gson().fromJson(ajustesStr, AjustesDeJogo::class.java)
            idDoJogoAtual = ajustes.idDoJogo
        }
        
        aplicarAjustesNoEmulador()

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
                        modifier = Modifier.let {
                            val wNat = nucleo.obterLarguraNativa().toFloat()
                            val hNat = nucleo.obterAlturaNativa().toFloat()
                            val aspectRatioNat = if (hNat > 0f) wNat / hNat else aspectRatio
                            if (ajustes.proporcao == ProporcaoImagem.ESTICAR) {
                                it.fillMaxSize()
                            } else {
                                val ratio = when (ajustes.proporcao) {
                                    ProporcaoImagem.AUTOMATICA -> aspectRatio
                                    ProporcaoImagem.PIXELS_QUADRADOS -> aspectRatioNat
                                    ProporcaoImagem.RATIO_4_3 -> 4f / 3f
                                    ProporcaoImagem.RATIO_16_9 -> 16f / 9f
                                    else -> aspectRatio
                                }
                                Log.i("PhoenixAjustes", "proporcao=${ajustes.proporcao} ratio=$ratio")
                                it.aspectRatio(ratio.coerceAtLeast(0.1f))
                            }
                        }
                    )

                    OverlayDeToque(
                        config = overlayConfig,
                        visivel = (!isPaused || !overlayConfig.ocultarNoMenu) && !controleOcultouOverlay,
                        corPrimaria = Color(temaCorPrimaria),
                        corAcento = Color(temaCorSuperficie),
                        aoMudarToque = { mask ->
                            motorDeAtalhos.atualizarTouchMask(mask)
                            // Se tocou na tela, reexibir overlay
                            controleOcultouOverlay = false
                        }
                    )

                    HudDoJogo(
                        rewindAtivo = rewindActive,
                        rewindSegundos = rewindSecs,
                        velocidadeAvanco = ffSpeed,
                        statsTexto = statsText,
                        mostrarStats = !isPaused && ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 || ajustes.mostrarFps),
                        avisoTexto = avisoTexto
                    )

                    if (!isPaused && menuAlca) {
                        Box(
                            modifier = Modifier
                                .align(if (menuLado == "DIREITA") Alignment.CenterEnd else Alignment.CenterStart)
                                .width(20.dp)
                                .height(80.dp)
                                .background(Color.White.copy(alpha = 0.15f), if (menuLado == "DIREITA") RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp) else RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                                .clickable(onClick = { alternarMenu() })
                        )
                    }
                    
                    if (!isPaused && menuGesto) {
                        Box(
                            modifier = Modifier
                                .align(if (menuLado == "DIREITA") Alignment.CenterEnd else Alignment.CenterStart)
                                .width(32.dp)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        if (menuLado == "DIREITA" && dragAmount < -20f) alternarMenu()
                                        else if (menuLado != "DIREITA" && dragAmount > 20f) alternarMenu()
                                    }
                                }
                        )
                    }

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
                            menuDesfoque = menuDesfoque,
                            menuOpacidade = menuOpacidade,
                            menuLado = menuLado,
                            menuTema = menuTema,
                            temaCorPrimaria = temaCorPrimaria,
                            temaCorSuperficie = temaCorSuperficie,
                            temaCorTexto = temaCorTexto,
                            amoled = amoled,
                            acabamento = acabamento.name,
                            reduzirEfeitos = reduzirEfeitos,
                            mapeamento = mapeamentoAtual,
                            atalhosJson = atalhosJson,
                            atalhosCfg = atalhosCfg,
                            capturando = capturaAtalho,
                            aoCapturar = { acao ->
                                capturaAtalho = acao
                            },
                            aoCancelarCaptura = {
                                capturaAtalho = null
                            },
                            aoLimparAtalho = { acao ->
                                atalhosCfg = atalhosCfg.comSemAtalho(acao)
                                aplicarNoMotor()
                                atalhosJson = Gson().toJson(atalhosCfg)
                                salvarAjuste("atalhos_json", atalhosJson, "string", false)
                            },
                            aoLimparHotkey = {
                                atalhosCfg = atalhosCfg.comHotkey(0)
                                aplicarNoMotor()
                                atalhosJson = Gson().toJson(atalhosCfg)
                                salvarAjuste("atalhos_json", atalhosJson, "string", false)
                            },
                            aoAlternarHotkey = { acao, usar ->
                                atalhosCfg = atalhosCfg.comUsarHotkey(acao, usar)
                                aplicarNoMotor()
                                atalhosJson = Gson().toJson(atalhosCfg)
                                salvarAjuste("atalhos_json", atalhosJson, "string", false)
                            },
                            aoRestaurarAtalhos = {
                                val ctx = contextoLocalizado ?: this@EmulatorActivity
                                atalhosCfg = atalhosCfg.restaurarPadrao()
                                atalhosJson = Gson().toJson(atalhosCfg)
                                salvarAjuste("atalhos_json", atalhosJson, "string", false)
                                aplicarNoMotor()
                                mensagemFeedback = ctx.getString(R.string.atalhos_restaurado)
                            },
                            aoRestaurarBotoes = { restaurarBotoesNoJogo() },
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
                            ajustes = ajustes,
                            aoMudarAjuste = { chave, valor, tipo -> 
                                if (chave == AjustesDeJogo.CHAVE_ESCALA && valor is String) {
                                    ajustes = ajustes.copy(escala = runCatching { EscalaImagem.valueOf(valor) }.getOrDefault(EscalaImagem.AJUSTAR))
                                } else if (chave == AjustesDeJogo.CHAVE_PROPORCAO && valor is String) {
                                    ajustes = ajustes.copy(proporcao = runCatching { ProporcaoImagem.valueOf(valor) }.getOrDefault(ProporcaoImagem.AUTOMATICA))
                                } else if (chave == AjustesDeJogo.CHAVE_MOSTRAR_FPS && valor is Boolean) {
                                    ajustes = ajustes.copy(mostrarFps = valor)
                                } else if (chave == AjustesDeJogo.CHAVE_VOLUME && valor is Float) {
                                    ajustes = ajustes.copy(volume = valor)
                                } else if (chave == AjustesDeJogo.CHAVE_MUDO && valor is Boolean) {
                                    ajustes = ajustes.copy(mudo = valor)
                                } else if (chave == AjustesDeJogo.CHAVE_VELOCIDADE_FF && valor is Int) {
                                    ajustes = ajustes.copy(velocidadeFF = valor)
                                } else if (chave == "escopo" && valor is Boolean) {
                                    // valor == true significa "somente este jogo"
                                    if (valor) {
                                        ajustes = ajustes.copy(overrides = setOf(AjustesDeJogo.CHAVE_ESCALA)) // Apenas simula que há override para mudar o selector
                                    } else {
                                        ajustes = ajustes.copy(overrides = emptySet())
                                    }
                                    return@MenuDePausa
                                }
                                if (ajustes.isSomenteEsteJogo()) {
                                    ajustes = ajustes.copy(overrides = ajustes.overrides + chave)
                                }
                                salvarAjuste(chave, valor, tipo, false)
                            },
                            aoLimparAjustesJogo = {
                                ajustes = ajustes.copy(overrides = emptySet())
                                salvarAjuste("", "", "", true)
                            }
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

                val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                LaunchedEffect(isPaused, isDebug, ajustes.mostrarFps) {
                    if (!isPaused && (isDebug || ajustes.mostrarFps)) {
                        while(true) {
                            delay(500)
                            val stats = nucleo.obterStats()
                            if (stats.size >= 3) {
                                val fpsStr = String.format(Locale.US, "%.1f", stats[0])
                                val msStr = String.format(Locale.US, "%.1f", stats[1])
                                val maxStr = String.format(Locale.US, "%.1f", stats[2])
                                statsText = if (isDebug) "$fpsStr fps | $msStr ms (max $maxStr)" else "$fpsStr FPS"
                            }
                        }
                    } else {
                        statsText = ""
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
        // Hide overlay on controller input if configured
        if (overlayConfig.visivelModo == com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.AUTO_ESCONDER_COM_CONTROLE &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
            event.keyCode != KeyEvent.KEYCODE_POWER) {
            controleOcultouOverlay = true
        }

        // PASSO 3: modo captura
        if (capturaAtalho != null) {
            val kc = event.keyCode
            // Ignorar teclas de volume e power sem capturá-las
            if (kc != KeyEvent.KEYCODE_VOLUME_UP && kc != KeyEvent.KEYCODE_VOLUME_DOWN
                && kc != KeyEvent.KEYCODE_VOLUME_MUTE && kc != KeyEvent.KEYCODE_POWER) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (kc == KeyEvent.KEYCODE_BACK) {
                        capturaAtalho = null
                        Log.d("PhoenixAjustes", "captura=$capturaAtalho")
                    } else if (capturaAtalho == "HOTKEY") {
                        definirHotkeyNoJogo(kc)
                        capturaAtalho = null
                        Log.d("PhoenixAjustes", "captura=$capturaAtalho")
                    } else if (capturaAtalho!!.startsWith("BTN_")) {
                        definirBotaoNoJogo(BotaoVirtual.valueOf(capturaAtalho!!.removePrefix("BTN_")), kc)
                        capturaAtalho = null
                        Log.d("PhoenixAjustes", "captura=$capturaAtalho")
                    } else {
                        try {
                            definirAtalhoNoJogo(AcaoAtalho.valueOf(capturaAtalho!!), kc)
                            capturaAtalho = null
                            Log.d("PhoenixAjustes", "captura=$capturaAtalho")
                        } catch (_: IllegalArgumentException) {
                            // nome inválido, cancela
                            capturaAtalho = null
                            Log.d("PhoenixAjustes", "captura=$capturaAtalho")
                        }
                    }
                }
                return true
            }
            // teclas ignoradas: retorna false para comportamento padrão
            return super.dispatchKeyEvent(event)
        }

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
        if (overlayConfig.visivelModo == com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.AUTO_ESCONDER_COM_CONTROLE &&
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            controleOcultouOverlay = true
        }

        if (capturaAtalho != null) {
            if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
                event.action == MotionEvent.ACTION_MOVE) {
                val l2 = if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > event.getAxisValue(MotionEvent.AXIS_BRAKE)) event.getAxisValue(MotionEvent.AXIS_LTRIGGER) else event.getAxisValue(MotionEvent.AXIS_BRAKE)
                val r2 = if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > event.getAxisValue(MotionEvent.AXIS_GAS)) event.getAxisValue(MotionEvent.AXIS_RTRIGGER) else event.getAxisValue(MotionEvent.AXIS_GAS)
                val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                val codigo = when {
                    l2 > 0.5f -> KeyEvent.KEYCODE_BUTTON_L2
                    r2 > 0.5f -> KeyEvent.KEYCODE_BUTTON_R2
                    hx < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                    hx > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                    hy < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                    hy > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                    else -> 0
                }
                if (codigo != 0) concluirCapturaPorEixo(codigo)
            }
            // enquanto capturar, não repasse eixos ao motor
            return true
        }

        if (eixoPrecisaSoltar) {
            if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
                event.action == MotionEvent.ACTION_MOVE) {
                val a1 = if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > event.getAxisValue(MotionEvent.AXIS_BRAKE)) event.getAxisValue(MotionEvent.AXIS_LTRIGGER) else event.getAxisValue(MotionEvent.AXIS_BRAKE)
                val a2 = if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > event.getAxisValue(MotionEvent.AXIS_GAS)) event.getAxisValue(MotionEvent.AXIS_RTRIGGER) else event.getAxisValue(MotionEvent.AXIS_GAS)
                val a3 = Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_X))
                val a4 = Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
                val maxVal = if (a1 > a2) a1 else a2
                val maxVal2 = if (maxVal > a3) maxVal else a3
                val solto = (if (maxVal2 > a4) maxVal2 else a4) < 0.3f
                if (solto) {
                    eixoPrecisaSoltar = false
                    motorDeAtalhos.leftTriggerPressed = false
                    motorDeAtalhos.rightTriggerPressed = false
                    motorDeAtalhos.dpadLeftPressed = false
                    motorDeAtalhos.dpadRightPressed = false
                    motorDeAtalhos.dpadUpPressed = false
                    motorDeAtalhos.dpadDownPressed = false
                }
            }
            return true
        }

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
        const val EXTRA_ATALHOS_CFG = "phoenix.atalhos_cfg"
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

fun ConfigDeAtalhos.comAtalho(acao: AcaoAtalho, codigo: Int): ConfigDeAtalhos {
    val mapa = acoes.toMutableMap()
    val atual = mapa[acao.name]?.copy() ?: AtalhoDaAcao()
    mapa[acao.name] = atual.copy(tecla = codigo)
    return copy(acoes = mapa)
}

fun ConfigDeAtalhos.comUsarHotkey(acao: AcaoAtalho, usar: Boolean): ConfigDeAtalhos {
    val mapa = acoes.toMutableMap()
    val atual = mapa[acao.name]?.copy() ?: AtalhoDaAcao()
    mapa[acao.name] = atual.copy(usarHotkey = usar)
    return copy(acoes = mapa)
}

fun ConfigDeAtalhos.comHotkey(codigo: Int): ConfigDeAtalhos {
    return copy(hotkey = codigo)
}

fun ConfigDeAtalhos.comSemAtalho(acao: AcaoAtalho): ConfigDeAtalhos {
    val mapa = acoes.toMutableMap()
    mapa[acao.name] = AtalhoDaAcao(0, usarHotkey = mapa[acao.name]?.usarHotkey != false)
    return copy(acoes = mapa)
}

fun ConfigDeAtalhos.restaurarPadrao(): ConfigDeAtalhos {
    return ConfigDeAtalhos.padrao()
}

