package com.dfdx047.phoenixemu.ui.telas

import kotlinx.coroutines.launch
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dfdx047.phoenixemu.LocalAudio
import com.dfdx047.phoenixemu.R
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.OverlayConfigNova
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.nomeDaTecla
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.ConfigDeAtalhos
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.ui.design.CartaoDeVidro
import com.dfdx047.phoenixemu.ui.design.EspacoDaNavegacao
import com.dfdx047.phoenixemu.ui.design.SeletorSegmentado
import kotlin.math.roundToInt

/**
 * Configuracao de controles.
 *
 * Duas metades que resolvem problemas diferentes: quem joga de gamepad
 * precisa remapear botoes, quem joga no toque precisa posicionar o controle
 * na tela. Ficam na mesma aba porque, do ponto de vista de quem usa, e a
 * mesma pergunta -- "como eu jogo".
 */
@Composable
fun TelaControles(prefs: Preferencias) {
    var aba by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(88.dp))

        SeletorSegmentado(
            opcoes = listOf(
                stringResource(R.string.controles_fisico),
                stringResource(R.string.controles_tela),
                stringResource(R.string.controles_atalhos)
            ),
            indiceSelecionado = aba,
            onSelecionar = { aba = it }
        )

        if (aba == 0) MapeamentoFisico(prefs) else if (aba == 1) EditorDoOverlay(prefs) else AtalhosDeJogo(prefs)

        EspacoDaNavegacao()
    }
}

// =====================================================================
// CONTROLE FISICO
// =====================================================================

@Composable
private fun MapeamentoFisico(prefs: Preferencias) {
    val audio = LocalAudio.current
    val mapa by prefs.mapeamento.collectAsStateWithLifecycle()
    var capturando by remember { mutableStateOf<BotaoVirtual?>(null) }

    CartaoDeVidro {
        Text(stringResource(R.string.controles_fisico_desc), style = MaterialTheme.typography.bodySmall)

        BotaoVirtual.entries.forEach { botao ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { audio.playClick(); capturando = botao }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(botao.rotulo), modifier = Modifier.weight(1f))
                Text(
                    nomeDaTecla(mapa[botao] ?: botao.padrao),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
        }

        OutlinedButton(
            onClick = { audio.playClick(); prefs.restaurarMapeamento() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.acao_restaurar_padrao)) }
    }

    val alvo = capturando
    if (alvo != null) {
        DialogoDeCaptura(
            titulo = stringResource(alvo.rotulo),
            onCapturar = { codigo ->
                prefs.definirTecla(alvo, codigo)
                capturando = null
            },
            onCancelar = { capturando = null }
        )
    }
}

@Composable
fun AtalhosDeJogo(prefs: Preferencias) {
    val atalhosCfg by prefs.atalhos.collectAsStateWithLifecycle()
    var editandoHotkey by remember { mutableStateOf(false) }
    var editandoAcao by remember { mutableStateOf<AcaoAtalho?>(null) }
    val audio = LocalAudio.current

    if (editandoHotkey) {
        DialogoDeCaptura(
            titulo = stringResource(R.string.atalhos_botao_hotkey),
            onCapturar = {
                prefs.definirHotkey(it)
                editandoHotkey = false
            },
            onCancelar = { editandoHotkey = false },
            onLimpar = {
                prefs.definirHotkey(0)
                editandoHotkey = false
            }
        )
    }
    
    if (editandoAcao != null) {
        DialogoDeCaptura(
            titulo = stringResource(editandoAcao!!.rotulo),
            onCapturar = {
                prefs.definirAtalho(editandoAcao!!, it)
                editandoAcao = null
            },
            onCancelar = { editandoAcao = null },
            onLimpar = {
                prefs.definirAtalho(editandoAcao!!, 0)
                editandoAcao = null
            }
        )
    }

    CartaoDeVidro {
        Column {
            Text(
                text = stringResource(R.string.atalhos_hotkey_descricao),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audio.playClick(); editandoHotkey = true }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.atalhos_botao_hotkey), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (atalhosCfg.hotkey != 0) nomeDaTecla(atalhosCfg.hotkey) else stringResource(R.string.atalhos_nenhum),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            AcaoAtalho.entries.forEachIndexed { i, acao ->
                val atalho = atalhosCfg.acoes[acao.name]
                val comboLista = atalhosCfg.combo(acao)
                val textoCombo = if (comboLista.isEmpty()) {
                    stringResource(R.string.atalhos_nenhum)
                } else {
                    comboLista.joinToString(" + ") { nomeDaTecla(it) }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { audio.playClick(); editandoAcao = acao }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(acao.rotulo), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = textoCombo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (atalhosCfg.hotkey != 0 && (atalho?.tecla ?: 0) != 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.atalhos_usar_hotkey),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = atalho?.usarHotkey ?: true,
                                onCheckedChange = {
                                    audio.playClick()
                                    prefs.definirUsarHotkey(acao, it)
                                }
                            )
                        }
                    }
                }
                
                if (i < AcaoAtalho.entries.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        }
    }
    
    Text(
        text = stringResource(R.string.atalhos_aviso),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
    
    OutlinedButton(
        onClick = { audio.playClick(); prefs.restaurarAtalhos() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.acao_restaurar_padrao))
    }
}

