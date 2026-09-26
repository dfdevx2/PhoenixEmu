package com.dfdx047.phoenixemu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dfdx047.phoenixemu.data.BibliotecaStore
import com.dfdx047.phoenixemu.data.EstadoScan
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.Trabalhos
import com.dfdx047.phoenixemu.ui.theme.PhoenixEmuTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API: a janela ja nasce na cor do tema, o que mata o
        // flash branco que o Theme.Material.Light antigo causava.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Com targetSdk 37 o edge-to-edge e obrigatorio. Antes o codigo
        // tentava pintar a status bar com window.statusBarColor, que o
        // sistema simplesmente ignora nesse modo.
        enableEdgeToEdge()

        val prefs = Preferencias.obter(this)
        val biblioteca = BibliotecaStore.obter(this)
        val audio = AudioEngine.obter(this)

        // Mantem a splash enquanto o cache da biblioteca esta sendo lido:
        // o usuario nunca ve uma tela "Nenhum jogo encontrado" piscando.
        splash.setKeepOnScreenCondition { !biblioteca.carregado.value }

        setContent {
            val tema by prefs.tema.collectAsStateWithLifecycle()
            PhoenixEmuTheme(temaAtual = tema) {
                CompositionLocalProvider(LocalAudio provides audio) {
                    PhoenixApp(prefs = prefs, biblioteca = biblioteca)
                }
            }
        }
    }

    // Nada de release() aqui: o AudioEngine e do processo, nao da Activity.
    // Era justamente o release no onDestroy que fazia a BGM reiniciar a cada
    // rotacao de tela.
}

private enum class Ecra { BIBLIOTECA, CONQUISTAS, CONFIGURACOES, SOBRE, CONFIG_JOGO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixApp(prefs: Preferencias, biblioteca: BibliotecaStore) {
    val context = LocalContext.current
    val audio = LocalAudio.current
    val escopoUi = rememberCoroutineScope()

    // ---------------------------------------------------------- estado
    val jogos by biblioteca.jogos.collectAsStateWithLifecycle()
    val estadoScan by biblioteca.estadoScan.collectAsStateWithLifecycle()
    val modoVisual by prefs.modoVisual.collectAsStateWithLifecycle()
    val filtro by prefs.filtro.collectAsStateWithLifecycle()
    val ordenacao by prefs.ordenacao.collectAsStateWithLifecycle()
    val wallpaperUri by prefs.wallpaperUri.collectAsStateWithLifecycle()

    // Guardado como indice: sobrevive a rotacao sem depender de serializacao
    // de enum. Na Fase 2 isto vira navigation-compose com rotas tipadas.
    var ecraIndice by rememberSaveable { mutableIntStateOf(Ecra.BIBLIOTECA.ordinal) }
    val ecraAtivo = Ecra.entries[ecraIndice]
    var jogoEmFocoId by rememberSaveable { mutableStateOf<String?>(null) }
    var jogoDoMenuId by rememberSaveable { mutableStateOf<String?>(null) }

    val jogoEmFoco = remember(jogos, jogoEmFocoId) { jogos.firstOrNull { it.id == jogoEmFocoId } }
    val jogoDoMenu = remember(jogos, jogoDoMenuId) { jogos.firstOrNull { it.id == jogoDoMenuId } }

    var mostrarMenuOrdenacao by remember { mutableStateOf(false) }

    val hostDeSnackbar = remember { SnackbarHostState() }

    // Progresso das capas vem do WorkManager, nao mais do BibliotecaStore:
    // o download sobrevive ao app ser fechado, entao quem sabe o andamento
    // e o WorkManager.
    val progressoCapas by remember { Trabalhos.progresso(context, Trabalhos.CAPAS) }
        .collectAsStateWithLifecycle(initialValue = null)

