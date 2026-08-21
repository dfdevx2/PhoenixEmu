package com.dfdx047.phoenixemu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import com.dfdx047.phoenixemu.ui.theme.PhoenixEmuTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = AudioEngine(this)
        setContent {
            var temaAtual by remember { mutableStateOf(TemaApp.DINAMICO) }
            PhoenixEmuTheme(temaAtual = temaAtual) {
                PhoenixApp(audioEngine, temaAtual) { novoTema -> temaAtual = novoTema }
            }
        }
    }

    override fun onResume() { super.onResume(); audioEngine.playBgm() }
    override fun onPause() { super.onPause(); audioEngine.pauseBgm() }
    override fun onDestroy() { super.onDestroy(); audioEngine.release() }
}

enum class ModoVisual { GRADE, XMB }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixApp(audioEngine: AudioEngine, temaAtual: TemaApp, onMudarTema: (TemaApp) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE) }

    val abas = listOf(stringResource(id = R.string.tab_nes), stringResource(id = R.string.tab_snes))
    val pagerState = rememberPagerState(pageCount = { abas.size })
    val coroutineScope = rememberCoroutineScope()
    var modoVisual by remember { mutableStateOf(ModoVisual.GRADE) }

    val bibliotecaDeJogos = remember { mutableStateListOf<Jogo>() }
    var aEscanear by remember { mutableStateOf(false) }
    var progressoScan by remember { mutableFloatStateOf(0f) }

    var dialogNovosJogos by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var ecraAtivo by remember { mutableStateOf("Biblioteca") }
    var jogoEmFoco by remember { mutableStateOf<Jogo?>(null) }

    var filtroAtual by remember { mutableStateOf("Todos") }
    val opcoesFiltro = listOf("Todos", "Recentes", "Favoritos")
    var ordenacaoAtual by remember { mutableStateOf("Nome (A-Z)") }
    var mostrarMenuOrdenacao by remember { mutableStateOf(false) }

    var jogoSelecionadoParaMenu by remember { mutableStateOf<Jogo?>(null) }
    var mostrarBottomSheet by remember { mutableStateOf(false) }

    val abrirExplorador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            BibliotecaManager.adicionarPastaUri(context, uri)

            coroutineScope.launch {
                aEscanear = true; progressoScan = 0f
                val pasta = DocumentFile.fromTreeUri(context, uri)
                if (pasta != null) {
                    val ficheiros = pasta.listFiles().toList()
                    val ficheirosParaAnalisar = ficheiros.filter { f -> !bibliotecaDeJogos.any { it.nomeArquivoOriginal == f.name } }

                    if(ficheirosParaAnalisar.isNotEmpty()){
                        val novosJogos = vasculharFicheirosEspecificos(context, ficheirosParaAnalisar) { p -> progressoScan = p }
                        bibliotecaDeJogos.addAll(novosJogos)

                        val listaOrdenada = bibliotecaDeJogos.sortedBy { it.nome }
                        bibliotecaDeJogos.clear()
                        bibliotecaDeJogos.addAll(listaOrdenada)
                        BibliotecaManager.salvarJogos(context, bibliotecaDeJogos)
                    }
                }
                delay(500); aEscanear = false
            }
        }
    }

    // O SCANNER FANTASMA TOTALMENTE OPERACIONAL
    LaunchedEffect(Unit) {
        val cache = BibliotecaManager.carregarJogos(context)
        if (cache.isNotEmpty()) {
            bibliotecaDeJogos.addAll(cache)
        }

        val pastasSalvas = BibliotecaManager.obterPastasUris(context)
        val novosFicheirosEncontrados = mutableListOf<DocumentFile>()

        if (pastasSalvas.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (pastaUri in pastasSalvas) {
                    val pasta = DocumentFile.fromTreeUri(context, pastaUri)
                    if (pasta != null && pasta.canRead()) {
                        val ficheirosNaPasta = pasta.listFiles()
                        val extensoesValidas = listOf("nes", "smc", "sfc", "zip")

                        for (ficheiro in ficheirosNaPasta) {
                            val nomeCompleto = ficheiro.name ?: continue
                            val ext = nomeCompleto.substringAfterLast('.', "").lowercase()

                            if (extensoesValidas.contains(ext)) {
                                val jaExiste = bibliotecaDeJogos.any { it.nomeArquivoOriginal == nomeCompleto }
                                if (!jaExiste) novosFicheirosEncontrados.add(ficheiro)
                            }
                        }
                    }
                }
            }
            if (novosFicheirosEncontrados.isNotEmpty()) dialogNovosJogos = novosFicheirosEncontrados
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.BottomStart) {
                    Text(text = stringResource(id = R.string.app_name), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(icon = { Icon(Icons.Default.VideogameAsset, null) }, label = { Text("Biblioteca de Jogos") }, selected = ecraAtivo == "Biblioteca", onClick = { audioEngine.playClick(); ecraAtivo = "Biblioteca"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                NavigationDrawerItem(icon = { Icon(Icons.Default.EmojiEvents, null) }, label = { Text("RetroAchievements") }, selected = ecraAtivo == "RetroAchievements", onClick = { audioEngine.playClick(); ecraAtivo = "RetroAchievements"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Configurações do Emulador") }, selected = ecraAtivo == "Configuracoes", onClick = { audioEngine.playClick(); ecraAtivo = "Configuracoes"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Info, null) }, label = { Text("Sobre o Projeto") }, selected = ecraAtivo == "Sobre", onClick = { audioEngine.playClick(); ecraAtivo = "Sobre"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
            }
        }
    ) {
        Scaffold(
            topBar = {
                val tituloTopo = when(ecraAtivo) { "Biblioteca" -> stringResource(id = R.string.app_name); "RetroAchievements" -> "RetroAchievements"; "Configuracoes" -> "Configurações"; "Sobre" -> "Sobre"; "ConfiguracoesJogo" -> "${jogoEmFoco?.nome}"; else -> "" }
                TopAppBar(
                    title = { Text(text = tituloTopo, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (ecraAtivo == "ConfiguracoesJogo") { IconButton(onClick = { audioEngine.playClick(); ecraAtivo = "Biblioteca" }) { Icon(Icons.Default.ArrowBack, "Voltar") }
                        } else { IconButton(onClick = { audioEngine.playClick(); coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } }
                    },
                    actions = {
                        if (ecraAtivo == "Biblioteca") { IconButton(onClick = { audioEngine.playClick(); modoVisual = if (modoVisual == ModoVisual.GRADE) ModoVisual.XMB else ModoVisual.GRADE }) { Icon(if (modoVisual == ModoVisual.GRADE) Icons.Default.ViewCarousel else Icons.Default.GridView, "Alternar Modo") } }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                )
            },
            floatingActionButton = {
                if (ecraAtivo == "Biblioteca") {
                    FloatingActionButton(onClick = { audioEngine.playClick(); abrirExplorador.launch(null) }, modifier = Modifier.padding(16.dp), containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { Icon(Icons.Default.Add, "Pasta") }
                }
            }
        ) { espacoInterno ->
            Box(modifier = Modifier.fillMaxSize().padding(espacoInterno)) {

                val wallpaperUri = prefs.getString("wallpaper_uri", null)
                if (wallpaperUri != null && ecraAtivo == "Biblioteca") {
                    val imageLoader = ImageLoader.Builder(context).components {
                        if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                    }.build()
                    AsyncImage(model = ImageRequest.Builder(context).data(Uri.parse(wallpaperUri)).build(), imageLoader = imageLoader, contentDescription = "Wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                }

                when (ecraAtivo) {
                    "Biblioteca" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent) {
                                abas.forEachIndexed { indice, titulo -> Tab(selected = pagerState.currentPage == indice, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(indice) } }, text = { Text(titulo) }) }
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    opcoesFiltro.forEach { opcao -> FilterChip(selected = (filtroAtual == opcao), onClick = { audioEngine.playClick(); filtroAtual = opcao }, label = { Text(opcao) }, leadingIcon = if (filtroAtual == opcao) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null) }
                                }
                                Box {
                                    IconButton(onClick = { audioEngine.playClick(); mostrarMenuOrdenacao = true }) { Icon(Icons.Default.Sort, "Ordenar") }
                                    DropdownMenu(expanded = mostrarMenuOrdenacao, onDismissRequest = { mostrarMenuOrdenacao = false }) {
                                        listOf("Nome (A-Z)", "Mais Jogados", "Jogados Recente").forEach { opcao -> DropdownMenuItem(text = { Text(text = opcao, fontWeight = if (ordenacaoAtual == opcao) FontWeight.Bold else FontWeight.Normal) }, onClick = { audioEngine.playClick(); ordenacaoAtual = opcao; mostrarMenuOrdenacao = false }) }
                                    }
                                }
                            }

                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { paginaAtual ->
                                val sistemaDaAba = if (paginaAtual == 0) "NES" else "SNES"
                                val jogosFiltrados = bibliotecaDeJogos.filter { jogo ->
                                    val pertenceNaAba = jogo.sistema == sistemaDaAba
                                    val passaNoFiltro = when (filtroAtual) { "Favoritos" -> jogo.isFavorito; "Recentes" -> jogo.ultimaVezJogado > 0L; else -> true }
                                    pertenceNaAba && passaNoFiltro
                                }
                                val jogosOrdenados = when (ordenacaoAtual) { "Mais Jogados" -> jogosFiltrados.sortedByDescending { it.tempoJogadoMinutos }; "Jogados Recente" -> jogosFiltrados.sortedByDescending { it.ultimaVezJogado }; else -> jogosFiltrados.sortedBy { it.nome } }
                                TelaJogos(jogos = jogosOrdenados, modoVisual = modoVisual, audioEngine = audioEngine, onJogoLongClick = { jogo -> jogoSelecionadoParaMenu = jogo; mostrarBottomSheet = true })
                            }
                        }
                    }
                    "RetroAchievements" -> TelaRetroAchievements(audioEngine)
                    "Configuracoes" -> TelaConfiguracoes(audioEngine, temaAtual, onMudarTema)
                    "Sobre" -> TelaSobre(audioEngine)
                    "ConfiguracoesJogo" -> TelaConfiguracoesJogo(jogoEmFoco, audioEngine)
                }
            }

            if (dialogNovosJogos.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { dialogNovosJogos = emptyList() },
                    title = { Text("Novos Jogos Encontrados!") },
                    text = { Text("Foram detetados ${dialogNovosJogos.size} novos jogos nas suas pastas. Deseja analisar e transferir as capas agora?") },
                    confirmButton = {
                        Button(onClick = {
                            audioEngine.playClick()
                            val novosFicheiros = dialogNovosJogos
                            dialogNovosJogos = emptyList()

                            coroutineScope.launch {
                                aEscanear = true; progressoScan = 0f
                                val jogosAdicionados = vasculharFicheirosEspecificos(context, novosFicheiros) { p -> progressoScan = p }
                                bibliotecaDeJogos.addAll(jogosAdicionados)

                                val novaListaOrdenada = bibliotecaDeJogos.sortedBy { it.nome }
                                bibliotecaDeJogos.clear()
                                bibliotecaDeJogos.addAll(novaListaOrdenada)
                                BibliotecaManager.salvarJogos(context, bibliotecaDeJogos)
                                delay(500); aEscanear = false
                            }
                        }) { Text("Sim, Transferir") }
                    },
                    dismissButton = { TextButton(onClick = { audioEngine.playClick(); dialogNovosJogos = emptyList() }) { Text("Mais Tarde") } }
                )
            }

            if (aEscanear) {
                AlertDialog(onDismissRequest = { }, confirmButton = {}, title = { Text("A processar jogos...", fontWeight = FontWeight.Bold) }, text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(progress = progressoScan, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "${(progressoScan * 100).toInt()}% concluído", modifier = Modifier.align(Alignment.End))
                    }
                })
            }

            if (mostrarBottomSheet && jogoSelecionadoParaMenu != null) {
                val jogo = jogoSelecionadoParaMenu!!
                ModalBottomSheet(onDismissRequest = { mostrarBottomSheet = false }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.VideogameAsset, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Column { Text(jogo.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Formato: .${jogo.extensao.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider()
                        ListItem(headlineContent = { Text("Configurações Individuais") }, leadingContent = { Icon(Icons.Default.Tune, null) }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { audioEngine.playClick(); jogoEmFoco = jogo; ecraAtivo = "ConfiguracoesJogo"; mostrarBottomSheet = false }))
                        ListItem(headlineContent = { Text("Remover da Biblioteca") }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = {
                            audioEngine.playClick()
                            bibliotecaDeJogos.remove(jogo)
                            BibliotecaManager.salvarJogos(context, bibliotecaDeJogos)
                            mostrarBottomSheet = false
                        }))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// =====================================================================
// FUNÇÕES AUXILIARES
// =====================================================================
suspend fun descobrirSistemaReal(context: Context, uri: Uri, extensaoOriginal: String): String? = withContext(Dispatchers.IO) {
    if (extensaoOriginal == "nes") return@withContext "NES"
    if (extensaoOriginal == "smc" || extensaoOriginal == "sfc") return@withContext "SNES"
    if (extensaoOriginal == "zip") {
        var sistema: String? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val nomeExt = entry.name.substringAfterLast('.', "").lowercase()
                        if (nomeExt == "nes") { sistema = "NES"; break }
                        if (nomeExt == "smc" || nomeExt == "sfc") { sistema = "SNES"; break }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {}
        return@withContext sistema
    }
    return@withContext null
}

suspend fun vasculharFicheirosEspecificos(context: Context, ficheiros: List<DocumentFile>, onProgress: (Float) -> Unit): List<Jogo> = withContext(Dispatchers.IO) {
    val jogosEncontrados = mutableListOf<Jogo>()
    val total = ficheiros.size

    if (total == 0) {
        withContext(Dispatchers.Main) { onProgress(1f) }
        return@withContext emptyList()
    }

    ficheiros.forEachIndexed { index, ficheiro ->
        if (ficheiro.isFile) {
            val nomeCompleto = ficheiro.name ?: ""
            val extensao = nomeCompleto.substringAfterLast('.', "").lowercase()

            val sistemaReal = descobrirSistemaReal(context, ficheiro.uri, extensao)

            if (sistemaReal != null) {
                val nomeBruto = nomeCompleto.substringBeforeLast('.')
                val romAnalisada = RomParser.analisar(nomeBruto)
                val capaPrimaria = RetroScraper.buscarCapaDefinitiva(nomeBruto, sistemaReal)

                jogosEncontrados.add(
                    Jogo(
                        nome = romAnalisada.nomeLimpo,
                        nomeArquivoOriginal = nomeCompleto,
                        extensao = extensao,
                        uriString = ficheiro.uri.toString(), // <--- A MÁGICA: Convertemos para String antes de salvar!
                        sistema = sistemaReal,
                        regiao = romAnalisada.regiao,
                        isFavorito = false,
                        ultimaVezJogado = 0L,
                        tempoJogadoMinutos = 0,
                        capaUrl = capaPrimaria
                    )
                )
            }
        }
        withContext(Dispatchers.Main) { onProgress((index + 1) / total.toFloat()) }
    }
    return@withContext jogosEncontrados
}

// =====================================================================
// COMPONENTES VISUAIS DA BIBLIOTECA
// =====================================================================
@Composable
fun TelaJogos(jogos: List<Jogo>, modoVisual: ModoVisual, audioEngine: AudioEngine, onJogoLongClick: (Jogo) -> Unit) {
    if (jogos.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum jogo encontrado.", textAlign = TextAlign.Center) }; return }
    if (modoVisual == ModoVisual.GRADE) {
        // Usamos agora a uriString como identificador seguro e salvável
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            items(jogos, key = { it.uriString }) { jogo -> CartaoDeJogo(jogo = jogo, audioEngine = audioEngine, onJogoLongClick = onJogoLongClick) }
        }
    } else {
        val xmbPagerState = rememberPagerState(pageCount = { jogos.size })
        HorizontalPager(state = xmbPagerState, contentPadding = PaddingValues(horizontal = 64.dp), modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) { page ->
            val offset = ((xmbPagerState.currentPage - page) + xmbPagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = 1f - (0.15f * offset); scaleY = 1f - (0.15f * offset); alpha = 1f - (0.5f * offset) }) {
                CartaoDeJogo(jogo = jogos[page], altura = 350.dp, audioEngine = audioEngine, onJogoLongClick = onJogoLongClick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartaoDeJogo(jogo: Jogo, altura: androidx.compose.ui.unit.Dp = 200.dp, audioEngine: AudioEngine, onJogoLongClick: (Jogo) -> Unit) {
    var favoritoLocal by remember { mutableStateOf(jogo.isFavorito) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(altura).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = { audioEngine.playClick() }, onLongClick = { audioEngine.playClick(); onJogoLongClick(jogo) }),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    if (jogo.capaUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(jogo.capaUrl).crossfade(true).error(android.R.drawable.ic_menu_gallery).build(),
                            contentDescription = "Capa de ${jogo.nome}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (jogo.regiao != "Desconhecida") {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f), shape = RoundedCornerShape(topEnd = 8.dp), modifier = Modifier.align(Alignment.BottomStart)) {
                            Text(text = jogo.regiao.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(jogo.nome, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(if (jogo.tempoJogadoMinutos > 0) "${jogo.tempoJogadoMinutos}m jogados" else "Nunca jogado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(onClick = { audioEngine.playClick(); favoritoLocal = !favoritoLocal; jogo.isFavorito = favoritoLocal }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(50))) {
                Icon(if (favoritoLocal) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (favoritoLocal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
    }
}