/**
 * Captura a proxima tecla do gamepad.
 *
 * Um modificador `onKeyEvent` nao serve aqui: teclas como L1/R1 e Start nem
 * sempre chegam ao Compose, porque o sistema as consome antes. Um
 * `OnKeyListener` na view raiz pega todas.
 */
@Composable
private fun DialogoDeCaptura(
    titulo: String,
    onCapturar: (Int) -> Unit,
    onCancelar: () -> Unit,
    onLimpar: (() -> Unit)? = null
) {
    val audio = LocalAudio.current
    AlertDialog(
        onDismissRequest = onCancelar,
        shape = RoundedCornerShape(28.dp),
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = {
            // A escuta fica DENTRO do slot de propósito. Aqui `LocalView` e a
            // view do dialogo; do lado de fora seria a da Activity -- e
            // enquanto o dialogo esta aberto a Activity nao tem o foco, entao
            // nenhuma tecla chegaria ali.
            EscutaDeTeclas(titulo, onCapturar)
            Column {
                Text(
                    stringResource(
                        R.string.controles_pressione,
                        titulo
                    )
                )
                if (onLimpar != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        audio.playClick()
                        onLimpar()
                    }) {
                        Text(stringResource(R.string.atalhos_limpar))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.acao_cancelar)) }
        }
    )
}

@Composable
private fun EscutaDeTeclas(chave: Any, onCapturar: (Int) -> Unit) {
    val vista = LocalView.current
    DisposableEffect(chave) {
        val raiz = vista.rootView
        val focavelAntes = raiz.isFocusableInTouchMode
        raiz.isFocusableInTouchMode = true
        raiz.requestFocus()
        raiz.setOnKeyListener { _, codigo, evento ->
            // Voltar tem de continuar fechando o dialogo, e capturar volume
            // deixaria a pessoa sem como ajustar o som.
            val reservada = codigo == KeyEvent.KEYCODE_BACK ||
                codigo == KeyEvent.KEYCODE_VOLUME_UP ||
                codigo == KeyEvent.KEYCODE_VOLUME_DOWN
            if (evento.action == KeyEvent.ACTION_DOWN && !reservada) {
                onCapturar(codigo)
                true
            } else {
                false
            }
        }
        onDispose {
            raiz.setOnKeyListener(null)
            raiz.isFocusableInTouchMode = focavelAntes
        }
    }
}

// =====================================================================
// CONTROLE NA TELA
// =====================================================================

