package com.dfdx047.phoenixemu

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

enum class TemaApp { DINAMICO, CLARO, ESCURO, AMOLED, NES_US, NES_JP, SNES_US, SNES_JP }

fun obterNomeDoTema(tema: TemaApp): String {
    return when(tema) {
        TemaApp.DINAMICO -> "Material You (Sistema)"
        TemaApp.CLARO -> "Tema Claro"
        TemaApp.ESCURO -> "Tema Escuro"
        TemaApp.AMOLED -> "Preto AMOLED Absoluto"
        TemaApp.NES_US -> "NES (Nintendinho Americano)"
        TemaApp.NES_JP -> "Famicom (Japonês)"
        TemaApp.SNES_US -> "Super Nintendo (Americano)"
        TemaApp.SNES_JP -> "Super Famicom (Japonês)"
    }
}

data class Jogo(
    val nome: String,
    val extensao: String,
    val uri: Uri,
    val sistema: String, // "NES" ou "SNES"
    var isFavorito: Boolean = false,
    var ultimaVezJogado: Long = 0L,
    var tempoJogadoMinutos: Int = 0,
    var capaUrl: String? = null
)

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
    val abas = listOf(stringResource(id = R.string.tab_nes), stringResource(id = R.string.tab_snes))

    val pagerState = rememberPagerState(pageCount = { abas.size })
    val coroutineScope = rememberCoroutineScope()
    var modoVisual by remember { mutableStateOf(ModoVisual.GRADE) }
    var primeiroCarregamento by remember { mutableStateOf(true) }

    val bibliotecaDeJogos = remember { mutableStateListOf<Jogo>() }
    var aEscanear by remember { mutableStateOf(false) }
    var progressoScan by remember { mutableFloatStateOf(0f) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var mostrarDialogoTemas by remember { mutableStateOf(false) }

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
            coroutineScope.launch {
                aEscanear = true; progressoScan = 0f
                val novosJogos = vasculharPasta(context, uri) { progresso -> progressoScan = progresso }
                bibliotecaDeJogos.clear()
                bibliotecaDeJogos.addAll(novosJogos)
                delay(500); aEscanear = false
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (primeiroCarregamento) primeiroCarregamento = false else audioEngine.playSwipe()
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
                NavigationDrawerItem(icon = { Icon(Icons.Default.Palette, null) }, label = { Text("Mudar Tema / Cor") }, selected = false, onClick = { audioEngine.playClick(); coroutineScope.launch { drawerState.close() }; mostrarDialogoTemas = true }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(icon = { Icon(Icons.Default.EmojiEvents, null) }, label = { Text("RetroAchievements") }, selected = ecraAtivo == "RetroAchievements", onClick = { audioEngine.playClick(); ecraAtivo = "RetroAchievements"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Configurações do Emulador") }, selected = ecraAtivo == "Configuracoes", onClick = { audioEngine.playClick(); ecraAtivo = "Configuracoes"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Info, null) }, label = { Text("Sobre o Projeto") }, selected = ecraAtivo == "Sobre", onClick = { audioEngine.playClick(); ecraAtivo = "Sobre"; coroutineScope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
            }
        }
    ) {
        Scaffold(
            topBar = {
                val tituloTopo = when(ecraAtivo) {
                    "Biblioteca" -> stringResource(id = R.string.app_name)
                    "RetroAchievements" -> "RetroAchievements"
                    "Configuracoes" -> "Configurações Globais"
                    "Sobre" -> "Sobre o Projeto"
                    "ConfiguracoesJogo" -> "Definições: ${jogoEmFoco?.nome}"
                    else -> ""
                }
                TopAppBar(
                    title = { Text(text = tituloTopo, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (ecraAtivo == "ConfiguracoesJogo") {
                            IconButton(onClick = { audioEngine.playClick(); ecraAtivo = "Biblioteca" }) { Icon(Icons.Default.ArrowBack, "Voltar para a Biblioteca") }
                        } else {
                            IconButton(onClick = { audioEngine.playClick(); coroutineScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu Lateral") }
                        }
                    },
                    actions = {
                        if (ecraAtivo == "Biblioteca") {
                            IconButton(onClick = { audioEngine.playClick(); modoVisual = if (modoVisual == ModoVisual.GRADE) ModoVisual.XMB else ModoVisual.GRADE }) {
                                Icon(if (modoVisual == ModoVisual.GRADE) Icons.Default.ViewCarousel else Icons.Default.GridView, "Alternar Modo")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                )
            },
            floatingActionButton = {
                if (ecraAtivo == "Biblioteca") {
                    FloatingActionButton(onClick = { audioEngine.playClick(); abrirExplorador.launch(null) }, modifier = Modifier.padding(16.dp), containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Icon(Icons.Default.Add, "Adicionar Pasta")
                    }
                }
            }
        ) { espacoInterno ->
            Box(modifier = Modifier.fillMaxSize().padding(espacoInterno)) {
                when (ecraAtivo) {
                    "Biblioteca" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = pagerState.currentPage) {
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
                                val jogosFiltrados = bibliotecaDeJogos.filter { jogo ->
                                    val pertenceNaAba = if (paginaAtual == 0) jogo.sistema == "NES" else jogo.sistema == "SNES"
                                    val passaNoFiltro = when (filtroAtual) { "Favoritos" -> jogo.isFavorito; "Recentes" -> jogo.ultimaVezJogado > 0L; else -> true }
                                    pertenceNaAba && passaNoFiltro
                                }

                                val jogosOrdenados = when (ordenacaoAtual) {
                                    "Mais Jogados" -> jogosFiltrados.sortedByDescending { it.tempoJogadoMinutos }
                                    "Jogados Recente" -> jogosFiltrados.sortedByDescending { it.ultimaVezJogado }
                                    else -> jogosFiltrados.sortedBy { it.nome }
                                }
                                TelaJogos(jogos = jogosOrdenados, modoVisual = modoVisual, audioEngine = audioEngine, onJogoLongClick = { jogo -> jogoSelecionadoParaMenu = jogo; mostrarBottomSheet = true })
                            }
                        }
                    }
                    "RetroAchievements" -> TelaRetroAchievements(audioEngine)
                    "Configuracoes" -> TelaConfiguracoes(audioEngine)
                    "Sobre" -> TelaSobre(audioEngine)
                    "ConfiguracoesJogo" -> TelaConfiguracoesJogo(jogoEmFoco, audioEngine)
                }
            }

            if (aEscanear) {
                AlertDialog(
                    onDismissRequest = { }, confirmButton = {}, title = { Text("A ler ficheiros e procurar capas...", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(progress = progressoScan, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "${(progressoScan * 100).toInt()}% concluído", modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (mostrarDialogoTemas) {
                AlertDialog(onDismissRequest = { mostrarDialogoTemas = false }, confirmButton = { TextButton(onClick = { audioEngine.playClick(); mostrarDialogoTemas = false }) { Text("Fechar") } }, title = { Text("Escolher Tema") }, text = { Column { TemaApp.entries.forEach { temaOpcao -> TextButton(onClick = { audioEngine.playClick(); onMudarTema(temaOpcao) }, modifier = Modifier.fillMaxWidth()) { Text(text = obterNomeDoTema(temaOpcao), fontWeight = if (temaAtual == temaOpcao) FontWeight.Bold else FontWeight.Normal, color = if (temaAtual == temaOpcao) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } } } })
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
                        ListItem(headlineContent = { Text("Configurações Individuais") }, supportingContent = { Text("Personalizar controlos e vídeo para este jogo") }, leadingContent = { Icon(Icons.Default.Tune, null) }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { audioEngine.playClick(); jogoEmFoco = jogo; ecraAtivo = "ConfiguracoesJogo"; mostrarBottomSheet = false }))
                        ListItem(headlineContent = { Text("Resetar Estatísticas") }, supportingContent = { Text("Zerar tempo de jogo acumulado") }, leadingContent = { Icon(Icons.Default.RestartAlt, null) }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { audioEngine.playClick(); jogo.tempoJogadoMinutos = 0; jogo.ultimaVezJogado = 0L; mostrarBottomSheet = false }))
                        ListItem(headlineContent = { Text("Remover da Biblioteca", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { audioEngine.playClick(); bibliotecaDeJogos.remove(jogo); mostrarBottomSheet = false }))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
// =====================================================================
// MODELOS JSON DO RAWG
// =====================================================================
data class RawgResponse(val results: List<RawgGame>?)
data class RawgGame(val name: String?, val background_image: String?)

// =====================================================================
// INTERFACE DA API DO RAWG (COM FILTRO DE PLATAFORMA)
// =====================================================================
interface RawgApi {
    @GET("games")
    suspend fun searchGames(
        @Query("search") query: String,
        @Query("platforms") platforms: String, // 49 = NES, 79 = SNES
        @Query("key") apiKey: String = "7836b5855e9f4cd29ff3e0401dbcbfcc"
    ): RawgResponse
}

object RetroScraper {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.rawg.io/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RawgApi::class.java)

    suspend fun buscarCapa(nomeLimpo: String, sistema: String): String? {
        val platformId = if (sistema == "NES") "49" else "79"
        return try {
            val response = api.searchGames(query = nomeLimpo, platforms = platformId)
            response.results?.firstOrNull { it.background_image != null }?.background_image
        } catch (e: Exception) {
            null
        }
    }
}

// =====================================================================
// FUNÇÃO PARA DESCOBRIR O SISTEMA DENTRO DO ZIP
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

// =====================================================================
// VASCULHAR PASTA COM IDENTIFICAÇÃO E LIMPEZA DE NOME
// =====================================================================
suspend fun vasculharPasta(
    context: Context,
    uriDaPasta: Uri,
    onProgress: (Float) -> Unit
): List<Jogo> = withContext(Dispatchers.IO) {
    val jogosEncontrados = mutableListOf<Jogo>()
    val pasta = DocumentFile.fromTreeUri(context, uriDaPasta) ?: return@withContext emptyList()
    val ficheiros = pasta.listFiles()
    val total = ficheiros.size

    if (total == 0) {
        withContext(Dispatchers.Main) { onProgress(1f) }
        return@withContext emptyList()
    }

    val extensoesValidas = listOf("nes", "smc", "sfc", "zip")

    ficheiros.forEachIndexed { index, ficheiro ->
        if (ficheiro.isFile) {
            val nomeCompleto = ficheiro.name ?: ""
            val extensao = nomeCompleto.substringAfterLast('.', "").lowercase()

            if (extensoesValidas.contains(extensao)) {
                val sistemaReal = descobrirSistemaReal(context, ficheiro.uri, extensao)

                if (sistemaReal != null) {
                    val nomeBruto = nomeCompleto.substringBeforeLast('.')

                    // LIMPEZA: Remove (USA), [!], (Rev A)
                    val nomeBonitoParaInterface = nomeBruto.replace(Regex("\\[.*?\\]|\\(.*?\\)"), "").trim()

                    val isFav = (1..10).random() > 8
                    val lastPlayed = if ((1..10).random() > 4) System.currentTimeMillis() - ((1..100).random() * 3600000L) else 0L
                    val tempoMin = if (lastPlayed > 0L) (5..240).random() else 0

                    val capaDescarregada = RetroScraper.buscarCapa(nomeBonitoParaInterface, sistemaReal)

                    jogosEncontrados.add(
                        Jogo(
                            nome = nomeBonitoParaInterface,
                            extensao = extensao,
                            uri = ficheiro.uri,
                            sistema = sistemaReal,
                            isFavorito = isFav,
                            ultimaVezJogado = lastPlayed,
                            tempoJogadoMinutos = tempoMin,
                            capaUrl = capaDescarregada
                        )
                    )
                }
            }
        }
        withContext(Dispatchers.Main) { onProgress((index + 1) / total.toFloat()) }
    }

    return@withContext jogosEncontrados.sortedBy { it.nome }
}

// =====================================================================
// COMPONENTES VISUAIS DA BIBLIOTECA (TELA DE JOGOS E CARTÃO)
// =====================================================================
@Composable
fun TelaJogos(jogos: List<Jogo>, modoVisual: ModoVisual, audioEngine: AudioEngine, onJogoLongClick: (Jogo) -> Unit) {
    if (jogos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum jogo encontrado para este filtro.\nClique no '+' para gerir pastas.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (modoVisual == ModoVisual.GRADE) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(jogos, key = { it.uri }) { jogo -> CartaoDeJogo(jogo = jogo, audioEngine = audioEngine, onJogoLongClick = onJogoLongClick) }
        }
    } else {
        val xmbPagerState = rememberPagerState(pageCount = { jogos.size })
        HorizontalPager(
            state = xmbPagerState,
            contentPadding = PaddingValues(horizontal = 64.dp),
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val pageOffset = (xmbPagerState.currentPage - page) + xmbPagerState.currentPageOffsetFraction
            val scale = 1f - (0.15f * pageOffset.absoluteValue.coerceIn(0f, 1f))
            val alpha = 1f - (0.5f * pageOffset.absoluteValue.coerceIn(0f, 1f))
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) {
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
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { audioEngine.playClick() },
                onLongClick = { audioEngine.playClick(); onJogoLongClick(jogo) }
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (jogo.capaUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(jogo.capaUrl).crossfade(true).build(),
                            contentDescription = "Capa de ${jogo.nome}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data("https://via.placeholder.com/400x600/121212/BB86FC?text=${jogo.nome.replace(" ", "+")}").crossfade(true).build(),
                            contentDescription = "Capa Genérica", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Text(jogo.nome, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    val textoTempo = if (jogo.tempoJogadoMinutos > 0) { val horas = jogo.tempoJogadoMinutos / 60; val mins = jogo.tempoJogadoMinutos % 60; if (horas > 0) "${horas}h ${mins}m jogados" else "${mins}m jogados" } else { "Nunca jogado" }
                    Text(textoTempo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    val textoUltimaVez = if (jogo.ultimaVezJogado > 0L) { val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()); "Último: ${sdf.format(Date(jogo.ultimaVezJogado))}" } else { "Novo na biblioteca" }
                    Text(textoUltimaVez, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            IconButton(
                onClick = { audioEngine.playClick(); favoritoLocal = !favoritoLocal; jogo.isFavorito = favoritoLocal },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(50))
            ) {
                Icon(if (favoritoLocal) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (favoritoLocal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// =====================================================================
// TELAS SECUNDÁRIAS (SOBRE, CONFIGURAÇÕES, JOGO E RETROACHIEVEMENTS)
// =====================================================================
@Composable
fun TelaSobre(audioEngine: AudioEngine) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(Icons.Default.Gamepad, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(id = R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Versão 0.1.0-alpha", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Um emulador de NES e SNES construído do zero com foco absoluto em elegância, performance e comodidades modernas (Material You).", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface); HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)); Text("Desenvolvido por:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text("dfdx047", style = MaterialTheme.typography.titleLarge) } }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { audioEngine.playClick(); uriHandler.openUri("https://github.com/dfdx047/PhoenixEmu") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Code, null, modifier = Modifier.padding(end = 8.dp)); Text("Acessar Repositório (GitHub)") }
    }
}

@Composable
fun TelaConfiguracoes(audioEngine: AudioEngine) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE) }
    var autoUpdater by remember { mutableStateOf(prefs.getBoolean("autoUpdater", true)) }
    var proporcaoTela by remember { mutableStateOf(prefs.getString("proporcaoTela", "4:3 Original") ?: "4:3 Original") }
    var filtroVideo by remember { mutableStateOf(prefs.getString("filtroVideo", "Nenhum (Pixel Perfect)") ?: "Nenhum (Pixel Perfect)") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("SISTEMA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text("Auto-Updater", fontWeight = FontWeight.Bold); Text("Buscar por novas atualizações no GitHub automaticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = autoUpdater, onCheckedChange = { audioEngine.playClick(); autoUpdater = it; prefs.edit().putBoolean("autoUpdater", it).apply() }) } }
        Text("VÍDEO E GRÁFICOS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(modifier = Modifier.padding(16.dp)) { Text("Proporção de Tela", fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp)); listOf("4:3 Original", "16:9 (Widescreen)", "Esticar para a Tela").forEach { opcao -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { RadioButton(selected = (proporcaoTela == opcao), onClick = { audioEngine.playClick(); proporcaoTela = opcao; prefs.edit().putString("proporcaoTela", opcao).apply() }); Text(text = opcao) } }; HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)); Text("Filtros de Imagem", fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp)); listOf("Nenhum (Pixel Perfect)", "Bilinear Suave", "CRT Scanlines").forEach { opcao -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { RadioButton(selected = (filtroVideo == opcao), onClick = { audioEngine.playClick(); filtroVideo = opcao; prefs.edit().putString("filtroVideo", opcao).apply() }); Text(text = opcao) } } } }
    }
}

@Composable
fun TelaConfiguracoesJogo(jogo: Jogo?, audioEngine: AudioEngine) {
    if (jogo == null) return
    var overrideVideo by remember { mutableStateOf(false) }; var overrideControls by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Gamepad, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(modifier = Modifier.width(16.dp)); Column { Text("Você está editando regras exclusivas para:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer); Text(jogo.nome, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) } } }
        Text("SUBSTITUIÇÕES (OVERRIDES)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text("Substituir Configurações de Vídeo", fontWeight = FontWeight.Bold); Text("Ignora as opções globais e usa opções específicas para este jogo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = overrideVideo, onCheckedChange = { audioEngine.playClick(); overrideVideo = it }) }; if (overrideVideo) { HorizontalDivider(); Text("Opções de vídeo exclusivas aparecerão aqui...", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text("Mapeamento de Controles Específico", fontWeight = FontWeight.Bold); Text("Cria um perfil de botões único para este jogo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = overrideControls, onCheckedChange = { audioEngine.playClick(); overrideControls = it }) } } }
    }
}

data class RetroGameStat(val nome: String, val sistema: String, val conquistasDesbloqueadas: Int, val totalConquistas: Int)

val mockStats = listOf(RetroGameStat("Super Mario Bros.", "NES", 12, 24), RetroGameStat("Castlevania", "NES", 5, 18), RetroGameStat("Super Mario World", "SNES", 45, 96), RetroGameStat("Chrono Trigger", "SNES", 10, 50), RetroGameStat("Donkey Kong Country", "SNES", 28, 28))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelaRetroAchievements(audioEngine: AudioEngine) {
    val context = LocalContext.current; val sharedPreferences = remember { context.getSharedPreferences("RetroAchievementsPrefs", Context.MODE_PRIVATE) }
    var username by remember { mutableStateOf(sharedPreferences.getString("username", "") ?: "") }; var password by remember { mutableStateOf("") }; var passwordVisible by remember { mutableStateOf(false) }; var isLogged by remember { mutableStateOf(sharedPreferences.getBoolean("isLogged", false)) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLogged) {
            val abasSistemas = listOf("NES", "SNES"); val pagerState = rememberPagerState(pageCount = { abasSistemas.size }); val coroutineScope = rememberCoroutineScope()
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Online • Hardcore Mode Ativado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }; IconButton(onClick = { audioEngine.playClick(); isLogged = false; sharedPreferences.edit().putBoolean("isLogged", false).apply() }) { Icon(Icons.Default.Logout, "Sair", tint = MaterialTheme.colorScheme.error) } }
                TabRow(selectedTabIndex = pagerState.currentPage) { abasSistemas.forEachIndexed { indice, titulo -> Tab(selected = pagerState.currentPage == indice, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(indice) } }, text = { Text(titulo) }) } }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { paginaAtual ->
                    val sistemaFiltro = abasSistemas[paginaAtual]
                    val jogosDoSistema = mockStats.filter { it.sistema == sistemaFiltro }
                    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        item { Text("Seu Progresso Recente", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        items(jogosDoSistema.size) { index -> val stat = jogosDoSistema[index]; val progressFloat = stat.conquistasDesbloqueadas.toFloat() / stat.totalConquistas.toFloat(); val progressPercent = (progressFloat * 100).toInt()
                            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Column(modifier = Modifier.padding(16.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stat.nome, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); if (progressPercent == 100) { Icon(Icons.Default.WorkspacePremium, "Platinado", tint = MaterialTheme.colorScheme.primary) } }; Spacer(modifier = Modifier.height(12.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${stat.conquistasDesbloqueadas} de ${stat.totalConquistas} Conquistas", style = MaterialTheme.typography.bodySmall); Text("$progressPercent%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(progress = progressFloat, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (progressPercent == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant) } }
                        }
                    }
                }
            }
        } else {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(32.dp), shape = RoundedCornerShape(24.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary); Text("Vincular Conta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Utilizador") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Web API Key / Palavra-passe") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } }, modifier = Modifier.fillMaxWidth()); Button(onClick = { audioEngine.playClick(); if (username.isNotEmpty() && password.isNotEmpty()) { sharedPreferences.edit().putString("username", username).putBoolean("isLogged", true).apply(); isLogged = true; password = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar Sessão") } } }
        }
    }
}