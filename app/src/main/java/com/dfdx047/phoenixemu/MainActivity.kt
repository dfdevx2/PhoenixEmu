package com.dfdx047.phoenixemu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dfdx047.phoenixemu.data.BibliotecaStore
import com.dfdx047.phoenixemu.data.EstadoScan
import com.dfdx047.phoenixemu.data.Idioma
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.Trabalhos
import com.dfdx047.phoenixemu.emulacao.Emulador
import com.dfdx047.phoenixemu.ui.design.BarraDeBusca
import com.dfdx047.phoenixemu.ui.design.BotaoPilula
import com.dfdx047.phoenixemu.ui.design.BarraDeDicas
import com.dfdx047.phoenixemu.ui.design.ChipDeVidro
import com.dfdx047.phoenixemu.ui.design.EspacoDaNavegacao
import com.dfdx047.phoenixemu.ui.design.FundoDoTema
import com.dfdx047.phoenixemu.ui.design.SUPORTA_DESFOQUE
import com.dfdx047.phoenixemu.ui.design.fracaoSuavizada
import com.dfdx047.phoenixemu.ui.design.halo
import com.dfdx047.phoenixemu.ui.design.lembrarEstadoDoChrome
import com.dfdx047.phoenixemu.ui.design.TipoDeControle
import com.dfdx047.phoenixemu.ui.design.lembrarTipoDeControle
import com.dfdx047.phoenixemu.ui.design.ItemDeNavegacao
import com.dfdx047.phoenixemu.ui.design.LocalFundo
import com.dfdx047.phoenixemu.ui.design.LocalVidro
import com.dfdx047.phoenixemu.ui.design.NavegacaoDeLista
import com.dfdx047.phoenixemu.ui.design.PilulaDeNavegacao
import com.dfdx047.phoenixemu.ui.design.SeletorSegmentado
import com.dfdx047.phoenixemu.ui.design.SuperficieDeVidro
import com.dfdx047.phoenixemu.ui.design.fonteDeFundo
import com.dfdx047.phoenixemu.ui.design.lembrarEstadoDeFundo
import com.dfdx047.phoenixemu.ui.telas.BoasVindas
import com.dfdx047.phoenixemu.ui.telas.TelaControles
import com.dfdx047.phoenixemu.ui.theme.PhoenixEmuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class MainActivity : ComponentActivity() {

    private val trocasDeSecao = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val prefs = Preferencias.obter(this)
        if (prefs.ombrosTrocamSecao.value &&
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        ) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> { trocasDeSecao.tryEmit(-1); true }
                KeyEvent.KEYCODE_BUTTON_R1 -> { trocasDeSecao.tryEmit(+1); true }
                else -> super.dispatchKeyEvent(event)
            }
        }
        if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R1)
        ) {
            return true // consome o UP tambem para evitar re-dispatch
        }
        return super.dispatchKeyEvent(event)
    }

    // O idioma tem de estar escolhido antes de qualquer recurso ser
    // resolvido, e este e o unico ponto do ciclo de vida anterior a isso.
    // Veja o comentario em Idioma.kt para o motivo de ele nao vir do
    // DataStore.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Idioma.aplicar(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = Preferencias.obter(this)
        val biblioteca = BibliotecaStore.obter(this)
        val audio = AudioEngine.obter(this)

        // A biblioteca comeca a carregar AQUI, e nao dentro do PhoenixApp.
        //
        // Ela ficava num LaunchedEffect la dentro, e isso virou uma espera
        // circular no momento em que a tela de boas-vindas entrou na frente:
        // a splash esperava a biblioteca, a biblioteca so carregava dentro do
        // PhoenixApp, e o PhoenixApp nao compunha enquanto as boas-vindas
        // estivessem pendentes. Resultado: travado na splash para sempre.
        // Comecando pelo escopo da Activity, nenhuma tela pode bloquear isto.
        lifecycleScope.launch { biblioteca.carregar() }

        // A leitura do DataStore e assincrona: sem esperar por ela, o app
        // apareceria por alguns quadros no tema padrao antes de trocar para o
        // salvo. A splash cobre exatamente essa janela.
        splash.setKeepOnScreenCondition {
            // As preferencias vem primeiro: e delas que sai o tema e a decisao
            // de mostrar as boas-vindas.
            if (!prefs.carregado.value) return@setKeepOnScreenCondition true
            // Se as boas-vindas vao aparecer, nao existe lista para esperar.
            if (!prefs.primeiraExecucaoConcluida.value) {
                return@setKeepOnScreenCondition false
            }
            !biblioteca.carregado.value
        }

        setContent {
            val tema by prefs.tema.collectAsStateWithLifecycle()
            val reduzirEfeitos by prefs.reduzirEfeitos.collectAsStateWithLifecycle()
            val amoled by prefs.amoled.collectAsStateWithLifecycle()
            val acabamento by prefs.acabamento.collectAsStateWithLifecycle()
            // A splash ja esperou o DataStore carregar, entao este booleano
            // chega com o valor real -- nao ha um quadro em que a tela de
            // boas-vindas apareca para quem ja passou por ela.
            val boasVindasPendentes = !prefs.primeiraExecucaoConcluida
                .collectAsStateWithLifecycle().value

            PhoenixEmuTheme(
                temaAtual = tema,
                reduzirEfeitos = reduzirEfeitos,
                amoled = amoled,
                acabamento = acabamento
            ) {
                CompositionLocalProvider(LocalAudio provides audio) {
                    if (boasVindasPendentes) {
                        BoasVindas(
                            idiomaAtual = Idioma.atual(this@MainActivity),
                            temaAtual = tema,
                            acabamentoAtual = acabamento,
                            amoledAtual = amoled,
                            onIdioma = { tag ->
                                Idioma.definir(this@MainActivity, tag)
                                // Trocar de idioma troca a tabela de recursos,
                                // e isso acontece em attachBaseContext. Recompor
                                // nao basta: a Activity precisa nascer de novo.
                                recreate()
                            },
                            onTema = prefs::definirTema,
                            onAcabamento = prefs::definirAcabamento,
                            onAmoled = prefs::definirAmoled,
                            onConcluir = {
                                audio.playClick()
                                prefs.concluirPrimeiraExecucao()
                            }
                        )
                    } else {
                        PhoenixApp(prefs = prefs, biblioteca = biblioteca, trocas = trocasDeSecao)
                    }
                }
            }
        }
    }
}

