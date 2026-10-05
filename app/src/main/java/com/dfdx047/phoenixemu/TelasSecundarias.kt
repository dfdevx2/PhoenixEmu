package com.dfdx047.phoenixemu

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dfdx047.phoenixemu.Acabamento
import com.dfdx047.phoenixemu.data.BibliotecaStore
import com.dfdx047.phoenixemu.data.Idioma
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.ui.theme.esquemaDeAmostra
import com.dfdx047.phoenixemu.ui.design.CartaoDeVidro
import com.dfdx047.phoenixemu.ui.design.EspacoDaNavegacao
import com.dfdx047.phoenixemu.ui.design.SeletorSegmentado
import com.dfdx047.phoenixemu.ui.design.SeletorSegmentadoNES
import com.dfdx047.phoenixemu.ui.design.BotaoDeDica
import com.dfdx047.phoenixemu.data.Trabalhos
import com.dfdx047.phoenixemu.emulacao.Emulador
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

/**
 * As telas secundarias usam o MESMO material das pilulas, sem desfoque ao
 * vivo: elas rolam, e pagar blur por cartao numa lista que rola e o caminho
 * mais curto para perder os 60 fps.
 */
@Composable
private fun Cartao(conteudo: @Composable ColumnScope.() -> Unit) {
    CartaoDeVidro(conteudo = conteudo)
}

@Composable
private fun LinhaDeOpcao(
    @StringRes rotulo: Int,
    @StringRes dica: Int? = null,
    selecionado: Boolean,
    onSelecionar: () -> Unit
) {
    LinhaDeOpcaoString(stringResource(rotulo), dica, selecionado, onSelecionar)
}

@Composable
private fun LinhaDeOpcaoString(
    rotulo: String,
    @StringRes dica: Int? = null,
    selecionado: Boolean,
    onSelecionar: () -> Unit
) {
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
        Text(rotulo)
        if (dica != null) {
            Spacer(Modifier.weight(1f))
            BotaoDeDica(stringResource(dica))
        }
    }
}

@Composable
private fun LinhaDeInterruptor(
    @StringRes rotulo: Int,
    @StringRes descricao: Int? = null,
    @StringRes dica: Int? = null,
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
        if (dica != null) {
            Spacer(Modifier.width(8.dp))
            BotaoDeDica(stringResource(dica))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = marcado, onCheckedChange = onMudar)
    }
}