    // Aviso do que a sincronizacao encontrou. O "Scanner Fantasma" original
    // era pensado para ser silencioso; o dialogo "Novos Jogos Encontrados"
    // contrariava isso e obrigava o usuario a autorizar algo que agora e
    // barato. Virou um aviso que nao interrompe nada.
    val resumo by biblioteca.ultimoResumo.collectAsStateWithLifecycle()
    val mensagemDeSync: String? = when {
        resumo.pastasComFalha > 0 -> stringResource(R.string.sync_pasta_falhou)
        resumo.adicionados > 0 && resumo.ausentes > 0 ->
            pluralStringResource(R.plurals.sync_adicionados, resumo.adicionados, resumo.adicionados) +
                " \u00b7 " +
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sistemas = remember { Sistema.entries }
    val pagerState = rememberPagerState(pageCount = { sistemas.size })

    // ------------------------------------------------- filtro e ordenacao
    //
    // Antes isso acontecia DENTRO da lambda do pager, ou seja, a biblioteca
    // inteira era filtrada e reordenada em cada recomposicao e para cada
    // pagina. Agora e memoizado e so recalcula quando a lista, o filtro ou
    // a ordenacao realmente mudam.
    val jogosPorSistema = remember(jogos, filtro, ordenacao) {
        val agora = System.currentTimeMillis()
        sistemas.associateWith { sistema ->
            jogos.asSequence()
                .filter { it.sistema == sistema }
                .filter { jogo ->
                    when (filtro) {
                        FiltroBiblioteca.TODOS -> true
                        FiltroBiblioteca.FAVORITOS -> jogo.isFavorito
                        FiltroBiblioteca.RECENTES -> jogo.ultimaVezJogado > 0L &&
                            jogo.ultimaVezJogado <= agora
                    }
                }
                .sortedWith(
                    when (ordenacao) {
                        Ordenacao.NOME ->
                            compareBy<Jogo> { it.nome.lowercase() }
                        Ordenacao.MAIS_JOGADOS ->
                            compareByDescending<Jogo> { it.tempoJogadoMinutos }
                                .thenBy { it.nome.lowercase() }
                        Ordenacao.JOGADOS_RECENTE ->
                            compareByDescending<Jogo> { it.ultimaVezJogado }
                                .thenBy { it.nome.lowercase() }
                    }
                )
                .toList()
        }
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
        // A varredura roda no escopo do store, nao no da composicao: mudar
        // de tela no meio da importacao nao cancela mais nada.
        biblioteca.importarPastaAsync(uri)
    }

    // -------------------------------------------------- scanner fantasma
    //
    // Agora e incremental: compara a assinatura de cada arquivo com a que
    // esta na biblioteca e so abre o que mudou. Roda sem bloquear a tela e
    // enfileira as capas que faltarem.
    LaunchedEffect(Unit) {
        biblioteca.carregar()
        biblioteca.sincronizarAsync(bloqueante = false)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                ItemDoMenu(Icons.Default.VideogameAsset, R.string.nav_biblioteca, ecraAtivo == Ecra.BIBLIOTECA) {
                    ecraIndice = Ecra.BIBLIOTECA.ordinal
                    escopoUi.launch { drawerState.close() }
                }
                ItemDoMenu(Icons.Default.EmojiEvents, R.string.nav_conquistas, ecraAtivo == Ecra.CONQUISTAS) {
                    ecraIndice = Ecra.CONQUISTAS.ordinal
                    escopoUi.launch { drawerState.close() }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ItemDoMenu(Icons.Default.Settings, R.string.nav_configuracoes, ecraAtivo == Ecra.CONFIGURACOES) {
                    ecraIndice = Ecra.CONFIGURACOES.ordinal
                    escopoUi.launch { drawerState.close() }
                }
                ItemDoMenu(Icons.Default.Info, R.string.nav_sobre, ecraAtivo == Ecra.SOBRE) {
                    ecraIndice = Ecra.SOBRE.ordinal
                    escopoUi.launch { drawerState.close() }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                val titulo = when (ecraAtivo) {
                    Ecra.BIBLIOTECA -> stringResource(R.string.app_name)
                    Ecra.CONQUISTAS -> stringResource(R.string.nav_conquistas)
                    Ecra.CONFIGURACOES -> stringResource(R.string.titulo_configuracoes)
                    Ecra.SOBRE -> stringResource(R.string.nav_sobre)
                    Ecra.CONFIG_JOGO -> jogoEmFoco?.nome.orEmpty()
                }
                TopAppBar(
                    title = { Text(titulo, fontWeight = FontWeight.Bold, maxLines = 1) },
                    navigationIcon = {
                        if (ecraAtivo == Ecra.CONFIG_JOGO) {
                            IconButton(onClick = {
                                audio.playClick()
                                ecraIndice = Ecra.BIBLIOTECA.ordinal
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.acao_voltar)
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                audio.playClick()
                                escopoUi.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, stringResource(R.string.acao_menu))
                            }
                        }
                    },
                    actions = {
                        if (ecraAtivo == Ecra.BIBLIOTECA) {
                            IconButton(onClick = {
                                audio.playClick()
                                biblioteca.sincronizarAsync(bloqueante = true)
                            }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    stringResource(R.string.acao_sincronizar)
                                )
                            }
                            IconButton(onClick = {
                                audio.playClick()
                                prefs.definirModoVisual(
                                    if (modoVisual == ModoVisual.GRADE) ModoVisual.XMB else ModoVisual.GRADE
                                )
                            }) {
                                Icon(
                                    if (modoVisual == ModoVisual.GRADE) Icons.Default.ViewCarousel
                                    else Icons.Default.GridView,
                                    stringResource(R.string.acao_alternar_modo)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            snackbarHost = { SnackbarHost(hostDeSnackbar) },
            floatingActionButton = {
                if (ecraAtivo == Ecra.BIBLIOTECA) {
                    FloatingActionButton(
                        onClick = { audio.playClick(); abrirExplorador.launch(null) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.acao_adicionar_pasta))
                    }
                }
            }
        ) { espacoInterno ->
            Box(Modifier.fillMaxSize()) {

                // O wallpaper fica FORA do padding do Scaffold, entao aparece
                // tambem atras da barra de topo, como se espera de um fundo.
                if (wallpaperUri != null && ecraAtivo == Ecra.BIBLIOTECA) {
                    Wallpaper(uri = wallpaperUri!!)
                }

                Box(Modifier.padding(espacoInterno)) {
                    when (ecraAtivo) {
                        Ecra.BIBLIOTECA -> Column(Modifier.fillMaxSize()) {
                            PrimaryTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = Color.Transparent
                            ) {
                                sistemas.forEachIndexed { indice, sistema ->
                                    Tab(
                                        selected = pagerState.currentPage == indice,
                                        onClick = {
                                            audio.playSwipe()
                                            escopoUi.launch {
                                                pagerState.animateScrollToPage(indice)
                                            }
                                        },
                                        text = { Text(stringResource(sistema.rotulo)) }
                                    )
                                }
                            }

                            BarraDeFiltros(
                                filtroAtual = filtro,
                                ordenacaoAtual = ordenacao,
                                menuAberto = mostrarMenuOrdenacao,
                                onAbrirMenu = { mostrarMenuOrdenacao = it },
                                onFiltro = { prefs.definirFiltro(it) },
                                onOrdenacao = { prefs.definirOrdenacao(it) }
                            )

                            // As capas chegam em segundo plano, com os jogos
                            // ja visiveis. Um dialogo modal aqui anularia
                            // justamente isso, entao o progresso vira uma
                            // barra fina que nao impede o uso da lista.
                            val capas = progressoCapas
                            if (capas != null && capas.total > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.scan_buscando_capas),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(
                                            R.string.progresso_contagem,
                                            capas.feitos,
                                            capas.total
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { capas.fracao },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                )
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                // No modo XMB havia um HorizontalPager dentro
                                // deste, e os dois disputavam o mesmo gesto.
                                // Desligar o arraste do externo resolve sem
                                // tirar a troca de aba pelo toque.
                                userScrollEnabled = modoVisual == ModoVisual.GRADE,
                                key = { sistemas[it].name }
                            ) { pagina ->
                                TelaJogos(
                                    jogos = jogosPorSistema[sistemas[pagina]].orEmpty(),
                                    modoVisual = modoVisual,
                                    onFavorito = { biblioteca.alternarFavorito(it.id) },
                                    onJogoLongClick = { jogoDoMenuId = it.id }
                                )
                            }
                        }

                        Ecra.CONQUISTAS -> TelaRetroAchievements()
                        Ecra.CONFIGURACOES -> TelaConfiguracoes(prefs)
                        Ecra.SOBRE -> TelaSobre()
                        Ecra.CONFIG_JOGO -> TelaConfiguracoesJogo(jogoEmFoco)
                    }
                }

                // --------------------------------------------------- dialogos
                // Ficam dentro do mesmo Box de proposito: AlertDialog e
                // ModalBottomSheet vivem em outra janela, mas manter um unico
                // filho de layout no slot de conteudo do Scaffold evita
                // surpresa de medicao.
                val scan = estadoScan
                if (scan is EstadoScan.EmAndamento && scan.bloqueante) {
                    DialogoDeProgresso(scan)
                }

                if (jogoDoMenu != null) {
                    MenuDoJogo(
                        jogo = jogoDoMenu,
                        onFechar = { jogoDoMenuId = null },
                        onConfigurar = {
                            jogoEmFocoId = jogoDoMenu.id
                            ecraIndice = Ecra.CONFIG_JOGO.ordinal
                            jogoDoMenuId = null
                        },
                        onRemover = {
                            biblioteca.remover(jogoDoMenu.id)
                            jogoDoMenuId = null
                        }
                    )
                }
            }
        }
    }
}

// =====================================================================
// COMPONENTES DA BIBLIOTECA
// =====================================================================

@Composable
private fun ItemDoMenu(
    icone: ImageVector,
    rotulo: Int,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    val audio = LocalAudio.current
    NavigationDrawerItem(
        icon = { Icon(icone, null) },
        label = { Text(stringResource(rotulo)) },
        selected = selecionado,
        onClick = { audio.playClick(); onClick() },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun Wallpaper(uri: String) {
    val context = LocalContext.current
    // O ImageRequest e o ImageLoader agora vem de fora da composicao: antes
    // um ImageLoader novo era construido a cada recomposicao, cada um com
    // caches proprios. O loader singleton vem do PhoenixApplication.
    val pedido = remember(uri) {
        ImageRequest.Builder(context).data(Uri.parse(uri)).build()
    }
    AsyncImage(
        model = pedido,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraDeFiltros(
    filtroAtual: FiltroBiblioteca,
    ordenacaoAtual: Ordenacao,
    menuAberto: Boolean,
    onAbrirMenu: (Boolean) -> Unit,
    onFiltro: (FiltroBiblioteca) -> Unit,
    onOrdenacao: (Ordenacao) -> Unit
) {
    val audio = LocalAudio.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            FiltroBiblioteca.entries.forEach { opcao ->
                FilterChip(
                    selected = filtroAtual == opcao,
                    onClick = { audio.playClick(); onFiltro(opcao) },
                    label = { Text(stringResource(opcao.rotulo)) },
                    leadingIcon = if (filtroAtual == opcao) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
        Box {
            IconButton(onClick = { audio.playClick(); onAbrirMenu(true) }) {
                Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.acao_ordenar))
            }
            DropdownMenu(expanded = menuAberto, onDismissRequest = { onAbrirMenu(false) }) {
                Ordenacao.entries.forEach { opcao ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(opcao.rotulo),
                                fontWeight = if (ordenacaoAtual == opcao) FontWeight.Bold
                                else FontWeight.Normal
                            )
                        },
                        onClick = {
                            audio.playClick()
                            onOrdenacao(opcao)
                            onAbrirMenu(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TelaJogos(
    jogos: List<Jogo>,
    modoVisual: ModoVisual,
    onFavorito: (Jogo) -> Unit,
    onJogoLongClick: (Jogo) -> Unit
) {
    if (jogos.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.biblioteca_vazia), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.biblioteca_vazia_dica),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (modoVisual == ModoVisual.GRADE) {
        LazyVerticalGrid(
            // Adaptive em vez de Fixed(2): funciona em celular, em tablet e
            // na tela widescreen do Odin sem codigo condicional.
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = jogos,
                key = { it.id },
                // contentType uniforme deixa o Lazy reaproveitar as
                // subcomposicoes dos itens que saem da tela.
                contentType = { "jogo" }
            ) { jogo ->
                CartaoDeJogo(
                    jogo = jogo,
                    onFavorito = onFavorito,
                    onJogoLongClick = onJogoLongClick
                )
            }
        }
    } else {
        val estadoXmb = rememberPagerState(pageCount = { jogos.size })
        HorizontalPager(
            state = estadoXmb,
            contentPadding = PaddingValues(horizontal = 64.dp),
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            key = { jogos.getOrNull(it)?.id ?: it }
        ) { pagina ->
            val distancia = (
                (estadoXmb.currentPage - pagina) + estadoXmb.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val escala = 1f - (0.15f * distancia)
                        scaleX = escala
                        scaleY = escala
                        alpha = 1f - (0.5f * distancia)
                    }
            ) {
                CartaoDeJogo(
                    jogo = jogos[pagina],
                    altura = 350.dp,
                    onFavorito = onFavorito,
                    onJogoLongClick = onJogoLongClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartaoDeJogo(
    jogo: Jogo,
    altura: Dp = 210.dp,
    onFavorito: (Jogo) -> Unit,
    onJogoLongClick: (Jogo) -> Unit
) {
    val audio = LocalAudio.current
    val context = LocalContext.current

    // Nao existe mais `favoritoLocal`. Antes o estado do coracao vivia
    // dentro do cartao e o codigo fazia `jogo.isFavorito = ...` no objeto:
    // a UI mudava, o filtro "Favoritos" nao reagia e nada era salvo.
    // Agora a verdade esta no BibliotecaStore e o cartao so a desenha.
    val escalaDoCoracao by animateFloatAsState(
        targetValue = if (jogo.isFavorito) 1f else 0.9f,
        label = "escalaFavorito"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            // Arquivo sumido (cartao desmontado, pasta movida) nao some da
            // biblioteca: ele fica esmaecido. Favorito e tempo de jogo
            // sobrevivem a pasta voltar.
            .alpha(if (jogo.ausente) 0.45f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { audio.playClick() },
                onLongClick = { audio.playClick(); onJogoLongClick(jogo) }
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    // Arquivo local primeiro. Depois que o CapaWorker baixou,
                    // rolar a lista nao toca mais na rede nem depende do cache
                    // do Coil, que e limitado e pode ser descartado.
                    val fonteDaCapa: Any? = remember(jogo.capaLocal, jogo.capaUrl) {
                        jogo.capaLocal?.takeIf { it.isNotBlank() }?.let(::File)
                            ?: jogo.capaUrl?.takeIf { it.isNotBlank() }
                    }
                    if (fonteDaCapa != null) {
                        val pedido = remember(fonteDaCapa) {
                            ImageRequest.Builder(context)
                                .data(fonteDaCapa)
                                .crossfade(true)
                                .build()
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
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                        )
                    }

                    if (jogo.ausente) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Icon(
                                Icons.Default.LinkOff,
                                stringResource(R.string.jogo_ausente),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    }

                    if (jogo.temRegiaoConhecida) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(topEnd = 8.dp),
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = jogo.regiao,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(Modifier.padding(12.dp)) {
                    Text(
                        jogo.nome,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (jogo.tempoJogadoMinutos > 0) {
                            stringResource(R.string.cartao_tempo_jogado, jogo.tempoJogadoMinutos)
                        } else {
                            stringResource(R.string.cartao_nunca_jogado)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IconButton(
                onClick = { audio.playClick(); onFavorito(jogo) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    imageVector = if (jogo.isFavorito) Icons.Default.Favorite
                    else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.cartao_favorito),
                    tint = if (jogo.isFavorito) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = escalaDoCoracao
                            scaleY = escalaDoCoracao
                        }
                )
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
        title = { Text(stringResource(R.string.scan_titulo), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(etapa), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                // A sobrecarga LinearProgressIndicator(progress: Float) esta
                // depreciada; a atual recebe uma lambda.
                LinearProgressIndicator(
                    progress = { estado.progresso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
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
