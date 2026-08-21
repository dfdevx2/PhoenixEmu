package com.dfdx047.phoenixemu

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// =====================================================================
// TELA: CONFIGURAÇÕES GLOBAIS (Áudio, Vídeo, Wallpaper e Temas)
// =====================================================================
@Composable
fun TelaConfiguracoes(audioEngine: AudioEngine, temaAtual: TemaApp, onMudarTema: (TemaApp) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE) }

    var bgmEnabled by remember { mutableStateOf(prefs.getBoolean("bgm_enabled", true)) }
    var sfxEnabled by remember { mutableStateOf(prefs.getBoolean("sfx_enabled", true)) }
    var bgmVolume by remember { mutableFloatStateOf(prefs.getFloat("bgm_volume", 1.0f)) }
    var sfxVolume by remember { mutableFloatStateOf(prefs.getFloat("sfx_volume", 1.0f)) }
    var proporcaoTela by remember { mutableStateOf(prefs.getString("proporcaoTela", "4:3 Original") ?: "4:3 Original") }
    var filtroVideo by remember { mutableStateOf(prefs.getString("filtroVideo", "Nenhum (Pixel Perfect)") ?: "Nenhum (Pixel Perfect)") }

    // Lançador para escolher o Wallpaper da galeria do telemóvel
    val escolherWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
            audioEngine.playClick()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // --- SECÇÃO: PERSONALIZAÇÃO (TEMAS E WALLPAPER) ---
        Text("PERSONALIZAÇÃO VISUAL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tema do Emulador", fontWeight = FontWeight.Bold)
                TemaApp.entries.forEach { temaOpcao ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = (temaAtual == temaOpcao), onClick = { audioEngine.playClick(); onMudarTema(temaOpcao) })
                        Text(text = obterNomeDoTema(temaOpcao))
                    }
                }
                HorizontalDivider()
                Text("Plano de Fundo (Biblioteca)", fontWeight = FontWeight.Bold)
                Text("Escolha uma imagem ou um GIF animado para ficar atrás da sua lista de jogos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { escolherWallpaper.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.padding(end = 8.dp)); Text("Escolher Fundo")
                    }
                    OutlinedButton(onClick = { prefs.edit().remove("wallpaper_uri").apply(); audioEngine.playClick() }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // --- SECÇÃO: ÁUDIO ---
        Text("ÁUDIO E SOM", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Música de Fundo (BGM)", fontWeight = FontWeight.Bold)
                    Switch(
                        checked = bgmEnabled,
                        onCheckedChange = {
                            audioEngine.playClick()
                            bgmEnabled = it
                            prefs.edit().putBoolean("bgm_enabled", it).apply()
                            audioEngine.atualizarVolumes() // LIGA O FIO!
                        }
                    )
                }
                if (bgmEnabled) {
                    Slider(
                        value = bgmVolume,
                        onValueChange = { bgmVolume = it },
                        onValueChangeFinished = {
                            prefs.edit().putFloat("bgm_volume", bgmVolume).apply()
                            audioEngine.atualizarVolumes() // LIGA O FIO!
                        },
                        valueRange = 0f..1f
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Efeitos Sonoros (SFX)", fontWeight = FontWeight.Bold)
                    Switch(
                        checked = sfxEnabled,
                        onCheckedChange = {
                            audioEngine.playClick()
                            sfxEnabled = it
                            prefs.edit().putBoolean("sfx_enabled", it).apply()
                            // O clique já lê a variável em tempo real, não precisa de atualizarVolumes aqui
                        }
                    )
                }
                if (sfxEnabled) {
                    Slider(
                        value = sfxVolume,
                        onValueChange = { sfxVolume = it },
                        onValueChangeFinished = {
                            prefs.edit().putFloat("sfx_volume", sfxVolume).apply()
                        },
                        valueRange = 0f..1f
                    )
                }
            }
        }

        // --- SECÇÃO: VÍDEO ---
        Text("VÍDEO E GRÁFICOS (GLOBAIS)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Proporção de Tela", fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp))
                listOf("4:3 Original", "16:9 (Widescreen)", "Esticar para a Tela").forEach { opcao -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { RadioButton(selected = (proporcaoTela == opcao), onClick = { audioEngine.playClick(); proporcaoTela = opcao; prefs.edit().putString("proporcaoTela", opcao).apply() }); Text(text = opcao) } }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Filtros de Imagem", fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp))
                listOf("Nenhum (Pixel Perfect)", "Bilinear Suave", "CRT Scanlines").forEach { opcao -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { RadioButton(selected = (filtroVideo == opcao), onClick = { audioEngine.playClick(); filtroVideo = opcao; prefs.edit().putString("filtroVideo", opcao).apply() }); Text(text = opcao) } }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// =====================================================================
