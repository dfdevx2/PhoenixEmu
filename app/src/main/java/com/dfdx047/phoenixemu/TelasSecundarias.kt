package com.dfdx047.phoenixemu

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.Trabalhos
import kotlinx.coroutines.launch

/** Trocar pelo seu handle real do Ko-fi. */
private const val URL_KOFI = "https://ko-fi.com/dfdx047"
private const val URL_REPO = "https://github.com/dfdx047/PhoenixEmu"

// =====================================================================
// PECAS REAPROVEITADAS
// =====================================================================

@Composable
private fun TituloDeSecao(@StringRes texto: Int) {
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun Cartao(conteudo: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = conteudo
        )
    }
}

@Composable
private fun LinhaDeOpcao(
    @StringRes rotulo: Int,
    selecionado: Boolean,
    onSelecionar: () -> Unit
) {
    // Row selecionavel em vez de RadioButton solto: a area de toque passa a
    // ser a linha inteira, e o leitor de tela anuncia o item corretamente.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selecionado, role = Role.RadioButton, onClick = onSelecionar)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selecionado, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(rotulo))
    }
}

@Composable
private fun LinhaDeInterruptor(
    @StringRes rotulo: Int,
    @StringRes descricao: Int? = null,
    marcado: Boolean,
    onMudar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(rotulo), fontWeight = FontWeight.Bold)
            if (descricao != null) {
                Text(
                    stringResource(descricao),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = marcado, onCheckedChange = onMudar)
    }
}

