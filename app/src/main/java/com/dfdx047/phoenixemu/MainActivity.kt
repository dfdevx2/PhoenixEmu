package com.dfdx047.phoenixemu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import com.dfdx047.phoenixemu.ui.theme.PhoenixEmuTheme

enum class TemaApp {
    DINAMICO, CLARO, ESCURO, AMOLED, NES_US, NES_JP, SNES_US, SNES_JP
}

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

data class Jogo(val nome: String, val extensao: String, val uri: Uri)

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = AudioEngine(this)

        setContent {
            var temaAtual by remember { mutableStateOf(TemaApp.DINAMICO) }

            PhoenixEmuTheme(temaAtual = temaAtual) {
                PhoenixApp(
                    audioEngine = audioEngine,
                    temaAtual = temaAtual,
                    onMudarTema = { novoTema -> temaAtual = novoTema }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        audioEngine.playBgm()
    }

    override fun onPause() {
        super.onPause()
        audioEngine.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
    }
}

enum class ModoVisual { GRADE, XMB }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoenixApp(
    audioEngine: AudioEngine,
    temaAtual: TemaApp,
    onMudarTema: (TemaApp) -> Unit
) {
    val context = LocalContext.current
    val abas = listOf(
        stringResource(id = R.string.tab_nes),
        stringResource(id = R.string.tab_snes)
    )

    val pagerState = rememberPagerState(pageCount = { abas.size })
    val coroutineScope = rememberCoroutineScope()
    var modoVisual by remember { mutableStateOf(ModoVisual.GRADE) }
    var primeiroCarregamento by remember { mutableStateOf(true) }

    var bibliotecaDeJogos by remember { mutableStateOf<List<Jogo>>(emptyList()) }
    var aEscanear by remember { mutableStateOf(false) }
    var progressoScan by remember { mutableFloatStateOf(0f) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var mostrarDialogoTemas by remember { mutableStateOf(false) }

    var ecraAtivo by remember { mutableStateOf("Biblioteca") }

    val abrirExplorador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            coroutineScope.launch {
                aEscanear = true
                progressoScan = 0f
                bibliotecaDeJogos = vasculharPasta(context, uri) { progresso -> progressoScan = progresso }
                delay(500)
                aEscanear = false
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (primeiroCarregamento) primeiroCarregamento = false
        else audioEngine.playSwipe()
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
                        text = stringResource(id = R.string.app_name),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.VideogameAsset, contentDescription = null) },
                    label = { Text("Biblioteca de Jogos") },
                    selected = ecraAtivo == "Biblioteca",
                    onClick = {
                        audioEngine.playClick()
                        ecraAtivo = "Biblioteca"
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    label = { Text("Mudar Tema / Cor") },
                    selected = false,
                    onClick = {
                        audioEngine.playClick()
                        coroutineScope.launch { drawerState.close() }
                        mostrarDialogoTemas = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                    label = { Text("RetroAchievements") },
                    selected = ecraAtivo == "RetroAchievements",
                    onClick = {
                        audioEngine.playClick()
                        ecraAtivo = "RetroAchievements"
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // NOVO: Clique das Configurações ativado!
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Configurações do Emulador") },
                    selected = ecraAtivo == "Configuracoes",
                    onClick = {
                        audioEngine.playClick()
                        ecraAtivo = "Configuracoes"
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("Sobre o Projeto") },
                    selected = false,
                    onClick = {
                        audioEngine.playClick()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Título Dinâmico consoante o ecrã
                val tituloTopo = when(ecraAtivo) {
                    "Biblioteca" -> stringResource(id = R.string.app_name)
                    "RetroAchievements" -> "RetroAchievements"
                    "Configuracoes" -> "Configurações"
                    else -> ""
                }

                TopAppBar(
                    title = { Text(text = tituloTopo, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            audioEngine.playClick()
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu Lateral")
                        }
                    },
                    actions = {
                        if (ecraAtivo == "Biblioteca") {
                            IconButton(onClick = {
                                audioEngine.playClick()
                                modoVisual = if (modoVisual == ModoVisual.GRADE) ModoVisual.XMB else ModoVisual.GRADE
                            }) {
                                Icon(
                                    imageVector = if (modoVisual == ModoVisual.GRADE) Icons.Default.ViewCarousel else Icons.Default.GridView,
                                    contentDescription = "Alternar Modo de Visualização"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                if (ecraAtivo == "Biblioteca") {
                    FloatingActionButton(
                        onClick = {
                            audioEngine.playClick()
                            abrirExplorador.launch(null)
                        },
                        modifier = Modifier.padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar Pasta de Jogos")
                    }
                }
            }
        ) { espacoInterno ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacoInterno)
            ) {
                // O NOSSO ROTEADOR DE ECRÃS
                when (ecraAtivo) {
                    "Biblioteca" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = pagerState.currentPage) {
                                abas.forEachIndexed { indice, titulo ->
                                    Tab(
                                        selected = pagerState.currentPage == indice,
                                        onClick = {
                                            coroutineScope.launch { pagerState.animateScrollToPage(indice) }
                                        },
                                        text = { Text(titulo) }
                                    )
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { paginaAtual ->
                                when (paginaAtual) {
                                    0 -> {
                                        val jogosNes = bibliotecaDeJogos.filter { it.extensao == "nes" || it.extensao == "zip" }
                                        TelaJogos(jogosNes, modoVisual, audioEngine)
                                    }
                                    1 -> {
                                        val jogosSnes = bibliotecaDeJogos.filter { it.extensao == "smc" || it.extensao == "sfc" || it.extensao == "zip" }
                                        TelaJogos(jogosSnes, modoVisual, audioEngine)
                                    }
                                }
                            }
                        }
                    }
                    "RetroAchievements" -> {
                        TelaRetroAchievements(audioEngine)
                    }
                    "Configuracoes" -> {
                        TelaConfiguracoes(audioEngine) // O NOSSO NOVO ECRÃ!
                    }
                }
            }

            if (aEscanear) {
                AlertDialog(
                    onDismissRequest = { },
                    confirmButton = {},
                    title = { Text("A ler os ficheiros...", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = progressoScan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(progressoScan * 100).toInt()}% concluído",
                                modifier = Modifier.align(Alignment.End),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (mostrarDialogoTemas) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogoTemas = false },
                    confirmButton = {
                        TextButton(onClick = {
                            audioEngine.playClick()
                            mostrarDialogoTemas = false
                        }) {
                            Text("Fechar")
                        }
                    },
                    title = { Text("Escolher Tema") },
                    text = {
                        Column {
                            TemaApp.entries.forEach { temaOpcao ->
                                TextButton(
                                    onClick = {
                                        audioEngine.playClick()
                                        onMudarTema(temaOpcao)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = obterNomeDoTema(temaOpcao),
                                        fontWeight = if (temaAtual == temaOpcao) FontWeight.Bold else FontWeight.Normal,
                                        color = if (temaAtual == temaOpcao) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

// --- ECRÃ: CONFIGURAÇÕES DO EMULADOR ---
@Composable
fun TelaConfiguracoes(audioEngine: AudioEngine) {
    val context = LocalContext.current
    // Uma "memória" diferente só para as configurações
    val prefs = remember { context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE) }

    // Lendo os estados guardados (ou valores padrão caso seja a primeira vez)
    var autoUpdater by remember { mutableStateOf(prefs.getBoolean("autoUpdater", true)) }
    var proporcaoTela by remember { mutableStateOf(prefs.getString("proporcaoTela", "4:3 Original") ?: "4:3 Original") }
    var filtroVideo by remember { mutableStateOf(prefs.getString("filtroVideo", "Nenhum (Pixel Perfect)") ?: "Nenhum (Pixel Perfect)") }

    // Scroll na tela inteira caso o usuário tenha uma tela pequena
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // --- SEÇÃO 1: SISTEMA ---
        Text(
            text = "SISTEMA",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Updater", fontWeight = FontWeight.Bold)
                    Text(
                        "Buscar por novas atualizações no GitHub automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // O INTERRUPTOR (Switch)
                Switch(
                    checked = autoUpdater,
                    onCheckedChange = { ligado ->
                        audioEngine.playClick()
                        autoUpdater = ligado
                        prefs.edit().putBoolean("autoUpdater", ligado).apply()
                    }
                )
            }
        }

        // --- SEÇÃO 2: VÍDEO E GRÁFICOS ---
        Text(
            text = "VÍDEO E GRÁFICOS",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Proporção de Tela", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // OS BOTÕES DE SELEÇÃO ÚNICA (RadioButton)
                val opcoesProporcao = listOf("4:3 Original", "16:9 (Widescreen)", "Esticar para a Tela")
                opcoesProporcao.forEach { opcao ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = (proporcaoTela == opcao),
                            onClick = {
                                audioEngine.playClick()
                                proporcaoTela = opcao
                                prefs.edit().putString("proporcaoTela", opcao).apply()
                            }
                        )
                        Text(text = opcao)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("Filtros de Imagem", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val opcoesFiltros = listOf("Nenhum (Pixel Perfect)", "Bilinear Suave", "CRT Scanlines")
                opcoesFiltros.forEach { opcao ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = (filtroVideo == opcao),
                            onClick = {
                                audioEngine.playClick()
                                filtroVideo = opcao
                                prefs.edit().putString("filtroVideo", opcao).apply()
                            }
                        )
                        Text(text = opcao)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp)) // Espaço no final para não colar na borda
    }
}

// --- ECRÃ: RETROACHIEVEMENTS ---
@Composable
fun TelaRetroAchievements(audioEngine: AudioEngine) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("RetroAchievementsPrefs", Context.MODE_PRIVATE) }

    var username by remember { mutableStateOf(sharedPreferences.getString("username", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLogged by remember { mutableStateOf(sharedPreferences.getBoolean("isLogged", false)) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLogged) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Bem-vindo de volta,\n$username!",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "O seu emulador está conectado e pronto para desbloquear conquistas nas suas ROMs.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        audioEngine.playClick()
                        isLogged = false
                        sharedPreferences.edit().putBoolean("isLogged", false).apply()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Desconectar")
                }
            }
        } else {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Vincular Conta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Utilizador") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Web API Key / Palavra-passe") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Mostrar senha")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            audioEngine.playClick()
                            if (username.isNotEmpty() && password.isNotEmpty()) {
                                sharedPreferences.edit()
                                    .putString("username", username)
                                    .putBoolean("isLogged", true)
                                    .apply()

                                isLogged = true
                                password = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar Sessão")
                    }
                }
            }
        }
    }
}

// --- FUNÇÃO PARA VASCULHAR A PASTA (O MOTOR SAF com Coroutines) ---
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
                val nomeLimpo = nomeCompleto.substringBeforeLast('.')
                jogosEncontrados.add(Jogo(nome = nomeLimpo, extensao = extensao, uri = ficheiro.uri))
            }
        }

        withContext(Dispatchers.Main) {
            onProgress((index + 1) / total.toFloat())
        }
    }

    return@withContext jogosEncontrados.sortedBy { it.nome }
}

// --- COMPONENTES VISUAIS ---
@Composable
fun TelaJogos(jogos: List<Jogo>, modoVisual: ModoVisual, audioEngine: AudioEngine) {
    if (jogos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nenhum jogo encontrado.\nClique no '+' para adicionar uma pasta.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            items(jogos) { jogo -> CartaoDeJogo(jogo = jogo, audioEngine = audioEngine) }
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
            val absoluteOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

            val scale = 1f - (0.15f * absoluteOffset)
            val alpha = 1f - (0.5f * absoluteOffset)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            ) {
                CartaoDeJogo(jogo = jogos[page], altura = 350.dp, audioEngine = audioEngine)
            }
        }
    }
}

@Composable
fun CartaoDeJogo(jogo: Jogo, altura: androidx.compose.ui.unit.Dp = 200.dp, audioEngine: AudioEngine) {
    ElevatedCard(
        onClick = {
            audioEngine.playClick()
            /* TODO: INICIAR A EMULAÇÃO AQUI! */
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(altura),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.cover_prefix, jogo.nome),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = jogo.nome,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Formato: .${jogo.extensao.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}