// TELA: SOBRE O PROJETO
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
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Um emulador de NES e SNES construído do zero com foco absoluto em elegância, performance e comodidades modernas.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface); HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)); Text("Desenvolvido por:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text("dfdx047", style = MaterialTheme.typography.titleLarge) } }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { audioEngine.playClick(); uriHandler.openUri("https://github.com/dfdx047/PhoenixEmu") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Code, null, modifier = Modifier.padding(end = 8.dp)); Text("Acessar Repositório (GitHub)") }
    }
}

// =====================================================================
// TELA: CONFIGURAÇÕES INDIVIDUAIS POR JOGO
// =====================================================================
@Composable
fun TelaConfiguracoesJogo(jogo: Jogo?, audioEngine: AudioEngine) {
    if (jogo == null) return
    var overrideVideo by remember { mutableStateOf(false) }; var overrideControls by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Gamepad, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(modifier = Modifier.width(16.dp)); Column { Text("Você está editando regras exclusivas para:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer); Text(jogo.nome, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) } } }
        Text("SUBSTITUIÇÕES (OVERRIDES)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text("Substituir Configurações de Vídeo", fontWeight = FontWeight.Bold); Text("Ignora as opções globais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = overrideVideo, onCheckedChange = { audioEngine.playClick(); overrideVideo = it }) }; if (overrideVideo) { HorizontalDivider(); Text("Opções de vídeo exclusivas aparecerão aqui...", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(modifier = Modifier.weight(1f)) { Text("Mapeamento de Controles Específico", fontWeight = FontWeight.Bold); Text("Cria um perfil de botões único.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = overrideControls, onCheckedChange = { audioEngine.playClick(); overrideControls = it }) } } }
    }
}

// =====================================================================
// TELA: RETROACHIEVEMENTS
// =====================================================================
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
                        item { Text("Progresso Recente", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        items(jogosDoSistema.size) { index -> val stat = jogosDoSistema[index]; val progressFloat = stat.conquistasDesbloqueadas.toFloat() / stat.totalConquistas.toFloat(); val progressPercent = (progressFloat * 100).toInt()
                            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Column(modifier = Modifier.padding(16.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(stat.nome, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); if (progressPercent == 100) { Icon(Icons.Default.WorkspacePremium, "Platinado", tint = MaterialTheme.colorScheme.primary) } }; Spacer(modifier = Modifier.height(12.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${stat.conquistasDesbloqueadas} de ${stat.totalConquistas} Conquistas", style = MaterialTheme.typography.bodySmall); Text("$progressPercent%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(progress = progressFloat, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (progressPercent == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant) } }
                        }
                    }
                }
            }
        } else {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(32.dp), shape = RoundedCornerShape(24.dp)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary); Text("Vincular Conta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Utilizador") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Web API Key") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } }, modifier = Modifier.fillMaxWidth()); Button(onClick = { audioEngine.playClick(); if (username.isNotEmpty() && password.isNotEmpty()) { sharedPreferences.edit().putString("username", username).putBoolean("isLogged", true).apply(); isLogged = true; password = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar Sessão") } } }
        }
    }
}