// =====================================================================
// TELA: CONFIGURACOES GLOBAIS
// =====================================================================
@Composable
fun TelaConfiguracoes(prefs: Preferencias) {
    val context = LocalContext.current
    val audio = LocalAudio.current

    // Todos os valores vem de StateFlow agora. Antes eram lidos uma vez com
    // prefs.getX() e guardados em remember: mudar a preferencia em outro
    // lugar nao refletia aqui, nem o contrario.
    val temaAtual by prefs.tema.collectAsStateWithLifecycle()
    val bgmAtivo by prefs.bgmAtivo.collectAsStateWithLifecycle()
    val sfxAtivo by prefs.sfxAtivo.collectAsStateWithLifecycle()
    val bgmVolume by prefs.bgmVolume.collectAsStateWithLifecycle()
    val sfxVolume by prefs.sfxVolume.collectAsStateWithLifecycle()
    val reduzirEfeitos by prefs.reduzirEfeitos.collectAsStateWithLifecycle()
    val proporcao by prefs.proporcaoTela.collectAsStateWithLifecycle()
    val filtroVideo by prefs.filtroVideo.collectAsStateWithLifecycle()
    val wallpaperUri by prefs.wallpaperUri.collectAsStateWithLifecycle()

    // Os sliders usam estado local enquanto o dedo esta na tela e so gravam
    // ao soltar: evita uma escrita em disco por pixel arrastado.
    var bgmArrastado by remember(bgmVolume) { mutableFloatStateOf(bgmVolume) }
    var sfxArrastado by remember(sfxVolume) { mutableFloatStateOf(sfxVolume) }

    val escolherWallpaper = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.definirWallpaper(uri)
            audio.playClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ------------------------------------------------ personalizacao
        TituloDeSecao(R.string.sec_personalizacao)
        Cartao {
            Text(stringResource(R.string.config_tema), fontWeight = FontWeight.Bold)
            TemaApp.entries.forEach { opcao ->
                LinhaDeOpcao(
                    rotulo = opcao.rotulo,
                    selecionado = temaAtual == opcao,
                    onSelecionar = { audio.playClick(); prefs.definirTema(opcao) }
                )
            }

            HorizontalDivider()

            Text(stringResource(R.string.config_fundo), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.config_fundo_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { escolherWallpaper.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.acao_escolher_fundo))
                }
                OutlinedButton(
                    onClick = { prefs.definirWallpaper(null); audio.playClick() },
                    enabled = wallpaperUri != null
                ) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.acao_remover_fundo),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()

            LinhaDeInterruptor(
                rotulo = R.string.config_reduzir_efeitos,
                descricao = R.string.config_reduzir_efeitos_desc,
                marcado = reduzirEfeitos,
                onMudar = { audio.playClick(); prefs.definirReduzirEfeitos(it) }
            )
        }

        // -------------------------------------------------------- audio
        TituloDeSecao(R.string.sec_audio)
        Cartao {
            LinhaDeInterruptor(
                rotulo = R.string.config_bgm,
                marcado = bgmAtivo,
                onMudar = { audio.playClick(); prefs.definirBgmAtivo(it) }
            )
            if (bgmAtivo) {
                Slider(
                    value = bgmArrastado,
                    onValueChange = { bgmArrastado = it },
                    onValueChangeFinished = { prefs.definirBgmVolume(bgmArrastado) },
                    valueRange = 0f..1f
                )
            }

            HorizontalDivider()

            LinhaDeInterruptor(
                rotulo = R.string.config_sfx,
                marcado = sfxAtivo,
                onMudar = { audio.playClick(); prefs.definirSfxAtivo(it) }
            )
            if (sfxAtivo) {
                Slider(
                    value = sfxArrastado,
                    onValueChange = { sfxArrastado = it },
                    onValueChangeFinished = {
                        prefs.definirSfxVolume(sfxArrastado)
                        audio.playClick() // devolve o volume novo como feedback
                    },
                    valueRange = 0f..1f
                )
            }
        }

        // -------------------------------------------------------- video
        TituloDeSecao(R.string.sec_video)
        Cartao {
            Text(stringResource(R.string.config_proporcao), fontWeight = FontWeight.Bold)
            val proporcoes = listOf(
                R.string.proporcao_4_3,
                R.string.proporcao_16_9,
                R.string.proporcao_esticar
            )
            proporcoes.forEachIndexed { indice, rotulo ->
                LinhaDeOpcao(
                    rotulo = rotulo,
                    selecionado = proporcao == indice,
                    onSelecionar = { audio.playClick(); prefs.definirProporcaoTela(indice) }
                )
            }

            HorizontalDivider()

            Text(stringResource(R.string.config_filtro_video), fontWeight = FontWeight.Bold)
            val filtros = listOf(
                R.string.filtro_nenhum,
                R.string.filtro_bilinear,
                R.string.filtro_crt
            )
            filtros.forEachIndexed { indice, rotulo ->
                LinhaDeOpcao(
                    rotulo = rotulo,
                    selecionado = filtroVideo == indice,
                    onSelecionar = { audio.playClick(); prefs.definirFiltroVideo(indice) }
                )
            }

            Text(
                stringResource(R.string.config_aviso_video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --------------------------------------------------- manutencao
        TituloDeSecao(R.string.sec_manutencao)
        Cartao {
            Text(stringResource(R.string.config_hashes), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.config_hashes_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // O calculo roda no WorkManager, entao continua depois de fechar
            // o app e pode ser cancelado sem perder o que ja foi feito.
            val progressoHashes by remember { Trabalhos.progresso(context, Trabalhos.HASHES) }
                .collectAsStateWithLifecycle(initialValue = null)
            val andamento = progressoHashes

            if (andamento != null) {
                LinearProgressIndicator(
                    progress = { andamento.fracao },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.progresso_contagem,
                            andamento.feitos,
                            andamento.total
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = {
                        audio.playClick()
                        Trabalhos.cancelar(context, Trabalhos.HASHES)
                    }) { Text(stringResource(R.string.acao_cancelar)) }
                }
            } else {
                Button(
                    onClick = { audio.playClick(); Trabalhos.enfileirarHashes(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.acao_calcular)) }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================
// TELA: SOBRE
// =====================================================================
@Composable
fun TelaSobre() {
    val audio = LocalAudio.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Default.Gamepad,
            null,
            modifier = Modifier.size(110.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.sobre_versao, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.sobre_descricao),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.sobre_desenvolvido_por),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.sobre_autor),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // ------------------------------------------------------- apoio
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.sobre_doacao_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Button(
                    onClick = { audio.playClick(); uriHandler.openUri(URL_KOFI) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolunteerActivism, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.acao_doar))
                }
            }
        }

        OutlinedButton(
            onClick = { audio.playClick(); uriHandler.openUri(URL_REPO) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Code, null, Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.acao_repositorio))
        }

        Text(
            stringResource(R.string.sobre_licenca_desc),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================
// TELA: CONFIGURACOES POR JOGO
// =====================================================================
@Composable
fun TelaConfiguracoesJogo(jogo: Jogo?) {
    if (jogo == null) return
    val audio = LocalAudio.current

    var overrideVideo by remember(jogo.id) { mutableStateOf(false) }
    var overrideControles by remember(jogo.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Gamepad,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.jogo_config_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        jogo.nome,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        TituloDeSecao(R.string.sec_overrides)

        Cartao {
            LinhaDeInterruptor(
                rotulo = R.string.override_video,
                descricao = R.string.override_video_desc,
                marcado = overrideVideo,
                onMudar = { audio.playClick(); overrideVideo = it }
            )
            if (overrideVideo) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.override_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Cartao {
            LinhaDeInterruptor(
                rotulo = R.string.override_controles,
                descricao = R.string.override_controles_desc,
                marcado = overrideControles,
                onMudar = { audio.playClick(); overrideControles = it }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================
// TELA: RETROACHIEVEMENTS (ainda com dados de exemplo)
// =====================================================================
val mockStats = listOf(
    RetroGameStat("Super Mario Bros.", Sistema.NES, 12, 24),
    RetroGameStat("Castlevania", Sistema.NES, 5, 18),
    RetroGameStat("Super Mario World", Sistema.SNES, 45, 96),
    RetroGameStat("Chrono Trigger", Sistema.SNES, 10, 50),
    RetroGameStat("Donkey Kong Country", Sistema.SNES, 28, 28)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaRetroAchievements() {
    val context = LocalContext.current
    val audio = LocalAudio.current
    val prefsRa = remember {
        context.getSharedPreferences("RetroAchievementsPrefs", Context.MODE_PRIVATE)
    }

    var usuario by remember { mutableStateOf(prefsRa.getString("username", "") ?: "") }
    var chave by remember { mutableStateOf("") }
    var chaveVisivel by remember { mutableStateOf(false) }
    var logado by remember { mutableStateOf(prefsRa.getBoolean("isLogged", false)) }

    if (!logado) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        Icons.Default.EmojiEvents,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.ra_vincular),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = { Text(stringResource(R.string.ra_usuario)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chave,
                        onValueChange = { chave = it },
                        label = { Text(stringResource(R.string.ra_api_key)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        singleLine = true,
                        visualTransformation = if (chaveVisivel) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { chaveVisivel = !chaveVisivel }) {
                                Icon(
                                    if (chaveVisivel) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff,
                                    stringResource(
                                        if (chaveVisivel) R.string.acao_ocultar_senha
                                        else R.string.acao_mostrar_senha
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            audio.playClick()
                            if (usuario.isNotBlank() && chave.isNotBlank()) {
                                // Fase 5: isto some. As credenciais vao para o
                                // DataStore protegido pelo Keystore e a
                                // validacao real acontece via rcheevos.
                                prefsRa.edit()
                                    .putString("username", usuario)
                                    .putBoolean("isLogged", true)
                                    .apply()
                                logado = true
                                chave = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.ra_entrar)) }

                    Text(
                        stringResource(R.string.ra_aviso_mock),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val sistemas = remember { Sistema.entries }
    val pagerState = rememberPagerState(pageCount = { sistemas.size })
    val escopo = rememberCoroutineScope()
    val porSistema = remember { mockStats.groupBy { it.sistema } }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountCircle,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(usuario, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.ra_online),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = {
                audio.playClick()
                logado = false
                prefsRa.edit().putBoolean("isLogged", false).apply()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    stringResource(R.string.ra_sair),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            sistemas.forEachIndexed { indice, sistema ->
                Tab(
                    selected = pagerState.currentPage == indice,
                    onClick = {
                        audio.playSwipe()
                        escopo.launch { pagerState.animateScrollToPage(indice) }
                    },
                    text = { Text(stringResource(sistema.rotulo)) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { sistemas[it].name }
        ) { pagina ->
            val stats = porSistema[sistemas[pagina]].orEmpty()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        stringResource(R.string.ra_progresso),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(items = stats, key = { it.nome }) { stat ->
                    CartaoDeConquista(stat)
                }
                item {
                    Text(
                        stringResource(R.string.ra_aviso_mock),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CartaoDeConquista(stat: RetroGameStat) {
    val fracao = if (stat.totalConquistas > 0) {
        stat.conquistasDesbloqueadas.toFloat() / stat.totalConquistas
    } else {
        0f
    }
    val percentual = (fracao * 100).toInt()
    val completo = percentual >= 100

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stat.nome,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (completo) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        stringResource(R.string.ra_platinado),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(
                        R.string.ra_conquistas,
                        stat.conquistasDesbloqueadas,
                        stat.totalConquistas
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "$percentual%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fracao },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (completo) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
