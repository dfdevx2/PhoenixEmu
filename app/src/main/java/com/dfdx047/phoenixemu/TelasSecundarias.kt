package com.dfdx047.phoenixemu

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfdx047.phoenixemu.Acabamento
import com.dfdx047.phoenixemu.data.BibliotecaStore
import com.dfdx047.phoenixemu.data.Idioma
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.ui.theme.esquemaDeAmostra
import com.dfdx047.phoenixemu.ui.design.CartaoDeVidro
import com.dfdx047.phoenixemu.ui.design.EspacoDaNavegacao
import com.dfdx047.phoenixemu.ui.design.SeletorSegmentado
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

@Composable
private fun RotuloComDica(@StringRes rotulo: Int, @StringRes dica: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(rotulo), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
    val reduzirEfeitos by prefs.reduzirEfeitos.collectAsStateWithLifecycle()
    val amoled by prefs.amoled.collectAsStateWithLifecycle()
    val acabamento by prefs.acabamento.collectAsStateWithLifecycle()
    val wallDesfoque by prefs.wallpaperDesfoque.collectAsStateWithLifecycle()
    val wallOpacidade by prefs.wallpaperOpacidade.collectAsStateWithLifecycle()
    var mostrarSeletorDeTema by remember { mutableStateOf(false) }
    var desfoqueArrastado by remember(wallDesfoque) { mutableFloatStateOf(wallDesfoque) }
    var opacidadeArrastada by remember(wallOpacidade) { mutableFloatStateOf(wallOpacidade) }
    val proporcao by prefs.proporcaoTela.collectAsStateWithLifecycle()
    val filtroVideo by prefs.filtroVideo.collectAsStateWithLifecycle()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Folga para o titulo flutuante que vive na camada de vidro.
        Spacer(Modifier.height(88.dp))

        // ------------------------------------------------ personalizacao
        TituloDeSecao(R.string.sec_personalizacao)
        Cartao {
            // A lista de radio buttons virou um botao com amostra: o nome do
            // tema sozinho nao diz nada sobre o que voce vai ver.
            Text(stringResource(R.string.config_tema), fontWeight = FontWeight.Bold)
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
                marcado = amoled,
                onMudar = { audio.playClick(); prefs.definirAmoled(it) }
            )

            HorizontalDivider()

            Text(stringResource(R.string.config_acabamento), fontWeight = FontWeight.Bold)
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

            // Os dois sliders so aparecem com wallpaper escolhido: sem imagem
            // eles nao teriam o que ajustar.
            if (wallpaperUri != null) {
                Text(
                    stringResource(R.string.config_wallpaper_desfoque),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = desfoqueArrastado,
                    onValueChange = { desfoqueArrastado = it },
                    onValueChangeFinished = { prefs.definirWallpaperDesfoque(desfoqueArrastado) },
                    valueRange = 0f..1f
                )
                Text(
                    stringResource(R.string.config_wallpaper_opacidade),
                    style = MaterialTheme.typography.bodyMedium
                )
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

            LinhaDeInterruptor(
                rotulo = R.string.config_reduzir_efeitos,
                descricao = R.string.config_reduzir_efeitos_desc,
                marcado = reduzirEfeitos,
                onMudar = { audio.playClick(); prefs.definirReduzirEfeitos(it) }
            )
        }
            
        // -------------------------------------------------------- navegacao
        TituloDeSecao(R.string.sec_navegacao)
        Cartao {
            val ombrosTrocamSecao by prefs.ombrosTrocamSecao.collectAsStateWithLifecycle()
            LinhaDeInterruptor(
                rotulo = R.string.config_ombros_secao,
                descricao = R.string.config_ombros_secao_desc,
                marcado = ombrosTrocamSecao,
                onMudar = { audio.playClick(); prefs.definirOmbrosTrocamSecao(it) }
            )
        }

        Cartao {
            // O idioma tambem e perguntado na primeira execucao, mas ninguem
            // decide isso bem numa tela que ainda pode estar no idioma errado.
            Text(stringResource(R.string.config_idioma), fontWeight = FontWeight.Bold)
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
                    // Recursos sao resolvidos em attachBaseContext; so nascer de
                    // novo troca a tabela de strings.
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
        
        // ----------------------------------------------------------- jogo
        TituloDeSecao(R.string.sec_jogo)
        Cartao {
            val autoSalvar by prefs.autoSalvar.collectAsStateWithLifecycle()
            val autoCarregar by prefs.autoCarregar.collectAsStateWithLifecycle()
            
            LinhaDeInterruptor(
                rotulo = R.string.config_autosalvar,
                descricao = R.string.config_autosalvar_desc,
                marcado = autoSalvar,
                onMudar = { audio.playClick(); prefs.definirAutoSalvar(it) }
            )
            HorizontalDivider()
            LinhaDeInterruptor(
                rotulo = R.string.config_autocarregar,
                descricao = R.string.config_autocarregar_desc,
                marcado = autoCarregar,
                onMudar = { audio.playClick(); prefs.definirAutoCarregar(it) }
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

        EspacoDaNavegacao()
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
    var sistemaIndice by rememberSaveable { mutableIntStateOf(0) }
    val porSistema = remember { mockStats.groupBy { it.sistema } }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(88.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
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

        Spacer(Modifier.height(12.dp))
        SeletorSegmentado(
            opcoes = sistemas.map { it.rotuloCurto },
            indiceSelecionado = sistemaIndice,
            onSelecionar = { audio.playSwipe(); sistemaIndice = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        run {
            val stats = porSistema[sistemas[sistemaIndice]].orEmpty()
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