@Composable
private fun EditorDoOverlay(prefs: Preferencias) {
    val audio = LocalAudio.current
    val config by prefs.overlay.collectAsStateWithLifecycle()
    var editandoLayout by remember { mutableStateOf(false) }
    val ctxSkins = androidx.compose.ui.platform.LocalContext.current
    remember(ctxSkins) { com.dfdx047.phoenixemu.emulator.Skins.garantir(ctxSkins); 0 }
    var skinIndice by remember { mutableIntStateOf(0) }

    LaunchedEffect(config.skinId) {
        val idx = com.dfdx047.phoenixemu.emulator.Skins.todas.indexOfFirst { it.id == config.skinId }
        if (idx >= 0) skinIndice = idx
    }

    if (editandoLayout) {
        Dialog(
            onDismissRequest = { editandoLayout = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                com.dfdx047.phoenixemu.emulator.EditorDeOverlayUI(
                    configInicial = config,
                    corPrimaria = MaterialTheme.colorScheme.primary,
                    corAcento = MaterialTheme.colorScheme.secondary,
                    fundoOpaco = true,
                    onSave = {
                        audio.playClick()
                        prefs.definirOverlay(it)
                        editandoLayout = false
                    },
                    onCancel = {
                        audio.playClick()
                        editandoLayout = false
                    }
                )
            }
        }
    }

    CartaoDeVidro {
        OutlinedButton(
            onClick = { audio.playClick(); editandoLayout = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.overlay_jogo_editar_layout)) }

        val modosVis = listOf(com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.SEMPRE, com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.AUTO_ESCONDER_COM_CONTROLE, com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.NUNCA)
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.editor_overlay_visibilidade), fontWeight = FontWeight.Bold)
            com.dfdx047.phoenixemu.ui.design.BotaoDeDica(stringResource(R.string.editor_overlay_visibilidade_dica))
        }
        SeletorSegmentado(
            opcoes = listOf(
                stringResource(R.string.editor_overlay_vis_sempre),
                stringResource(R.string.editor_overlay_vis_auto),
                stringResource(R.string.editor_overlay_vis_nunca)
            ),
            indiceSelecionado = modosVis.indexOf(config.visivelModo).coerceAtLeast(0),
            onSelecionar = { prefs.definirOverlay(config.copy(visivelModo = modosVis[it])) }
        )

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.overlay_skin), fontWeight = FontWeight.Bold)
            com.dfdx047.phoenixemu.ui.design.BotaoDeDica(stringResource(R.string.skin_importar_dica))
        }
        SeletorSegmentado(
            opcoes = com.dfdx047.phoenixemu.emulator.Skins.todas.map { it.nome },
            indiceSelecionado = skinIndice,
            onSelecionar = {
                skinIndice = it
                prefs.definirOverlay(config.copy(skinId = com.dfdx047.phoenixemu.emulator.Skins.todas[it].id))
            }
        )

        run {
            val escopoSkins = androidx.compose.runtime.rememberCoroutineScope()
            var msgSkin by remember { mutableStateOf<String?>(null) }
            val seletorZip = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) escopoSkins.launch {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            ctxSkins.contentResolver.openInputStream(uri)?.use {
                                com.dfdx047.phoenixemu.emulator.CarregadorDeSkins.instalarZip(ctxSkins, it)
                            }
                        }.getOrNull()
                    }
                    msgSkin = when (r) {
                        is com.dfdx047.phoenixemu.emulator.ResultadoSkin.Ok -> {
                            com.dfdx047.phoenixemu.emulator.Skins.recarregar(ctxSkins)
                            prefs.definirOverlay(config.copy(skinId = r.skin.id))
                            ctxSkins.getString(R.string.skin_importada, r.skin.nome)
                        }
                        is com.dfdx047.phoenixemu.emulator.ResultadoSkin.Erro -> ctxSkins.getString(
                            when (r.codigo) {
                                "arquivo_proibido" -> R.string.skin_erro_arquivo_proibido
                                "grande" -> R.string.skin_erro_grande
                                "sem_manifesto" -> R.string.skin_erro_sem_manifesto
                                "manifesto_invalido" -> R.string.skin_erro_manifesto_invalido
                                "imagem_invalida" -> R.string.skin_erro_imagem_invalida
                                "id_reservado" -> R.string.skin_erro_id_reservado
                                else -> R.string.skin_erro_zip_invalido
                            }
                        )
                        null -> ctxSkins.getString(R.string.skin_erro_zip_invalido)
                    }
                }
            }
            val skinAtual = com.dfdx047.phoenixemu.emulator.Skins.todas.getOrNull(skinIndice)
            val ehInstalada = skinAtual != null &&
                com.dfdx047.phoenixemu.emulator.Skins.embutidas.none { it.id == skinAtual.id }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
            ) {
                androidx.compose.material3.Button(onClick = { seletorZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Text(stringResource(R.string.skin_importar))
                }
                if (ehInstalada && skinAtual != null) {
                    androidx.compose.material3.Button(onClick = {
                        com.dfdx047.phoenixemu.emulator.CarregadorDeSkins.remover(ctxSkins, skinAtual.id)
                        com.dfdx047.phoenixemu.emulator.Skins.recarregar(ctxSkins)
                        prefs.definirOverlay(config.copy(skinId = com.dfdx047.phoenixemu.emulator.SkinClassica16Bit.id))
                        msgSkin = null
                    }) { Text(stringResource(R.string.skin_remover)) }
                }
            }
            msgSkin?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        HorizontalDivider()
        
        BotaoExportarImportarLayout(config, onImportar = { prefs.definirOverlay(it) })
        
        HorizontalDivider()

        Text(stringResource(R.string.editor_overlay_opacidade_ociosa_valor, (config.opacidadeOciosa * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = config.opacidadeOciosa,
            onValueChange = { prefs.definirOverlay(config.copy(opacidadeOciosa = it)) },
            valueRange = 0.0f..1f
        )
        
        Text(stringResource(R.string.editor_overlay_opacidade_pressionado_valor, (config.opacidadePressionado * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = config.opacidadePressionado,
            onValueChange = { prefs.definirOverlay(config.copy(opacidadePressionado = it)) },
            valueRange = 0.0f..1f
        )

        Text(stringResource(R.string.editor_overlay_tamanho_global_valor, (config.escalaGlobal * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = config.escalaGlobal,
            onValueChange = { prefs.definirOverlay(config.copy(escalaGlobal = it)) },
            valueRange = 0.5f..2.0f
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.editor_overlay_ocultar_no_menu))
            Switch(
                checked = config.ocultarNoMenu,
                onCheckedChange = { prefs.definirOverlay(config.copy(ocultarNoMenu = it)) }
            )
        }

        OutlinedButton(
            onClick = { audio.playClick(); prefs.restaurarOverlay() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.acao_restaurar_padrao)) }
    }
}