/** Versão de LinhaDeInterruptor com suporte a desabilitado por uma chave-mae. */
@Composable
private fun LinhaDeInterruptorIndividuial(
    @StringRes rotulo: Int,
    @StringRes descricao: Int? = null,
    @StringRes dica: Int? = null,
    marcado: Boolean,
    desabilitadoPor: Boolean,
    onMudar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(rotulo),
                fontWeight = FontWeight.Bold,
                color = if (desabilitadoPor) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
            )
            if (descricao != null) {
                Text(
                    stringResource(descricao),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (desabilitadoPor) 0.5f else 1f)
                )
            }
        }
        if (dica != null) {
            Spacer(Modifier.width(8.dp))
            BotaoDeDica(stringResource(dica))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = marcado,
            enabled = !desabilitadoPor,
            onCheckedChange = onMudar
        )
    }
}
@Composable
private fun RotuloComDica(@StringRes rotulo: Int, @StringRes dica: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(rotulo), fontWeight = FontWeight.Bold)
        BotaoDeDica(stringResource(dica))
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
    val somCapaPassa by prefs.somCapaPassa.collectAsStateWithLifecycle()
    val somTrocaAba by prefs.somTrocaAba.collectAsStateWithLifecycle()
    val somBoot by prefs.somBoot.collectAsStateWithLifecycle()
    val somEntrarJogo by prefs.somEntrarJogo.collectAsStateWithLifecycle()
    val reduzirEfeitos by prefs.reduzirEfeitos.collectAsStateWithLifecycle()
    val amoled by prefs.amoled.collectAsStateWithLifecycle()
    val acabamento by prefs.acabamento.collectAsStateWithLifecycle()
    val wallDesfoque by prefs.wallpaperDesfoque.collectAsStateWithLifecycle()
    val wallOpacidade by prefs.wallpaperOpacidade.collectAsStateWithLifecycle()
    var mostrarSeletorDeTema by remember { mutableStateOf(false) }
    var desfoqueArrastado by remember(wallDesfoque) { mutableFloatStateOf(wallDesfoque) }
    var opacidadeArrastada by remember(wallOpacidade) { mutableFloatStateOf(wallOpacidade) }
    val globalEscala by prefs.globalEscalaImagem.collectAsStateWithLifecycle()
    val globalProporcao by prefs.globalProporcaoImagem.collectAsStateWithLifecycle()
    val globalFiltro by prefs.globalFiltroImagem.collectAsStateWithLifecycle()
    val globalMostrarFps by prefs.globalMostrarFps.collectAsStateWithLifecycle()
    
    val globalVolume by prefs.globalVolume.collectAsStateWithLifecycle()
    val globalMudo by prefs.globalMudo.collectAsStateWithLifecycle()
    var volumeArrastado by remember(globalVolume) { mutableFloatStateOf(globalVolume) }
    
    val globalVelocidadeFF by prefs.globalVelocidadeFF.collectAsStateWithLifecycle()
    val globalMenuEstilo by prefs.globalMenuEstilo.collectAsStateWithLifecycle()
    val globalMenuDesfoque by prefs.globalMenuDesfoque.collectAsStateWithLifecycle()
    val globalMenuOpacidade by prefs.globalMenuOpacidade.collectAsStateWithLifecycle()
    var opacidadeArrastadaMenu by remember(globalMenuOpacidade) { mutableFloatStateOf(globalMenuOpacidade) }
    var desfoqueArrastadoMenu by remember(globalMenuDesfoque) { mutableFloatStateOf(globalMenuDesfoque) }
    val globalMenuLado by prefs.globalMenuLado.collectAsStateWithLifecycle()
    val globalMenuTema by prefs.globalMenuTema.collectAsStateWithLifecycle()
    val globalMenuAlca by prefs.globalMenuAlca.collectAsStateWithLifecycle()
    val globalMenuVoltar by prefs.globalMenuVoltar.collectAsStateWithLifecycle()
    val globalMenuGesto by prefs.globalMenuGesto.collectAsStateWithLifecycle()

    val wallpaperUri by prefs.wallpaperUri.collectAsStateWithLifecycle()
    // Nao vem do DataStore: veja Idioma.kt. Ler uma vez basta, porque trocar
    // de idioma recria a Activity.
    val idioma = remember { Idioma.atual(context) }

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

    var abaSelecionada by rememberSaveable { mutableIntStateOf(0) }
    val abasStr = listOf(
        stringResource(R.string.aba_aparencia),
        stringResource(R.string.aba_audio),
        stringResource(R.string.aba_video),
        stringResource(R.string.aba_jogo),
        stringResource(R.string.aba_navegacao),
        stringResource(R.string.aba_manutencao)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(88.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            SeletorSegmentado(
                opcoes = abasStr,
                indiceSelecionado = abaSelecionada,
                onSelecionar = { audio.playSwipe(); abaSelecionada = it }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (abaSelecionada == 0) {
                Cartao {
                    RotuloComDica(R.string.config_tema, R.string.dica_tema)
                    OutlinedButton(
                        onClick = { audio.playClick(); mostrarSeletorDeTema = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AmostraDeTema(temaAtual)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(temaAtual.rotulo), modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.acao_escolher_tema),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinhaDeInterruptor(
                        rotulo = R.string.config_amoled,
                        descricao = R.string.config_amoled_desc,
                        dica = R.string.dica_amoled,
                        marcado = amoled,
                        onMudar = { audio.playClick(); prefs.definirAmoled(it) }
                    )

                    HorizontalDivider()

                    RotuloComDica(R.string.config_acabamento, R.string.dica_acabamento)
                    Text(
                        stringResource(R.string.config_acabamento_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SeletorSegmentado(
                        opcoes = Acabamento.entries.map { stringResource(it.rotulo) },
                        indiceSelecionado = Acabamento.entries.indexOf(acabamento),
                        onSelecionar = {
                            audio.playClick()
                            prefs.definirAcabamento(Acabamento.entries[it])
                        }
                    )

                    HorizontalDivider()

                    RotuloComDica(R.string.config_fundo, R.string.dica_fundo)
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

                    if (wallpaperUri != null) {
                        RotuloComDica(R.string.config_wallpaper_desfoque, R.string.dica_desfoque)
                        Slider(
                            value = desfoqueArrastado,
                            onValueChange = { desfoqueArrastado = it },
                            onValueChangeFinished = { prefs.definirWallpaperDesfoque(desfoqueArrastado) },
                            valueRange = 0f..1f
                        )
                        RotuloComDica(R.string.config_wallpaper_opacidade, R.string.dica_opacidade)
                        Slider(
                            value = opacidadeArrastada,
                            onValueChange = { opacidadeArrastada = it },
                            onValueChangeFinished = { prefs.definirWallpaperOpacidade(opacidadeArrastada) },
                            valueRange = 0.15f..1f
                        )
                        Text(
                            stringResource(R.string.config_wallpaper_aviso),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    // Menu do jogo (overlay) — segue o acabamento global
                    RotuloComDica(R.string.menu_overlay_titulo, R.string.menu_overlay_dica)

                    HorizontalDivider()

                    // Desfoque e Opacidade — SEMPRE visíveis (independente do acabamento)
                    RotuloComDica(R.string.ajustes_titulo_menu_desfoque, R.string.ajustes_dica_menu_desfoque)
                    Slider(
                        value = desfoqueArrastadoMenu,
                        onValueChange = { desfoqueArrastadoMenu = it },
                        onValueChangeFinished = { prefs.setAjusteFloat("aj_global_menu_desfoque", desfoqueArrastadoMenu) },
                        valueRange = 0f..40f
                    )
                    if (reduzirEfeitos) {
                        Text(
                            stringResource(R.string.menu_desfoque_reduzindo_aviso),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RotuloComDica(R.string.ajustes_titulo_menu_opacidade, R.string.ajustes_dica_menu_opacidade)
                    Slider(
                        value = opacidadeArrastadaMenu,
                        onValueChange = { opacidadeArrastadaMenu = it },
                        onValueChangeFinished = { prefs.setAjusteFloat("aj_global_menu_opacidade", opacidadeArrastadaMenu) },
                        valueRange = 0.30f..1f
                    )

                    HorizontalDivider()

                    RotuloComDica(R.string.ajustes_titulo_menu_lado, R.string.ajustes_dica_menu_lado)
                    val menuLadoOpcoes = listOf(
                        "ESQUERDA" to stringResource(R.string.ajustes_op_lado_esquerda),
                        "DIREITA" to stringResource(R.string.ajustes_op_lado_direita)
                    )
                    menuLadoOpcoes.forEach { (nome, rotulo) ->
                        LinhaDeOpcaoString(
                            rotulo = rotulo,
                            selecionado = globalMenuLado == nome,
                            onSelecionar = { prefs.setAjusteString("aj_global_menu_lado", nome) }
                        )
                    }

                    HorizontalDivider()

                    LinhaDeInterruptor(
                        rotulo = R.string.ajustes_titulo_menu_tema,
                        dica = R.string.ajustes_dica_menu_tema,
                        marcado = globalMenuTema,
                        onMudar = { prefs.setAjusteBoolean("aj_global_menu_tema", it) }
                    )

                    HorizontalDivider()

                    LinhaDeInterruptor(
                        rotulo = R.string.config_reduzir_efeitos,
                        descricao = R.string.config_reduzir_efeitos_desc,
                        dica = R.string.dica_reduzir_efeitos,
                        marcado = reduzirEfeitos,
                        onMudar = { audio.playClick(); prefs.definirReduzirEfeitos(it) }
                    )

                    HorizontalDivider()

                    val topoFixo by prefs.topoFixoNaGrade.collectAsStateWithLifecycle()
                    LinhaDeInterruptor(
                        rotulo = R.string.titulo_topo_fixo,
                        descricao = R.string.desc_topo_fixo,
                        dica = R.string.dica_topo_fixo,
                        marcado = topoFixo,
                        onMudar = { audio.playClick(); prefs.definirTopoFixoNaGrade(it) }
                    )

                    HorizontalDivider()

                    val sombras by prefs.sombras.collectAsStateWithLifecycle()
                    LinhaDeInterruptor(
                        rotulo = R.string.titulo_sombras,
                        descricao = R.string.desc_sombras,
                        dica = R.string.dica_sombras,
                        marcado = sombras,
                        onMudar = { audio.playClick(); prefs.definirSombras(it) }
                    )

                    HorizontalDivider()

                    val ocultarNav by prefs.ocultarNavAoDescer.collectAsStateWithLifecycle()
                    LinhaDeInterruptor(
                        rotulo = R.string.titulo_nav_oculta,
                        descricao = R.string.desc_nav_oculta,
                        dica = R.string.dica_nav_oculta,
                        marcado = ocultarNav,
                        onMudar = { audio.playClick(); prefs.definirOcultarNavAoDescer(it) }
                    )

                    HorizontalDivider()

                    RotuloComDica(R.string.config_idioma, R.string.dica_idioma)
                    Text(
                        stringResource(R.string.boasvindas_idioma_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SeletorSegmentado(
                        opcoes = listOf(
                            stringResource(R.string.idioma_sistema),
                            stringResource(R.string.idioma_portugues),
                            stringResource(R.string.idioma_ingles)
                        ),
                        indiceSelecionado = Idioma.disponiveis.indexOf(idioma).coerceAtLeast(0),
                        onSelecionar = { indice ->
                            audio.playClick()
                            Idioma.definir(context, Idioma.disponiveis[indice])
                            (context as? Activity)?.recreate()
                        }
                    )
                }

                if (mostrarSeletorDeTema) {
                    SeletorDeTema(
                        temaAtual = temaAtual,
                        onEscolher = { audio.playClick(); prefs.definirTema(it) },
                        onFechar = { mostrarSeletorDeTema = false }
                    )
                }
            } else if (abaSelecionada == 1) {
                Cartao {
                    Text(
                        stringResource(R.string.ajustes_aviso_sobrepor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    LinhaDeInterruptor(
                        rotulo = R.string.config_bgm,
                        dica = R.string.dica_bgm,
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
                        dica = R.string.dica_sfx,
                        marcado = sfxAtivo,
                        onMudar = { audio.playClick(); prefs.definirSfxAtivo(it) }
                    )
                    if (sfxAtivo) {
                        Slider(
                            value = sfxArrastado,
                            onValueChange = { sfxArrastado = it },
                            onValueChangeFinished = {
                                prefs.definirSfxVolume(sfxArrastado)
                                audio.playClick()
                            },
                            valueRange = 0f..1f
                        )
                    }

                    HorizontalDivider()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinhaDeInterruptorIndividuial(
                            rotulo = R.string.ajuste_som_capa_passa,
                            descricao = R.string.ajuste_som_capa_passa_desc,
                            dica = R.string.dica_ajuste_som_capa_passa,
                            marcado = somCapaPassa,
                            desabilitadoPor = !sfxAtivo,
                            onMudar = { prefs.definirSomCapaPassa(it) }
                        )
                        LinhaDeInterruptorIndividuial(
                            rotulo = R.string.ajuste_som_troca_aba,
                            descricao = R.string.ajuste_som_troca_aba_desc,
                            dica = R.string.dica_ajuste_som_troca_aba,
                            marcado = somTrocaAba,
                            desabilitadoPor = !sfxAtivo,
                            onMudar = { prefs.definirSomTrocaAba(it) }
                        )
                        LinhaDeInterruptorIndividuial(
                            rotulo = R.string.ajuste_som_boot,
                            descricao = R.string.ajuste_som_boot_desc,
                            dica = R.string.dica_ajuste_som_boot,
                            marcado = somBoot,
                            desabilitadoPor = !sfxAtivo,
                            onMudar = { prefs.definirSomBoot(it) }
                        )
                        LinhaDeInterruptorIndividuial(
                            rotulo = R.string.ajuste_som_entar_jogo,
                            descricao = R.string.ajuste_som_entar_jogo_desc,
                            dica = R.string.dica_ajuste_som_entar_jogo,
                            marcado = somEntrarJogo,
                            desabilitadoPor = !sfxAtivo,
                            onMudar = { prefs.definirSomEntrarJogo(it) }
                        )
                    }

                    HorizontalDivider()
                    
                    RotuloComDica(R.string.ajustes_titulo_volume, R.string.ajustes_dica_volume)
                    Slider(
                        value = volumeArrastado,
                        onValueChange = { volumeArrastado = it },
                        onValueChangeFinished = { prefs.setAjusteFloat("aj_global_volume", volumeArrastado) },
                        valueRange = 0f..1f
                    )
                    
                    LinhaDeInterruptor(
                        rotulo = R.string.ajustes_titulo_mudo,
                        dica = R.string.ajustes_dica_mudo,
                        marcado = globalMudo,
                        onMudar = { prefs.setAjusteBoolean("aj_global_mudo", it) }
                    )
                }
            } else if (abaSelecionada == 2) {
                Cartao {
                    Text(
                        stringResource(R.string.ajustes_aviso_sobrepor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    RotuloComDica(R.string.ajustes_titulo_escala, R.string.ajustes_dica_escala)
                    val escalasOpcoes = listOf(
                        "AJUSTAR" to stringResource(R.string.ajustes_op_escala_ajustar),
                        "X1" to stringResource(R.string.ajustes_op_escala_1x),
                        "X2" to stringResource(R.string.ajustes_op_escala_2x),
                        "X3" to stringResource(R.string.ajustes_op_escala_3x)
                    )
                    escalasOpcoes.forEach { (nome, rotulo) ->
                        LinhaDeOpcaoString(
                            rotulo = rotulo,
                            selecionado = globalEscala == nome,
                            onSelecionar = { prefs.setAjusteString("aj_global_escala", nome) }
                        )
                    }

                    HorizontalDivider()

                    RotuloComDica(R.string.ajustes_titulo_proporcao, R.string.ajustes_dica_proporcao)
                    val proporcoesOpcoes = listOf(
                        "AUTOMATICA" to stringResource(R.string.ajustes_op_proporcao_auto),
                        "PIXELS_QUADRADOS" to stringResource(R.string.ajustes_op_proporcao_pixels_quadrados),
                        "RATIO_4_3" to stringResource(R.string.ajustes_op_proporcao_4_3),
                        "RATIO_16_9" to stringResource(R.string.ajustes_op_proporcao_16_9),
                        "ESTICAR" to stringResource(R.string.ajustes_op_proporcao_esticar)
                    )
                    proporcoesOpcoes.forEach { (nome, rotulo) ->
                        LinhaDeOpcaoString(
                            rotulo = rotulo,
                            selecionado = globalProporcao == nome,
                            onSelecionar = { prefs.setAjusteString("aj_global_proporcao", nome) }
                        )
                    }

                    HorizontalDivider()

                    RotuloComDica(R.string.ajustes_titulo_filtro, R.string.ajustes_dica_filtro)
                    val filtrosOpcoes = listOf(
                        "NITIDO" to stringResource(R.string.ajustes_op_filtro_nitido),
                        "LINEAR" to stringResource(R.string.ajustes_op_filtro_linear),
                        "SCANLINES" to stringResource(R.string.ajustes_op_filtro_scanlines),
                        "CRT" to stringResource(R.string.ajustes_op_filtro_crt),
                        "VIZINHO" to stringResource(R.string.ajustes_op_filtro_vizinho)
                    )
                    filtrosOpcoes.forEach { (nome, rotulo) ->
                        LinhaDeOpcaoString(
                            rotulo = rotulo,
                            selecionado = globalFiltro == nome,
                            onSelecionar = { prefs.setAjusteString("aj_global_filtro", nome) }
                        )
                    }

                    HorizontalDivider()

                    LinhaDeInterruptor(
                        rotulo = R.string.ajustes_titulo_mostrar_fps,
                        dica = R.string.ajustes_dica_mostrar_fps,
                        marcado = globalMostrarFps,
                        onMudar = { prefs.setAjusteBoolean("aj_global_mostrarFps", it) }
                    )
                    
                    
                }
            } else if (abaSelecionada == 3) {
                Cartao {
                    Text(
                        stringResource(R.string.ajustes_aviso_sobrepor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    val autoSalvar by prefs.autoSalvar.collectAsStateWithLifecycle()
                    val autoCarregar by prefs.autoCarregar.collectAsStateWithLifecycle()
                    
                    LinhaDeInterruptor(
                        rotulo = R.string.config_autosalvar,
                        descricao = R.string.config_autosalvar_desc,
                        dica = R.string.dica_autosalvar,
                        marcado = autoSalvar,
                        onMudar = { audio.playClick(); prefs.definirAutoSalvar(it) }
                    )
                    HorizontalDivider()
                    LinhaDeInterruptor(
                        rotulo = R.string.config_autocarregar,
                        descricao = R.string.config_autocarregar_desc,
                        dica = R.string.dica_autocarregar,
                        marcado = autoCarregar,
                        onMudar = { audio.playClick(); prefs.definirAutoCarregar(it) }
                    )
                    HorizontalDivider()
                    
                    RotuloComDica(R.string.ajustes_titulo_velocidade_ff, R.string.ajustes_dica_velocidade_ff)
                    val ffOpcoes = listOf(
                        2 to stringResource(R.string.ajustes_op_ff_2x),
                        3 to stringResource(R.string.ajustes_op_ff_3x),
                        4 to stringResource(R.string.ajustes_op_ff_4x)
                    )
                    ffOpcoes.forEach { (valor, rotulo) ->
                        LinhaDeOpcaoString(
                            rotulo = rotulo,
                            selecionado = globalVelocidadeFF == valor,
                            onSelecionar = { prefs.setAjusteInt("aj_global_velocidadeFF", valor) }
                        )
                    }
                }
            } else if (abaSelecionada == 4) {
                Cartao {
                    val ombrosTrocamSecao by prefs.ombrosTrocamSecao.collectAsStateWithLifecycle()
                    LinhaDeInterruptor(
                        rotulo = R.string.config_ombros_secao,
                        descricao = R.string.config_ombros_secao_desc,
                        dica = R.string.dica_ombros,
                        marcado = ombrosTrocamSecao,
                        onMudar = { audio.playClick(); prefs.definirOmbrosTrocamSecao(it) }
                    )
                    
                    HorizontalDivider()
                    
                    LinhaDeInterruptor(
                        rotulo = R.string.ajustes_titulo_menu_alca,
                        dica = R.string.ajustes_dica_menu_alca,
                        marcado = globalMenuAlca,
                        onMudar = { prefs.setAjusteBoolean("aj_global_menu_alca", it) }
                    )
                    LinhaDeInterruptor(
                        rotulo = R.string.ajustes_titulo_menu_voltar,
                        dica = R.string.ajustes_dica_menu_voltar,
                        marcado = globalMenuVoltar,
                        onMudar = { prefs.setAjusteBoolean("aj_global_menu_voltar", it) }
                    )
                    LinhaDeInterruptor(
                        rotulo = R.string.ajustes_titulo_menu_gesto,
                        dica = R.string.ajustes_dica_menu_gesto,
                        marcado = globalMenuGesto,
                        onMudar = { prefs.setAjusteBoolean("aj_global_menu_gesto", it) }
                    )
                }
            } else if (abaSelecionada == 5) {
                var mostrarDialogoRestaurar by remember { mutableStateOf(false) }
                var mostrarDialogoApagar by remember { mutableStateOf(false) }
                val escopo = rememberCoroutineScope()

                Cartao {
                    RotuloComDica(R.string.ajustes_titulo_restaurar_globais, R.string.ajustes_desc_restaurar_globais)
                    OutlinedButton(
                        onClick = {
                            audio.playClick()
                            mostrarDialogoRestaurar = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.ajustes_botao_restaurar)) }

                    HorizontalDivider()

                    RotuloComDica(R.string.ajustes_titulo_apagar_jogos, R.string.ajustes_desc_apagar_jogos)
                    OutlinedButton(
                        onClick = {
                            audio.playClick()
                            mostrarDialogoApagar = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.ajustes_botao_apagar_jogos)) }
                    
                    HorizontalDivider()
                    
                    RotuloComDica(R.string.config_hashes, R.string.dica_hashes)
                    Text(
                        stringResource(R.string.config_hashes_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                if (mostrarDialogoRestaurar) {
                    val focusCancelarRestaurar = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusCancelarRestaurar.requestFocus() }
                    AlertDialog(
                        onDismissRequest = { mostrarDialogoRestaurar = false },
                        confirmButton = {
                            TextButton(onClick = {
                                mostrarDialogoRestaurar = false
                                escopo.launch {
                                    prefs.limparOverrides(listOf(
                                        "aj_global_escala", "aj_global_proporcao", "aj_global_mostrarFps",
                                        "aj_global_volume", "aj_global_mudo", "aj_global_velocidadeFF",
                                        "aj_global_menu_estilo", "aj_global_menu_desfoque", "aj_global_menu_opacidade",
                                        "aj_global_menu_lado", "aj_global_menu_tema", "aj_global_menu_alca",
                                        "aj_global_menu_voltar", "aj_global_menu_gesto"
                                    ))
                                }
                            }) { Text(stringResource(R.string.ajustes_botao_confirmar)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostrarDialogoRestaurar = false },
                                modifier = Modifier.focusRequester(focusCancelarRestaurar)) {
                                Text(stringResource(R.string.acao_cancelar))
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        title = { Text(stringResource(R.string.ajustes_dialog_restaurar_titulo), fontWeight = FontWeight.Bold) },
                        text = { Text(stringResource(R.string.ajustes_dialog_restaurar_texto)) }
                    )
                }

                if (mostrarDialogoApagar) {
                    val focusCancelarApagar = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusCancelarApagar.requestFocus() }
                    AlertDialog(
                        onDismissRequest = { mostrarDialogoApagar = false },
                        confirmButton = {
                            TextButton(onClick = {
                                mostrarDialogoApagar = false
                                escopo.launch { prefs.apagarTodosOsAjustesPorJogo() }
                            }) { Text(stringResource(R.string.ajustes_botao_confirmar)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostrarDialogoApagar = false },
                                modifier = Modifier.focusRequester(focusCancelarApagar)) {
                                Text(stringResource(R.string.acao_cancelar))
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        title = { Text(stringResource(R.string.ajustes_dialog_apagar_titulo), fontWeight = FontWeight.Bold) },
                        text = { Text(stringResource(R.string.ajustes_dialog_apagar_texto)) }
                    )
                }
            }
            EspacoDaNavegacao()
        }
    }
}

/** Quatro circulos com as cores reais do tema: fundo, superficie, primaria, terciaria. */
@Composable
private fun AmostraDeTema(tema: TemaApp, tamanho: androidx.compose.ui.unit.Dp = 20.dp) {
    val esquema = esquemaDeAmostra(tema)
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        listOf(esquema.primary, esquema.tertiary, esquema.surface, esquema.background)
            .forEach { cor ->
                Box(
                    Modifier
                        .size(tamanho)
                        .clip(CircleShape)
                        .background(cor)
                )
            }
    }
}

@Composable
private fun SeletorDeTema(
    temaAtual: TemaApp,
    onEscolher: (TemaApp) -> Unit,
    onFechar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onFechar,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.titulo_escolher_tema), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TemaApp.entries.forEach { opcao ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = temaAtual == opcao,
                                role = Role.RadioButton,
                                onClick = { onEscolher(opcao); onFechar() }
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = temaAtual == opcao, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(opcao.rotulo), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        AmostraDeTema(opcao, tamanho = 18.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar) { Text(stringResource(R.string.acao_fechar)) }
        }
    )
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(96.dp))
        // A silhueta monocromatica do icone, tingida com a primaria do tema:
        // muda de cor junto com o tema, sem precisar de um asset por tema.
        Icon(
            painter = painterResource(R.mipmap.ic_launcher_monochrome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp)
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

        CartaoDeVidro {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
        CartaoDeVidro {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.sobre_doacao_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
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

        // Ferramenta da Fase 4, so no build de depuracao: abre o nucleo falso
        // direto, sem ROM. Texto fixo de proposito -- nao e interface de
        // usuario, e some sozinho do APK de release.
        if (BuildConfig.DEBUG) {
            val contexto = LocalContext.current
            OutlinedButton(
                onClick = {
                    audio.playClick()
                    Emulador.abrirNucleoFalso(contexto, Preferencias.obter(contexto))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Testar núcleo falso")
            }
        }

        EspacoDaNavegacao()
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

    // Vive dentro de um ModalBottomSheet agora, nao mais como tela propria:
    // por isso fillMaxWidth e nao fillMaxSize -- a folha se dimensiona pelo
    // conteudo.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CartaoDeVidro {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Gamepad,
                    null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.jogo_config_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        jogo.nome,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        val contexto = LocalContext.current
        val biblioteca = remember(contexto) { BibliotecaStore.obter(contexto) }
        val escolherCapa = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                audio.playClick()
                // A imagem e copiada para dentro do app: a permissao da URI do
                // seletor nao sobrevive a reinicializacao, e a capa sumiria
                // sozinha dias depois.
                biblioteca.definirCapaManual(jogo.id, uri)
            }
        }

        Cartao {
            Text(stringResource(R.string.acao_trocar_capa), fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { escolherCapa.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.acao_trocar_capa))
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
// TELA: RETROACHIEVEMENTS (com jogos reais da biblioteca)
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaRetroAchievements(jogos: List<Jogo>) {
    val context = LocalContext.current
    val audio = LocalAudio.current
    val prefsRa = remember {
        context.getSharedPreferences("RetroAchievementsPrefs", Context.MODE_PRIVATE)
    }

    var usuario by remember { mutableStateOf(prefsRa.getString("username", "") ?: "") }
    var chave by remember { mutableStateOf("") }
    var chaveVisivel by remember { mutableStateOf(false) }
    var logado by remember { mutableStateOf(prefsRa.getBoolean("isLogged", false)) }

    // Jogos "abertos" = tempo > 0 ou ultimo jogado
    val jogosAbertos = remember(jogos) {
        jogos.filter { it.tempoJogadoMinutos > 0 || it.ultimaVezJogado > 0 }
            .sortedByDescending { it.ultimaVezJogado }
    }

    // Estado: jogo selecionado (destaque) — inicia no mais recente
    var jogoSelecionadoId by remember {
        mutableStateOf<String?>(jogosAbertos.firstOrNull()?.id)
    }
    val jogoDestaque = remember(jogoSelecionadoId, jogosAbertos) {
        jogosAbertos.find { it.id == jogoSelecionadoId }
    }

    // Sistema filtrado pelo seletor
    var sistemaIndice by rememberSaveable { mutableIntStateOf(0) }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    if (!logado) {
        Box(Modifier.fillMaxSize().padding(top = 88.dp, bottom = 96.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                CartaoDeVidro(modifier = Modifier.padding(28.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                            prefsRa.edit().putString("username", usuario).apply()
                            prefsRa.edit().putString("api_key", chave).apply()
                            prefsRa.edit().putBoolean("isLogged", true).apply()
                            logado = true
                            audio.playClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ra_entrar))
                    }
                    if (prefsRa.getString("username", "") != null) {
                        Text(
                            text = usuario,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            prefsRa.edit().clear().apply()
                            logado = false
                            audio.playClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.ra_sair))
                    }
                    Spacer(Modifier.height(24.dp))
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                }
            }
        }
    } else {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 88.dp, start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // a) Cabeçalho da conta
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            usuario,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "●",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        TextButton(onClick = {
                            prefsRa.edit().clear().apply()
                            logado = false
                            audio.playClick()
                        }) {
                            Text(
                                stringResource(R.string.ra_sair),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (jogosAbertos.isEmpty()) {
                // Estado vazio
                item {
                    CartaoDeVidro {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                stringResource(R.string.ra_sem_jogos),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // b) DESTAQUE — jogo selecionado
                item {
                    if (jogoDestaque != null) {
                        val total = conquistasDeExemplo(jogoDestaque).first
                        val desbloqueadas = conquistasDeExemplo(jogoDestaque).second
                        val pontos = 5 * desbloqueadas
                        val fracao = if (total > 0) desbloqueadas.toFloat() / total else 0f
                        val percentual = (fracao * 100).toInt()

                        CartaoDeVidro {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Capa
                                    Box(
                                        modifier = Modifier
                                            .height(150.dp)
                                            .width(117.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (jogoDestaque.capaLocal != null && java.io.File(jogoDestaque.capaLocal).exists()) {
                                            AsyncImage(
                                                model = java.io.File(jogoDestaque.capaLocal),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else if (!jogoDestaque.capaUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = jogoDestaque.capaUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Image,
                                                null,
                                                modifier = Modifier.size(40.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }

                                    // Info à direita
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            jogoDestaque.nome,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            modifier = Modifier.width(60.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = jogoDestaque.sistema.rotuloCurto,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                        val tempoTexto = if (jogoDestaque.tempoJogadoMinutos > 0) {
                                            stringResource(
                                                R.string.cartao_tempo_jogado,
                                                jogoDestaque.tempoJogadoMinutos
                                            )
                                        } else {
                                            stringResource(R.string.cartao_nunca_jogado)
                                        }
                                        Text(tempoTexto, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            stringResource(
                                                R.string.ra_conquistas,
                                                desbloqueadas, total
                                            ),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        LinearProgressIndicator(
                                            progress = { fracao },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = if (percentual >= 100) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text(
                                            stringResource(R.string.ra_pontos, pontos),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )

                                        Spacer(Modifier.height(12.dp))

                                        // Título "Conquistas"
                                        Text(
                                            stringResource(R.string.ra_destaque_conquistas),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        // 4 linhas de exemplo
                                        val conquistasTotal = kotlin.math.min(4, total)
                                        for (i in 1..conquistasTotal) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.WorkspacePremium,
                                                    null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (i <= desbloqueadas) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        stringResource(R.string.ra_ex_titulo, i),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (i <= desbloqueadas) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        stringResource(R.string.ra_ex_desc),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (i <= desbloqueadas) MaterialTheme.colorScheme.onSurfaceVariant
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                }
                                                Text(
                                                    stringResource(R.string.ra_pontos, 5 * i),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (i <= desbloqueadas) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            if (i < conquistasTotal) Spacer(Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // c) Jogos jogados recentemente — Row horizontal
                item {
                    Text(
                        stringResource(R.string.ra_recentes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val recentes = jogosAbertos.take(10)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        recentes.forEachIndexed { idx, jogo ->
                            CartaoRecente(jogo, onClick = {
                                audio.playClick()
                                jogoSelecionadoId = jogo.id
                                scope.launch { lazyListState.animateScrollToItem(0) }
                            })
                        }
                    }
                }

                // d) Todos os jogos — Seletor segmentado
                item {
                    Text(
                        stringResource(R.string.ra_todos_jogos),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    val sistemas = Sistema.entries.map { it.rotuloCurto }
                    SeletorSegmentadoNES(
                        opcoes = sistemas,
                        indiceSelecionado = sistemaIndice,
                        onSelecionar = { idx ->
                            audio.playSwipe()
                            sistemaIndice = idx
                        }
                    )
                }

                // e) Jogos do sistema escolhido
                item {
                    val sistemaAtual = Sistema.entries[sistemaIndice]
                    val jogosFiltrados = jogosAbertos.filter { it.sistema == sistemaAtual }
                    if (jogosFiltrados.isEmpty()) {
                        Text(
                            stringResource(R.string.ra_sem_jogos_sistema),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        jogosFiltrados.forEach { jogo ->
                            val stats = conquistasDeExemplo(jogo)
                            CartaoConquistaJogo(
                                jogo = jogo,
                                totalConquistas = stats.first,
                                desbloqueadas = stats.second,
                                onClick = {
                                    audio.playClick()
                                    jogoSelecionadoId = jogo.id
                                    scope.launch { lazyListState.animateScrollToItem(0) }
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // f) Aviso mock
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

// =====================================================================
// Funções auxiliares e composables da tela
// =====================================================================

private fun conquistasDeExemplo(jogo: Jogo): Pair<Int, Int> {
    val total = 12 + kotlin.math.abs(jogo.id.hashCode()) % 40
    val desbloqueadas = if (jogo.tempoJogadoMinutos > 0 || jogo.ultimaVezJogado > 0) {
        kotlin.math.abs(jogo.nome.hashCode()) % (total + 1)
    } else {
        0
    }
    return total to desbloqueadas
}

@Composable
private fun CartaoRecente(jogo: Jogo, onClick: () -> Unit) {
    val stats = conquistasDeExemplo(jogo)
    val fracao = if (stats.first > 0) stats.second.toFloat() / stats.first else 0f

    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(110.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (jogo.capaLocal != null && java.io.File(jogo.capaLocal).exists()) {
                AsyncImage(
                    model = java.io.File(jogo.capaLocal),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else if (!jogo.capaUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = jogo.capaUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = jogo.nome,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { fracao },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun CartaoConquistaJogo(
    jogo: Jogo,
    totalConquistas: Int,
    desbloqueadas: Int,
    onClick: () -> Unit
) {
    val fracao = if (totalConquistas > 0) desbloqueadas.toFloat() / totalConquistas else 0f
    val percentual = (fracao * 100).toInt()
    val completo = percentual >= 100

    CartaoDeVidro(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    jogo.nome,
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
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(
                        R.string.ra_conquistas,
                        desbloqueadas, totalConquistas
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

// =====================================================================
// Cartão de conquista — usado pela lista de jogos na tela RA
// =====================================================================

@Composable
private fun CartaoDeConquista(stat: RetroGameStat) {
    val fracao = if (stat.totalConquistas > 0) {
        stat.conquistasDesbloqueadas.toFloat() / stat.totalConquistas
    } else {
        0f
    }
    val percentual = (fracao * 100).toInt()
    val completo = percentual >= 100

    CartaoDeVidro {
        Column(Modifier.fillMaxWidth()) {
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