// =====================================================================
// NAVEGACAO
// O drawer saiu. Cinco secoes numa pilula flutuante: e menos toque para
// chegar em qualquer lugar e, num handheld, e alcancavel com o polegar e
// com o D-pad. Cabem cinco porque so o item ativo mostra o rotulo.
// =====================================================================
private enum class Secao { BIBLIOTECA, CONQUISTAS, CONTROLES, AJUSTES, SOBRE }

@Composable
private fun itensDeNavegacao(): List<ItemDeNavegacao> = listOf(
    ItemDeNavegacao(Icons.Default.VideogameAsset, stringResource(R.string.nav_biblioteca_curto)),
    ItemDeNavegacao(Icons.Default.EmojiEvents, stringResource(R.string.nav_conquistas_curto)),
    ItemDeNavegacao(Icons.Default.SportsEsports, stringResource(R.string.nav_controles_curto)),
    ItemDeNavegacao(Icons.Default.Settings, stringResource(R.string.nav_ajustes_curto)),
    ItemDeNavegacao(Icons.Default.Info, stringResource(R.string.nav_sobre))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixApp(prefs: Preferencias, biblioteca: BibliotecaStore, trocas: kotlinx.coroutines.flow.SharedFlow<Int>) {
    val context = LocalContext.current
    val audio = LocalAudio.current
    val estilo = LocalVidro.current

    // ------------------------------------------------------------ estado
    // Filtro, busca e ordenacao acontecem no SQL agora. A tela so consome.
    val jogosVisiveis by biblioteca.jogosVisiveis.collectAsStateWithLifecycle()
    val estadoScan by biblioteca.estadoScan.collectAsStateWithLifecycle()
    val modoVisual by prefs.modoVisual.collectAsStateWithLifecycle()
    val filtro by prefs.filtro.collectAsStateWithLifecycle()
    val ordenacao by prefs.ordenacao.collectAsStateWithLifecycle()
    val wallpaperUri by prefs.wallpaperUri.collectAsStateWithLifecycle()
    val wallpaperDesfoque by prefs.wallpaperDesfoque.collectAsStateWithLifecycle()
    val wallpaperOpacidade by prefs.wallpaperOpacidade.collectAsStateWithLifecycle()

    var secaoIndice by rememberSaveable { mutableIntStateOf(Secao.BIBLIOTECA.ordinal) }
    val secao = Secao.entries[secaoIndice]

    var sistemaIndice by rememberSaveable { mutableIntStateOf(0) } // 0 = Todos
    var busca by rememberSaveable { mutableStateOf("") }
    var menuDeOrdenacaoAberto by remember { mutableStateOf(false) }
    // Guardamos o proprio Jogo, nao o id: com a lista vindo do banco ja
    // filtrada, procurar por id em memoria deixou de fazer sentido -- e o
    // objeto esta em maos no momento do toque longo.
    var jogoEmFoco by remember { mutableStateOf<Jogo?>(null) }
    var jogoDoMenu by remember { mutableStateOf<Jogo?>(null) }
    var jogoParaJogar by remember { mutableStateOf<Jogo?>(null) }

    val hostDeSnackbar = remember { SnackbarHostState() }
    val estadoGrade = rememberLazyGridState()
    val estadoCarrossel = rememberLazyListState()

    // A selecao e do app, nao do sistema de foco: e ela que o D-pad move e
    // que o toque atualiza, e e ela que desenha o halo.
    var indiceSelecionado by rememberSaveable { mutableIntStateOf(0) }
    // Filtrar ou buscar encurta a lista; sem isto a selecao ficaria apontando
    // para um indice que nao existe mais.
    LaunchedEffect(jogosVisiveis.size) {
        if (indiceSelecionado > jogosVisiveis.lastIndex) {
            indiceSelecionado = jogosVisiveis.lastIndex.coerceAtLeast(0)
        }
    }

    val progressoCapas by remember { Trabalhos.progresso(context, Trabalhos.CAPAS) }
        .collectAsStateWithLifecycle(initialValue = null)

    val resumo by biblioteca.ultimoResumo.collectAsStateWithLifecycle()
    val mensagemDeSync: String? = when {
        resumo.pastasComFalha > 0 -> stringResource(R.string.sync_pasta_falhou)
        resumo.adicionados > 0 && resumo.ausentes > 0 ->
            pluralStringResource(R.plurals.sync_adicionados, resumo.adicionados, resumo.adicionados) +
                " · " +
                pluralStringResource(R.plurals.sync_ausentes, resumo.ausentes, resumo.ausentes)
        resumo.adicionados > 0 ->
            pluralStringResource(R.plurals.sync_adicionados, resumo.adicionados, resumo.adicionados)
        resumo.ausentes > 0 ->
            pluralStringResource(R.plurals.sync_ausentes, resumo.ausentes, resumo.ausentes)
        else -> null
    }
    LaunchedEffect(mensagemDeSync) {
        if (mensagemDeSync != null) hostDeSnackbar.showSnackbar(mensagemDeSync)
    }

    // Ainda nao ha nucleo de emulacao. Em vez de o botao A nao fazer nada --
    // que parece defeito -- ele diz o que esta faltando.
    val aindaSemNucleo = stringResource(R.string.aviso_sem_nucleo)
    LaunchedEffect(jogoParaJogar) {
        val jogo = jogoParaJogar ?: return@LaunchedEffect
        jogoParaJogar = null
        Emulador.abrirJogo(context, prefs, jogo)
    }

    // ------------------------------------------------- filtro e ordenacao
    val rotulosDeSistema = listOf(stringResource(R.string.filtro_todos)) +
        Sistema.entries.map { it.rotuloCurto }

    // O que era filtragem em Kotlin virou parametro de consulta. O store
    // cuida do debounce da busca.
    LaunchedEffect(sistemaIndice) {
        biblioteca.definirSistema(
            if (sistemaIndice == 0) null else Sistema.entries[sistemaIndice - 1]
        )
    }
    LaunchedEffect(busca) { biblioteca.definirTermo(busca) }

    // Altura do chrome do topo: barra de busca + linha de sistema/filtros.
    // E o mesmo numero usado como recuo superior da lista, entao os dois
    // nunca saem de sincronia.
    val TOPO_XMB = 72.dp
    val BASE_XMB = 100.dp

    val xmbAtivo = modoVisual == ModoVisual.XMB && secao == Secao.BIBLIOTECA
    val tipoDeControle = lembrarTipoDeControle()
    val alturaDoTopo = if (secao != Secao.BIBLIOTECA) 72.dp else if (xmbAtivo) TOPO_XMB else 124.dp
    val alturaDoRodape = if (xmbAtivo) BASE_XMB else if (tipoDeControle == TipoDeControle.NENHUM) 104.dp else 148.dp
    val densidade = LocalDensity.current
    val alturaDoTopoPx = with(densidade) { alturaDoTopo.toPx() }
    val alturaDaNavegacaoPx = with(densidade) { alturaDoRodape.toPx() }
    val chrome = lembrarEstadoDoChrome(alturaDoTopo)

    // Trocar de secao com o chrome escondido deixaria a tela sem cabecalho.
    LaunchedEffect(secaoIndice) { chrome.mostrar() }

    // Trocar de secao via L1/R1 (SharedFlow do dispatchKeyEvent)
    val audioRef = audio
    val chromeRef = chrome
    LaunchedEffect(Unit) {
        trocas.collect { delta ->
            val novo = (secaoIndice + delta).coerceIn(0, Secao.entries.lastIndex)
            if (novo != secaoIndice) {
                audioRef.playSwipe()
                secaoIndice = novo
                chromeRef.mostrar()
            }
        }
    }

    val fabExpandido by remember {
        derivedStateOf { estadoGrade.firstVisibleItemIndex == 0 && estadoGrade.firstVisibleItemScrollOffset < 48 }
    }

    // ------------------------------------------------------- seletor SAF
    val abrirExplorador = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        biblioteca.adicionarPasta(uri)
        biblioteca.importarPastaAsync(uri)
    }

    LaunchedEffect(Unit) {
        biblioteca.carregar()
        biblioteca.sincronizarAsync(bloqueante = false)
    }

    // ---------------------------------------------------------- estrutura
    //
    // Duas camadas, e a separacao entre elas e o que faz o vidro funcionar:
    //
    //   1. FUNDO  -> wallpaper + conteudo rolavel. Gravado numa GraphicsLayer.
    //   2. VIDRO  -> as pilulas flutuantes. Ficam FORA do fundo, senao
    //                sampleariam a si mesmas e viraria realimentacao.
    val fundo = lembrarEstadoDeFundo()

    val ombrosTrocamSecao by prefs.ombrosTrocamSecao.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostDeSnackbar,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
            )
        }
    ) { _ ->
        val xmbAtivo = modoVisual == ModoVisual.XMB && secao == Secao.BIBLIOTECA

        Box(
            Modifier
                .fillMaxSize()
        ) {

            // ---------------------------------------------- camada 1: fundo
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(chrome.conexao)
                    .fonteDeFundo(fundo, ativo = estilo.desfoqueReal)
            ) {
                // Sem wallpaper, um gradiente do proprio tema: da ao vidro
                // algo para desfocar e evita que a primeira abertura do app
                // seja a tela mais sem graca dele.
                FundoDoTema()
                if (wallpaperUri != null) {
                    Wallpaper(
                        uri = wallpaperUri!!,
                        desfoque = wallpaperDesfoque,
                        opacidade = wallpaperOpacidade
                    )
                }

                // Nada aqui dentro pode ser superficie de vidro com desfoque.
                CompositionLocalProvider(LocalFundo provides null) {
                    AnimatedContent(
                        targetState = secao,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "troca de secao"
                    ) { alvo ->
                        when (alvo) {
                            Secao.BIBLIOTECA -> TelaJogos(
                                jogos = jogosVisiveis,
                                modoVisual = modoVisual,
                                estadoGrade = estadoGrade,
                                estadoCarrossel = estadoCarrossel,
                                recuoSuperior = alturaDoTopo,
                                recuoInferior = alturaDoRodape,
                                indiceSelecionado = indiceSelecionado,
                                comControle = tipoDeControle != TipoDeControle.NENHUM,
                                onSelecionar = { indiceSelecionado = it },
                                onAbrir = { jogoParaJogar = it },
                                onOpcoes = { jogoDoMenu = it }
                            )
                            Secao.CONQUISTAS -> TelaRetroAchievements()
                            Secao.CONTROLES -> TelaControles(prefs)
                            Secao.AJUSTES -> TelaConfiguracoes(prefs)
                            Secao.SOBRE -> TelaSobre()
                        }
                    }
                }
            }

            // ---------------------------------------------- camada 2: vidro
            CompositionLocalProvider(LocalFundo provides fundo) {

                // O topo desliza para cima e a navegacao para baixo conforme
                // a lista rola. Sem isso, em paisagem sobravam menos de duas
                // fileiras de capas visiveis -- e as pilulas cobriam
                // justamente a arte que a pessoa esta tentando olhar.
                val oculto = if (xmbAtivo) 0f else chrome.fracaoSuavizada()

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = -oculto * alturaDoTopoPx
                            alpha = 1f - oculto
                        }
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (secao == Secao.BIBLIOTECA) {
                        if (xmbAtivo) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (this@BoxWithConstraints.maxWidth >= 600.dp) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    BarraDeBusca(
                                        texto = busca,
                                        onTexto = { busca = it },
                                        dica = stringResource(R.string.busca_dica),
                                        iconeInicial = Icons.Default.Search,
                                        modifier = Modifier.weight(1f)
                                    )
                                    var mostrandoFiltros by remember { mutableStateOf(false) }
                                    BotaoRedondoDeVidro(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.acao_filtrar)) {
                                        audio.playClick()
                                        mostrandoFiltros = true
                                    }
                                    if (mostrandoFiltros) {
                                        DialogoDeFiltros(
                                            rotulosDeSistema = rotulosDeSistema,
                                            sistemaIndice = sistemaIndice,
                                            onSistema = { idx -> audio.playSwipe(); sistemaIndice = idx },
                                            filtro = filtro,
                                            onFiltro = { f -> audio.playClick(); prefs.definirFiltro(f) },
                                            ordenacao = ordenacao,
                                            onOrdenacao = { o -> audio.playClick(); prefs.definirOrdenacao(o) },
                                            onFechar = { mostrandoFiltros = false }
                                        )
                                    }
                                    BotaoRedondoDeVidro(Icons.Default.GridView, stringResource(R.string.acao_alternar_modo)) {
                                        audio.playClick()
                                        prefs.definirModoVisual(ModoVisual.GRADE)
                                    }
                                    BotaoRedondoDeVidro(Icons.Default.Refresh, stringResource(R.string.acao_sincronizar)) {
                                        audio.playClick()
                                        biblioteca.sincronizarAsync(bloqueante = true)
                                    }
                                }
                            }
                        } else {
                            BarraDeBusca(
                                texto = busca,
                                onTexto = { busca = it },
                                dica = stringResource(R.string.busca_dica),
                                iconeInicial = Icons.Default.Search,
                                modifier = Modifier.fillMaxWidth(),
                                acaoFinal = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            audio.playClick()
                                            biblioteca.sincronizarAsync(bloqueante = true)
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                stringResource(R.string.acao_sincronizar),
                                                tint = estilo.corDoConteudo.copy(alpha = 0.75f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = {
                                            audio.playClick()
                                            prefs.definirModoVisual(
                                                if (modoVisual == ModoVisual.GRADE) ModoVisual.XMB
                                                else ModoVisual.GRADE
                                            )
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(
                                                if (modoVisual == ModoVisual.GRADE) Icons.Default.ViewCarousel
                                                else Icons.Default.GridView,
                                                stringResource(R.string.acao_alternar_modo),
                                                tint = estilo.corDoConteudo.copy(alpha = 0.75f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            )

                            // Sistema, filtros e ordenacao numa linha so. Eram
                            // tres linhas empilhadas; em paisagem isso sozinho
                            // comia metade da altura util.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // O seletor segmentado substitui as abas +
                                    // pager: nao ha mais pager externo para
                                    // disputar o gesto com o carrossel XMB.
                                    SeletorSegmentado(
                                        opcoes = rotulosDeSistema,
                                        indiceSelecionado = sistemaIndice,
                                        onSelecionar = { audio.playSwipe(); sistemaIndice = it }
                                    )
                                    FiltroBiblioteca.entries.forEach { opcao ->
                                        ChipDeVidro(
                                            texto = stringResource(opcao.rotulo),
                                            selecionado = filtro == opcao,
                                            onClick = { audio.playClick(); prefs.definirFiltro(opcao) }
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    SuperficieDeVidro(
                                        modifier = Modifier.clip(CircleShape),
                                        forma = CircleShape
                                    ) {
                                        IconButton(onClick = {
                                            audio.playClick(); menuDeOrdenacaoAberto = true
                                        }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Sort,
                                                stringResource(R.string.acao_ordenar),
                                                tint = estilo.corDoConteudo
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = menuDeOrdenacaoAberto,
                                        onDismissRequest = { menuDeOrdenacaoAberto = false }
                                    ) {
                                        Ordenacao.entries.forEach { opcao ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(opcao.rotulo),
                                                        fontWeight = if (ordenacao == opcao) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    audio.playClick()
                                                    prefs.definirOrdenacao(opcao)
                                                    menuDeOrdenacaoAberto = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        TituloFlutuante(
                            texto = when (secao) {
                                Secao.CONQUISTAS -> stringResource(R.string.nav_conquistas)
                                Secao.CONTROLES -> stringResource(R.string.nav_controles)
                                Secao.AJUSTES -> stringResource(R.string.titulo_configuracoes)
                                else -> stringResource(R.string.nav_sobre)
                            }
                        )
                    }
                }

                // Rodape: progresso das capas e dicas de controle. Saiu do
                // topo -- la ele era uma quarta linha de chrome permanente
                // para uma informacao passageira.
                Column(
                    modifier = Modifier
                        .align(if (xmbAtivo) Alignment.BottomCenter else Alignment.BottomCenter)
                        .graphicsLayer {
                            if (!xmbAtivo) {
                                translationY = oculto * alturaDaNavegacaoPx
                                alpha = 1f - oculto
                            }
                        }
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val capas = progressoCapas
                    if (capas != null && capas.total > 0 && secao == Secao.BIBLIOTECA) {
                        SuperficieDeVidro(forma = CircleShape, forte = true) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.scan_buscando_capas),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(Modifier.width(10.dp))
                                LinearProgressIndicator(
                                    progress = { capas.fracao },
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(
                                        R.string.progresso_contagem,
                                        capas.feitos,
                                        capas.total
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // As dicas descrevem acoes da biblioteca; nas outras
                    // secoes elas so ocupariam espaco.
                    if (secao == Secao.BIBLIOTECA && !xmbAtivo) {
                        BarraDeDicas(
                            tipo = tipoDeControle,
                            dicas = listOf(
                                tipoDeControle.confirmar to stringResource(R.string.dica_jogar),
                                tipoDeControle.opcoes to stringResource(R.string.dica_opcoes)
                            )
                        )
                    }

                    PilulaDeNavegacao(
                        itens = itensDeNavegacao(),
                        indiceSelecionado = secaoIndice,
                        onSelecionar = {
                            audio.playClick()
                            secaoIndice = it
                            chrome.mostrar()
                        }
                    )
                }
                
                if (secao == Secao.BIBLIOTECA && xmbAtivo) {
                    BarraDeDicas(
                        tipo = tipoDeControle,
                        dicas = listOf(
                            tipoDeControle.confirmar to stringResource(R.string.dica_jogar),
                            tipoDeControle.opcoes to stringResource(R.string.dica_opcoes)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, bottom = 16.dp)
                    )
                }

                if (secao == Secao.BIBLIOTECA) {
                    BotaoPilula(
                        icone = Icons.Default.Add,
                        texto = stringResource(R.string.acao_adicionar_pasta),
                        expandido = xmbAtivo || (fabExpandido && oculto < 0.5f && modoVisual == ModoVisual.GRADE),
                        onClick = { audio.playClick(); abrirExplorador.launch(null) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .graphicsLayer {
                                if (!xmbAtivo) {
                                    translationY = oculto * alturaDaNavegacaoPx
                                    alpha = 1f - oculto
                                }
                            }
                            .navigationBarsPadding()
                            .padding(end = 16.dp, bottom = if (xmbAtivo) 16.dp else 96.dp)
                    )
                }
            }

            // ------------------------------------------------------ dialogos
            val scan = estadoScan
            if (scan is EstadoScan.EmAndamento && scan.bloqueante) {
                DialogoDeProgresso(scan)
            }

            // Copias locais: `by remember` cria propriedade DELEGADA, e o
            // Kotlin nao faz smart cast nelas -- o valor poderia mudar entre a
            // checagem de nulo e o uso. Com o val local, o compilador sabe que
            // nao muda.
            val menuAberto = jogoDoMenu
            if (menuAberto != null) {
                MenuDoJogo(
                    jogo = menuAberto,
                    onFechar = { jogoDoMenu = null },
                    onFavoritar = {
                        biblioteca.alternarFavorito(menuAberto.id)
                        jogoDoMenu = null
                    },
                    onConfigurar = {
                        jogoEmFoco = menuAberto
                        jogoDoMenu = null
                    },
                    onRemover = {
                        biblioteca.remover(menuAberto.id)
                        jogoDoMenu = null
                    }
                )
            }

            val configAberta = jogoEmFoco
            if (configAberta != null) {
                FolhaDeConfiguracoesDoJogo(
                    jogo = configAberta,
                    onFechar = { jogoEmFoco = null }
                )
            }
        }
    }
}

// =====================================================================
// PECAS DA TELA
// =====================================================================

@Composable
private fun TituloFlutuante(texto: String) {
    SuperficieDeVidro(forma = CircleShape, forte = true) {
        Text(
            texto,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LocalVidro.current.corDoConteudo,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun Wallpaper(uri: String, desfoque: Float, opacidade: Float) {
    val context = LocalContext.current
    val pedido = remember(uri) {
        ImageRequest.Builder(context).data(Uri.parse(uri)).build()
    }
    // O desfoque do wallpaper e `Modifier.blur`, que borra o proprio conteudo
    // -- aqui e o que se quer. Ele exige API 31; abaixo disso o modificador
    // nao faz nada, e o slider simplesmente nao tem efeito.
    val raio = (desfoque.coerceIn(0f, 1f) * 24f).dp

    AsyncImage(
        model = pedido,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .then(if (raio > 0.dp) Modifier.blur(raio) else Modifier)
            .alpha(opacidade.coerceIn(0f, 1f))
    )
    // Veu leve: garante contraste do texto sobre wallpapers claros sem
    // apagar a imagem. O vidro por cima faz o resto.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * opacidade))
    )
}

@Composable
fun TelaJogos(
    jogos: List<Jogo>,
    modoVisual: ModoVisual,
    estadoGrade: LazyGridState,
    estadoCarrossel: LazyListState,
    recuoSuperior: Dp,
    recuoInferior: Dp,
    indiceSelecionado: Int,
    comControle: Boolean,
    onSelecionar: (Int) -> Unit,
    onAbrir: (Jogo) -> Unit,
    onOpcoes: (Jogo) -> Unit
) {
    val recuo = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = recuoSuperior + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottom = 16.dp
    )

    if (jogos.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.biblioteca_vazia),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.biblioteca_vazia_dica),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        return
    }

    val gerenciadorDeFoco = LocalFocusManager.current
    val focoDaLista = remember { FocusRequester() }
    val emGrade = modoVisual == ModoVisual.GRADE

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Copiados para locais AQUI, no corpo do BoxWithConstraints, e nao
        // lidos mais para baixo: os escopos de layout do Compose sao marcados
        // com @DslMarker, entao dentro de um Box aninhado so o BoxScope mais
        // interno fica acessivel de forma implicita -- `maxHeight` do escopo de
        // fora passa a ser invisivel ali.
        val larguraTotal = maxWidth
        val alturaTotal = maxHeight

        // GridCells.Fixed com a contagem calculada, e nao Adaptive: a
        // navegacao por D-pad precisa saber quantas colunas existem para
        // "descer uma fileira" significar alguma coisa.
        val colunas = (((larguraTotal - 32.dp) / (156.dp + 14.dp)).toInt()).coerceIn(2, 8)

        // A lista inteira e UM alvo de foco. Quem se movimenta dentro dela e
        // o indice, nao o sistema de foco.
        //
        // O tratador de teclas fica num Box ANCESTRAL, e nao na mesma cadeia
        // do `focusable()`. Esse era o bug: evento de tecla sobe do no focado
        // pelos ancestrais e nunca desce, e com `.focusable().onKeyEvent {}` o
        // tratador era descendente do alvo de foco -- nao recebia nada, e a
        // grade nunca respondia ao controle. Inverter a ordem na mesma cadeia
        // resolveria, mas depender dela e fragil; um no pai realmente acima na
        // arvore nao deixa margem de duvida.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { evento ->
                    val r = NavegacaoDeLista.interpretar(
                        evento = evento,
                        indice = indiceSelecionado,
                        total = jogos.size,
                        colunas = colunas,
                        emGrade = emGrade
                    )
                    when (r.acao) {
                        NavegacaoDeLista.Acao.MOVER -> { onSelecionar(r.novoIndice); true }
                        // Consome sem mexer: se devolvesse false, o Compose
                        // levaria o foco para fora da lista na primeira borda.
                        NavegacaoDeLista.Acao.LIMITE -> true
                        NavegacaoDeLista.Acao.ABRIR -> {
                            jogos.getOrNull(indiceSelecionado)?.let(onAbrir); true
                        }
                        NavegacaoDeLista.Acao.OPCOES -> {
                            jogos.getOrNull(indiceSelecionado)?.let(onOpcoes); true
                        }
                        NavegacaoDeLista.Acao.SAIR_PARA_CIMA ->
                            gerenciadorDeFoco.moveFocus(NavegacaoDeLista.direcaoDeSaida)
                        NavegacaoDeLista.Acao.SAIR_PARA_BAIXO ->
                            gerenciadorDeFoco.moveFocus(NavegacaoDeLista.direcaoDeSaidaAbaixo)
                        NavegacaoDeLista.Acao.NADA -> false
                    }
                }
        ) {
            // Com controle ligado, a lista assume o foco assim que aparece. O
            // delay existe porque o alvo de foco precisa estar composto e
            // posicionado antes de aceitar o pedido -- sem ele, na primeira
            // abertura o requestFocus caia no vazio.
            LaunchedEffect(comControle, emGrade) {
                if (comControle) {
                    delay(120)
                    runCatching { focoDaLista.requestFocus() }
                }
            }

            // O alvo de foco propriamente dito: um filho vazio de tamanho
            // zero. Ele nao envolve a lista, so detem o foco -- assim nenhum
            // no rolavel ou clicavel da lista disputa com ele.
            Box(
                Modifier
                    .size(1.dp)
                    .focusRequester(focoDaLista)
                    .focusable()
            )

            if (emGrade) {
                // Mantem a capa selecionada visivel quando o indice muda pelo
                // controle. `animateScrollToItem` e inofensivo se ela ja estiver
                // na tela, porque a grade nao rola alem do necessario.
                LaunchedEffect(indiceSelecionado) {
                    val visivel = estadoGrade.layoutInfo.visibleItemsInfo
                        .any { it.index == indiceSelecionado }
                    if (!visivel) estadoGrade.animateScrollToItem(indiceSelecionado)
                }

                LazyVerticalGrid(
                    state = estadoGrade,
                    columns = GridCells.Fixed(colunas),
                    contentPadding = recuo,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = jogos.size,
                        key = { jogos[it].id },
                        contentType = { "jogo" }
                    ) { indice ->
                        val jogo = jogos[indice]
                        CartaoDeJogo(
                            jogo = jogo,
                            selecionado = indice == indiceSelecionado,
                            onClicar = { onSelecionar(indice); onAbrir(jogo) },
                            onOpcoes = { onSelecionar(indice); onOpcoes(jogo) }
                        )
                    }
                    item(span = { GridItemSpan(maxCurrentLineSpan) }) { EspacoDaNavegacao() }
                }
            } else {
                BoxWithConstraints {
                    CarrosselXmb(
                        jogos = jogos,
                        estadoCarrossel = estadoCarrossel,
                        indiceSelecionado = indiceSelecionado,
                        onSelecionar = onSelecionar,
                        onAbrir = onAbrir,
                        onOpcoes = onOpcoes
                    )
                }
            }
        }
    }
}

/**
 * Carrossel XMB.
 *
 * A capa central salta, ganha halo e as vizinhas recuam, escurecem e
 * desfocam. O desfoque aqui e `Modifier.blur`, que borra o PROPRIO conteudo
 * -- o oposto do que a pilula de vidro precisa, onde o desfoque tem que ser
 * do que esta atras.
 */
@Composable
private fun CarrosselXmb(
    jogos: List<Jogo>,
    estadoCarrossel: LazyListState,
    indiceSelecionado: Int,
    onSelecionar: (Int) -> Unit,
    onAbrir: (Jogo) -> Unit,
    onOpcoes: (Jogo) -> Unit
) {
    val primaria = MaterialTheme.colorScheme.primary

    var alturaDaCapa by remember { mutableFloatStateOf(200f) }
    var larguraDaCapa by remember { mutableFloatStateOf(156f) }

    var rolagemProgramatica by remember { mutableStateOf(false) }
    val arrastando by estadoCarrossel.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(indiceSelecionado) {
        val info = estadoCarrossel.layoutInfo
        val centro = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val central = info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - centro) }?.index ?: 0

        if (!arrastando && central != indiceSelecionado) {
            rolagemProgramatica = true
            try {
                estadoCarrossel.animateScrollToItem(indiceSelecionado)
            } finally {
                rolagemProgramatica = false
            }
        }
    }

    val densidade = LocalDensity.current
    val passoPx: Float = with(densidade) { (larguraDaCapa * 0.42f).dp.toPx() }
    val onSelecionarState by rememberUpdatedState(onSelecionar)
    val indiceSelecionadoState by rememberUpdatedState(indiceSelecionado)

    LaunchedEffect(Unit) {
        snapshotFlow<Float> {
            estadoCarrossel.firstVisibleItemIndex + estadoCarrossel.firstVisibleItemScrollOffset / passoPx
        }.map { it.roundToInt().coerceIn(0, jogos.lastIndex) }
         .distinctUntilChanged()
         .collect { novo: Int ->
             if (!rolagemProgramatica && novo != indiceSelecionadoState) {
                 onSelecionarState(novo)
             }
         }
    }

    BoxWithConstraints {
        val larguraDaTelaDp = maxWidth.value
        val alturaDisponivelDp = maxHeight.value

        val novaAltura = (alturaDisponivelDp - 8f).coerceIn(120f, 640f)
        val novaLargura = (novaAltura * 0.78f).coerceAtMost(larguraDaTelaDp * 0.42f)

        if (alturaDaCapa != novaAltura || larguraDaCapa != novaLargura) {
            alturaDaCapa = novaAltura
            larguraDaCapa = novaLargura
        }

        val recuoLateral = ((maxWidth.value - larguraDaCapa) / 2).coerceAtLeast(16f)

        LazyRow(
            state = estadoCarrossel,
            flingBehavior = rememberSnapFlingBehavior(estadoCarrossel),
            horizontalArrangement = Arrangement.spacedBy(-(larguraDaCapa * 0.58f).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
        ) {
            item { Spacer(Modifier.width((recuoLateral - larguraDaCapa * 0.21f).dp)) }

            items(count = jogos.size, key = { jogos[it].id }) { indice ->
                val d = indice - indiceSelecionado
                val ad = abs(d)

                if (ad > 4) {
                    Spacer(Modifier.width(larguraDaCapa.dp).height(alturaDaCapa.dp))
                } else {
                    val jogo = jogos[indice]

                    Box(
                        modifier = Modifier
                            .zIndex((10 - ad).toFloat())
                            .graphicsLayer {
                                val pos = estadoCarrossel.firstVisibleItemIndex + estadoCarrossel.firstVisibleItemScrollOffset / passoPx
                                val f = indice - pos
                                val af = abs(f).coerceAtMost(4f)
                                
                                val tabelaEscala = floatArrayOf(1.00f, 0.94f, 0.82f, 0.70f, 0.60f)
                                val tabelaAlfa = floatArrayOf(1.00f, 0.92f, 0.75f, 0.50f, 0.00f)
                                val tabelaDesloc = floatArrayOf(0.00f, 0.46f, 0.80f, 1.02f, 1.12f)
                                
                                fun interpolar(tabela: FloatArray, x: Float): Float {
                                    val ix = x.toInt().coerceAtMost(3)
                                    val p = x - ix
                                    return tabela[ix] * (1f - p) + tabela[ix + 1] * p
                                }
                                
                                val escala = interpolar(tabelaEscala, af)
                                scaleX = escala
                                scaleY = escala
                                alpha = interpolar(tabelaAlfa, af)
                                
                                val sinal = if (f < 0f) -1f else if (f > 0f) 1f else 0f
                                translationX = sinal * (interpolar(tabelaDesloc, af) * with(densidade) { larguraDaCapa.dp.toPx() } - passoPx * af)
                            }
                            .drawWithContent {
                                drawContent()
                                val pos = estadoCarrossel.firstVisibleItemIndex + estadoCarrossel.firstVisibleItemScrollOffset / passoPx
                                val f = indice - pos
                                val af = abs(f).coerceAtMost(4f)
                                val tabelaEscurecer = floatArrayOf(0.00f, 0.12f, 0.28f, 0.42f, 0.55f)
                                fun interpolar(tabela: FloatArray, x: Float): Float {
                                    val ix = x.toInt().coerceAtMost(3)
                                    val p = x - ix
                                    return tabela[ix] * (1f - p) + tabela[ix + 1] * p
                                }
                                val escurecimento = interpolar(tabelaEscurecer, af)
                                if (escurecimento > 0f) {
                                    drawRect(Color.Black.copy(alpha = escurecimento))
                                }
                            }
                    ) {
                        CartaoDeJogo(
                            jogo = jogo,
                            altura = with(densidade) { alturaDaCapa.dp },
                            largura = with(densidade) { larguraDaCapa.dp },
                            selecionado = ad == 0,
                            bordaDeFoco = ad == 0,
                            capaCheia = true,
                            corDoHalo = primaria,
                            onClicar = {
                                if (ad == 0) onAbrir(jogo)
                                else onSelecionar(indice)
                            },
                            onOpcoes = { onSelecionar(indice); onOpcoes(jogo) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.width((recuoLateral - larguraDaCapa * 0.21f).dp)) }
        }
    }
}

@Composable
private fun BotaoRedondoDeVidro(icone: ImageVector, descricao: String, onClick: () -> Unit) {
    SuperficieDeVidro(modifier = Modifier.clip(CircleShape), forma = CircleShape, forte = true) {
        IconButton(onClick = onClick) {
            Icon(icone, descricao, tint = LocalVidro.current.corDoConteudo)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoDeFiltros(
    rotulosDeSistema: List<String>,
    sistemaIndice: Int,
    onSistema: (Int) -> Unit,
    filtro: FiltroBiblioteca,
    onFiltro: (FiltroBiblioteca) -> Unit,
    ordenacao: Ordenacao,
    onOrdenacao: (Ordenacao) -> Unit,
    onFechar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFechar,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.titulo_filtros), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SeletorSegmentado(
                    opcoes = rotulosDeSistema,
                    indiceSelecionado = sistemaIndice,
                    onSelecionar = onSistema
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FiltroBiblioteca.entries.forEach { opcao ->
                        ChipDeVidro(
                            texto = stringResource(opcao.rotulo),
                            selecionado = filtro == opcao,
                            onClick = { onFiltro(opcao) }
                        )
                    }
                }

                HorizontalDivider()

                Column {
                    Ordenacao.entries.forEach { opcao ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .selectable(
                                    selected = ordenacao == opcao,
                                    role = Role.RadioButton,
                                    onClick = { onOrdenacao(opcao) }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = ordenacao == opcao, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(opcao.rotulo))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar) { 
                Text(stringResource(R.string.acao_fechar)) 
            }
        }
    )
}

/** Qual item esta mais perto do centro da viewport. */
private fun indiceMaisCentral(estado: LazyListState): Int {
    val info = estado.layoutInfo
    val centro = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo
        .minByOrNull { abs((it.offset + it.size / 2f) - centro) }
        ?.index ?: 0
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartaoDeJogo(
    jogo: Jogo,
    selecionado: Boolean,
    altura: Dp = 214.dp,
    largura: Dp? = null,
    bordaDeFoco: Boolean = false,
    capaCheia: Boolean = false,
    corDoHalo: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClicar: () -> Unit,
    onOpcoes: () -> Unit
) {
    val audio = LocalAudio.current
    val context = LocalContext.current
    val estilo = LocalVidro.current

    val brilho by animateFloatAsState(
        targetValue = if (selecionado) 1f else 0f,
        label = "brilhoDaSelecao"
    )

    val base = if (largura != null) Modifier.width(largura) else Modifier.fillMaxWidth()
    
    val modifierComBorda = if (bordaDeFoco) {
        base.border(
            3.dp, 
            Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
            ), 
            RoundedCornerShape(20.dp)
        )
    } else base

    val alphaOpcionais by animateFloatAsState(if (selecionado) 1f else 0f, label = "alphaOpcionais")

    SuperficieDeVidro(
        modifier = modifier
            .then(modifierComBorda)
            .height(altura)
            .alpha(if (jogo.ausente) 0.45f else 1f)
            .halo(brilho, corDoHalo)
            .focusProperties { canFocus = false }
            .combinedClickable(
                onClick = { audio.playClick(); onClicar() },
                onLongClick = { audio.playClick(); onOpcoes() }
            ),
        forma = RoundedCornerShape(20.dp),
        desfocar = false
    ) {
        if (capaCheia) {
            Box(modifier = Modifier.fillMaxSize()) {
                val fonteDaCapa: Any? = remember(jogo.capaLocal, jogo.capaUrl) {
                    jogo.capaLocal?.takeIf { it.isNotBlank() }?.let(::File)
                        ?: jogo.capaUrl?.takeIf { it.isNotBlank() }
                }
                if (fonteDaCapa != null) {
                    val pedido = remember(fonteDaCapa) {
                        ImageRequest.Builder(context).data(fonteDaCapa).crossfade(true).build()
                    }
                    AsyncImage(
                        model = pedido,
                        contentDescription = stringResource(R.string.cartao_capa, jogo.nome),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VideogameAsset,
                            null,
                            modifier = Modifier.size(44.dp),
                            tint = estilo.corDoConteudo.copy(alpha = 0.35f)
                        )
                    }
                }
                
                if (jogo.ausente) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart).alpha(alphaOpcionais)
                    ) {
                        Icon(
                            Icons.Default.LinkOff,
                            stringResource(R.string.jogo_ausente),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(14.dp)
                        )
                    }
                }

                if (jogo.temRegiaoConhecida) {
                    SuperficieDeVidro(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .alpha(alphaOpcionais),
                        forma = CircleShape,
                        forte = true,
                        desfocar = false
                    ) {
                        Text(
                            text = jogo.regiao,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (jogo.isFavorito) {
                    SuperficieDeVidro(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(30.dp)
                            .alpha(alphaOpcionais),
                        forma = CircleShape,
                        forte = true,
                        desfocar = false
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            stringResource(R.string.cartao_favorito),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(16.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .alpha(alphaOpcionais)
                ) {
                    Text(
                        jogo.nome,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val fonteDaCapa: Any? = remember(jogo.capaLocal, jogo.capaUrl) {
                        jogo.capaLocal?.takeIf { it.isNotBlank() }?.let(::File)
                            ?: jogo.capaUrl?.takeIf { it.isNotBlank() }
                    }
                    if (fonteDaCapa != null) {
                        val pedido = remember(fonteDaCapa) {
                            ImageRequest.Builder(context).data(fonteDaCapa).crossfade(true).build()
                        }
                        AsyncImage(
                            model = pedido,
                            contentDescription = stringResource(R.string.cartao_capa, jogo.nome),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.VideogameAsset,
                            null,
                            modifier = Modifier.size(44.dp),
                            tint = estilo.corDoConteudo.copy(alpha = 0.35f)
                        )
                    }

                    if (jogo.ausente) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Icon(
                                Icons.Default.LinkOff,
                                stringResource(R.string.jogo_ausente),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(14.dp)
                            )
                        }
                    }

                    if (jogo.temRegiaoConhecida) {
                        SuperficieDeVidro(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                            forma = CircleShape,
                            forte = true,
                            desfocar = false
                        ) {
                            Text(
                                text = jogo.regiao,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (jogo.isFavorito) {
                        SuperficieDeVidro(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(30.dp),
                            forma = CircleShape,
                            forte = true,
                            desfocar = false
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                stringResource(R.string.cartao_favorito),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(16.dp)
                            )
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        jogo.nome,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (jogo.tempoJogadoMinutos > 0) {
                            stringResource(R.string.cartao_tempo_jogado, jogo.tempoJogadoMinutos)
                        } else {
                            stringResource(R.string.cartao_nunca_jogado)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogoDeProgresso(estado: EstadoScan.EmAndamento) {
    val etapa = when (estado.etapa) {
        EstadoScan.Etapa.LENDO_PASTAS -> R.string.scan_lendo_pastas
        EstadoScan.Etapa.IDENTIFICANDO -> R.string.scan_identificando
    }
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {},
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.scan_titulo), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(etapa), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { estado.progresso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.scan_percentual,
                        (estado.progresso * 100).toInt()
                    ),
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MenuDoJogo(
    jogo: Jogo,
    onFechar: () -> Unit,
    onFavoritar: () -> Unit,
    onConfigurar: () -> Unit,
    onRemover: () -> Unit
) {
    val audio = LocalAudio.current
    ModalBottomSheet(onDismissRequest = onFechar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.VideogameAsset,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        jogo.nome,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.jogo_formato, jogo.extensao.uppercase()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
            // O coracao saiu do cartao: com controle nao ha como toca-lo, e
            // aqui ele fica acessivel pelos dois caminhos.
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (jogo.isFavorito) R.string.acao_desfavoritar
                            else R.string.acao_favoritar
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        if (jogo.isFavorito) Icons.Default.Favorite
                        else Icons.Default.FavoriteBorder,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { audio.playClick(); onFavoritar() })
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.acao_config_individuais)) },
                leadingContent = { Icon(Icons.Default.Tune, null) },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { audio.playClick(); onConfigurar() })
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.acao_remover)) },
                leadingContent = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { audio.playClick(); onRemover() })
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * As configuracoes por jogo deixaram de ser uma TELA e viraram uma folha.
 * Com a navegacao em quatro secoes fixas, uma quinta tela sem entrada propria
 * na barra ficaria orfa: o usuario chegava nela e nao sabia como voltar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaDeConfiguracoesDoJogo(jogo: Jogo, onFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onFechar) {
        TelaConfiguracoesJogo(jogo)
        Spacer(Modifier.height(24.dp))
    }
}
