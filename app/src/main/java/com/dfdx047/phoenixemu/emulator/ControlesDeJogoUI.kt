package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.R
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.AtalhoDaAcao
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.ConfigDeAtalhos
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.data.nomeDaTecla
import com.dfdx047.phoenixemu.data.OverlayConfigNova
import com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay
import com.google.gson.Gson
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import kotlinx.coroutines.delay

@Composable
internal fun ConteudoAbaControles(
    paleta: PaletaMenu,
    mapeamento: IntArray,
    atalhosJson: String,
    plataforma: String,
    atalhosCfg: ConfigDeAtalhos,
    capturando: String?,
    aoCapturar: (String) -> Unit,
    aoCancelarCaptura: () -> Unit,
    aoLimpar: (AcaoAtalho) -> Unit,
    aoLimparHotkey: () -> Unit,
    aoAlternarHotkey: (AcaoAtalho, Boolean) -> Unit,
    aoRestaurar: () -> Unit,
    aoRestaurarBotoes: () -> Unit,
    aoEditarLayout: () -> Unit,
    overlayConfig: OverlayConfigNova,
    aoMudarOverlay: (OverlayConfigNova) -> Unit,
    ajustes: AjustesDeJogo,
    aoMudarAjuste: (String, Any, String) -> Unit
) {
    val ctx = LocalContext.current
    val corTexto = paleta.texto
    val corDestaque = paleta.destaque
    val corContorno = paleta.contorno

    var confirmarRestaurar by remember { mutableStateOf(false) }
    var confirmarJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }

    LaunchedEffect(capturando) {
        Log.d("PhoenixAjustes", "aba controles capturando=$capturando hotkey=${atalhosCfg.hotkey}")
    }

    LaunchedEffect(confirmarRestaurar) {
        if (confirmarRestaurar) {
            delay(3000)
            confirmarRestaurar = false
            confirmarJob = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ——— CONTROLES NA TELA ———
        Text(
            text = stringResource(R.string.overlay_jogo_titulo),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = corDestaque,
        )

        LinhaSlider(
            titulo = stringResource(R.string.overlay_jogo_opacidade_ociosa),
            dica = stringResource(R.string.overlay_jogo_opacidade_ociosa_dica),
            valor = overlayConfig.opacidadeOciosa,
            onValueChange = { aoMudarOverlay(overlayConfig.copy(opacidadeOciosa = it)) },
            paleta = paleta,
            faixa = 0f..1f,
            passos = 19
        )

        LinhaSlider(
            titulo = stringResource(R.string.overlay_jogo_opacidade_pressionado),
            dica = stringResource(R.string.overlay_jogo_opacidade_pressionado_dica),
            valor = overlayConfig.opacidadePressionado,
            onValueChange = { aoMudarOverlay(overlayConfig.copy(opacidadePressionado = it)) },
            paleta = paleta,
            faixa = 0f..1f,
            passos = 19
        )

        LinhaSlider(
            titulo = stringResource(R.string.overlay_jogo_tamanho),
            dica = stringResource(R.string.overlay_jogo_tamanho_dica),
            valor = overlayConfig.escalaGlobal,
            onValueChange = { aoMudarOverlay(overlayConfig.copy(escalaGlobal = it)) },
            paleta = paleta,
            faixa = 0.5f..2f,
            passos = 30
        )

        LinhaSegmentada(
            titulo = stringResource(R.string.overlay_jogo_visibilidade),
            dica = stringResource(R.string.overlay_jogo_visibilidade_dica),
            opcoes = listOf(
                stringResource(R.string.overlay_jogo_vis_sempre) to ModoVisibilidadeOverlay.SEMPRE,
                stringResource(R.string.overlay_jogo_vis_auto) to ModoVisibilidadeOverlay.AUTO_ESCONDER_COM_CONTROLE,
                stringResource(R.string.overlay_jogo_vis_nunca) to ModoVisibilidadeOverlay.NUNCA
            ),
            selecionado = overlayConfig.visivelModo,
            onSelect = { aoMudarOverlay(overlayConfig.copy(visivelModo = it as ModoVisibilidadeOverlay)) },
            paleta = paleta
        )

        LinhaInterruptor(
            titulo = stringResource(R.string.overlay_jogo_haptico),
            dica = stringResource(R.string.overlay_jogo_haptico_dica),
            checado = overlayConfig.hapticoAtivo,
            onCheckedChange = { aoMudarOverlay(overlayConfig.copy(hapticoAtivo = it)) },
            paleta = paleta
        )

        LinhaInterruptor(
            titulo = stringResource(R.string.overlay_jogo_rotulos),
            dica = stringResource(R.string.overlay_jogo_rotulos_dica),
            checado = overlayConfig.mostrarRotulos,
            onCheckedChange = { aoMudarOverlay(overlayConfig.copy(mostrarRotulos = it)) },
            paleta = paleta
        )

        LinhaInterruptor(
            titulo = stringResource(R.string.overlay_jogo_ocultar_menu),
            dica = stringResource(R.string.overlay_jogo_ocultar_menu_dica),
            checado = overlayConfig.ocultarNoMenu,
            onCheckedChange = { aoMudarOverlay(overlayConfig.copy(ocultarNoMenu = it)) },
            paleta = paleta
        )
        
        LinhaBotaoGenerico(
            texto = stringResource(R.string.overlay_jogo_editar_layout),
            corTexto = corTexto,
            corDestaque = corDestaque,
            corContorno = corContorno,
            onClick = aoEditarLayout
        )

        // ——— ATALHOS ———
        Text(
            text = stringResource(R.string.atalhos_titulo),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = corDestaque,
        )

        // B) HOTKEY row
        LinhaHotkey(
            hotkey = atalhosCfg.hotkey,
            capturando = capturando == "HOTKEY",
            corTexto = corTexto,
            corDestaque = corDestaque,
            corContorno = corContorno,
            aoCapturar = { aoCapturar("HOTKEY") },
            aoCancelar = aoCancelarCaptura,
            aoLimpar = aoLimparHotkey,
            desabilitado = capturando != null && capturando != "HOTKEY",
        )

        // C) One row per AcaoAtalho
        for (acao in AcaoAtalho.entries) {
            val combo = atalhosCfg.combo(acao)
            val textoCombo = if (combo.isEmpty()) {
                ctx.getString(R.string.controles_indisponivel)
            } else {
                combo.joinToString(" + ") { nomeDaTecla(it) }
            }
            val usarHotkey = atalhosCfg.acoes[acao.name]?.usarHotkey ?: true
            val hotkeyDesabilitada = atalhosCfg.hotkey == 0

            LinhaAtalhoEditavel(
                acao = acao,
                rotuloRes = acao.rotulo,
                textoCombo = textoCombo,
                usarHotkey = usarHotkey,
                hotkeyDesabilitada = hotkeyDesabilitada,
                capturando = capturando == acao.name,
                corTexto = corTexto,
                corDestaque = corDestaque,
                corContorno = corContorno,
                aoCapturar = { aoCapturar(acao.name) },
                aoCancelar = aoCancelarCaptura,
                aoLimpar = { aoLimpar(acao) },
                aoAlternarHotkey = { aoAlternarHotkey(acao, !usarHotkey) },
            )
        }

        // D) Aviso
        Text(
            text = stringResource(R.string.atalhos_aviso_conflito),
            fontSize = 11.sp,
            color = corTexto.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        // E) Restaurar padrão
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BotaoRestaurar(
                confirmar = confirmarRestaurar,
                corTexto = corTexto,
                corDestaque = corDestaque,
                corContorno = corContorno,
                aoConfirmar1 = { confirmarRestaurar = true },
                aoConfirmar2 = {
                    confirmarRestaurar = false
                    aoRestaurar()
                },
            )
        }

        // ——— BOTÕES DO CONTROLE ———
        Text(
            text = stringResource(R.string.controles_botoes_control),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = corDestaque,
        )

        val botoesJogo = when (plataforma.uppercase()) {
            "NES" -> listOf(
                Triple("D-pad", 0, true),
                Triple("A", 4, false),
                Triple("B", 5, false),
                Triple("SELECT", 10, false),
                Triple("START", 11, false),
            )
            else -> listOf(
                Triple("D-pad ↑", 0, true),
                Triple("D-pad ↓", 1, true),
                Triple("D-pad ←", 2, true),
                Triple("D-pad →", 3, true),
                Triple("A", 4, false),
                Triple("B", 5, false),
                Triple("X", 6, false),
                Triple("Y", 7, false),
                Triple("L", 8, false),
                Triple("R", 9, false),
                Triple("SELECT", 10, false),
                Triple("START", 11, false),
            )
        }

        for (bt in botoesJogo) {
            val rotulo = bt.first
            val indiceMap = bt.second
            val isAxisGroup = bt.third

            if (isAxisGroup) {
                if (rotulo == "D-pad") {
                    val nomeBotaoVirtual = "CIMA"
                    val textoCapturado = if (mapeamento[0] != BotaoVirtual.CIMA.padrao) {
                        nomeDaTecla(mapeamento[0])
                    } else {
                        ctx.getString(R.string.controles_indisponivel)
                    }

                    LinhaBotaoEditavel(
                        rotulo = "D-pad (Cima)",
                        textoCapturado = textoCapturado,
                        capturando = capturando == "BTN_$nomeBotaoVirtual",
                        corTexto = corTexto,
                        corDestaque = corDestaque,
                        corContorno = corContorno,
                        aoCapturar = { aoCapturar("BTN_$nomeBotaoVirtual") },
                        aoCancelar = aoCancelarCaptura,
                    )
                } else {
                    val nomeBotaoVirtual = when (rotulo) {
                        "D-pad ↑" -> "CIMA"
                        "D-pad ↓" -> "BAIXO"
                        "D-pad ←" -> "ESQUERDA"
                        "D-pad →" -> "DIREITA"
                        else -> "CIMA"
                    }
                    val textoCapturado = if (mapeamento[indiceMap] != BotaoVirtual.valueOf(nomeBotaoVirtual).padrao) {
                        nomeDaTecla(mapeamento[indiceMap])
                    } else {
                        ctx.getString(R.string.controles_indisponivel)
                    }
                    LinhaBotaoEditavel(
                        rotulo = rotulo,
                        textoCapturado = textoCapturado,
                        capturando = capturando == "BTN_$nomeBotaoVirtual",
                        corTexto = corTexto,
                        corDestaque = corDestaque,
                        corContorno = corContorno,
                        aoCapturar = { aoCapturar("BTN_$nomeBotaoVirtual") },
                        aoCancelar = aoCancelarCaptura,
                    )
                }
            } else {
                val nomeBotaoVirtual = rotulo
                val textoCapturado = if (mapeamento[indiceMap] != BotaoVirtual.valueOf(nomeBotaoVirtual).padrao) {
                    nomeDaTecla(mapeamento[indiceMap])
                } else {
                    ctx.getString(R.string.controles_indisponivel)
                }

                LinhaBotaoEditavel(
                    rotulo = rotulo,
                    textoCapturado = textoCapturado,
                    capturando = capturando == "BTN_$nomeBotaoVirtual",
                    corTexto = corTexto,
                    corDestaque = corDestaque,
                    corContorno = corContorno,
                    aoCapturar = { aoCapturar("BTN_$nomeBotaoVirtual") },
                    aoCancelar = aoCancelarCaptura,
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BotaoRestaurar(
                confirmar = confirmarRestaurar,
                corTexto = corTexto,
                corDestaque = corDestaque,
                corContorno = corContorno,
                aoConfirmar1 = { confirmarRestaurar = true },
                aoConfirmar2 = {
                    confirmarRestaurar = false
                    aoRestaurarBotoes()
                },
                textoBase = stringResource(R.string.controles_restaurar_botoes)
            )
        }
    }
}

@Composable
private fun LinhaHotkey(
    hotkey: Int,
    capturando: Boolean,
    corTexto: Color,
    corDestaque: Color,
    corContorno: Color,
    aoCapturar: () -> Unit,
    aoCancelar: () -> Unit,
    aoLimpar: () -> Unit,
    desabilitado: Boolean,
) {
    val ctx = LocalContext.current
    val rotuloStr = stringResource(R.string.atalhos_hotkey_titulo)
    val foco = remember { FocusRequester() }

    LaunchedEffect(capturando) {
        if (capturando) foco.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (desabilitado) 0.5f else 1f)
            .border(1.dp, corContorno, RoundedCornerShape(12.dp))
            .clickable(enabled = !desabilitado && !capturando, onClick = aoCapturar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rotuloStr,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (capturando) corDestaque else corTexto,
            )
            Text(
                text = if (capturando) stringResource(R.string.atalhos_pressione_botao) else if (hotkey != 0) nomeDaTecla(hotkey) else stringResource(R.string.controles_indisponivel),
                fontSize = 13.sp,
                color = if (capturando) corDestaque else corTexto.copy(alpha = 0.7f),
            )
        }
        if (!capturando && hotkey != 0) {
            TextButton(
                onClick = aoLimpar,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.atalhos_limpar),
                    fontSize = 13.sp,
                    color = corDestaque
                )
            }
        } else if (capturando) {
            TextButton(
                onClick = aoCancelar,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .focusRequester(foco)
            ) {
                Text(
                    text = stringResource(R.string.atalhos_cancelar),
                    fontSize = 13.sp,
                    color = corDestaque
                )
            }
        }
    }
}

@Composable
private fun LinhaAtalhoEditavel(
    acao: AcaoAtalho,
    rotuloRes: Int,
    textoCombo: String,
    usarHotkey: Boolean,
    hotkeyDesabilitada: Boolean,
    capturando: Boolean,
    corTexto: Color,
    corDestaque: Color,
    corContorno: Color,
    aoCapturar: () -> Unit,
    aoCancelar: () -> Unit,
    aoLimpar: () -> Unit,
    aoAlternarHotkey: () -> Unit,
) {
    val rotuloStr = stringResource(rotuloRes)
    val foco = remember { FocusRequester() }

    LaunchedEffect(capturando) {
        if (capturando) foco.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, corContorno, RoundedCornerShape(12.dp))
            .clickable(enabled = !capturando, onClick = aoCapturar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rotuloStr,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (capturando) corDestaque else corTexto,
                )
                Text(
                    text = if (capturando) stringResource(R.string.atalhos_pressione_botao) else textoCombo,
                    fontSize = 13.sp,
                    color = if (capturando) corDestaque else corTexto.copy(alpha = 0.7f),
                )
            }
            if (!capturando && textoCombo != stringResource(R.string.controles_indisponivel)) {
                TextButton(
                    onClick = aoLimpar,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.atalhos_limpar),
                        fontSize = 13.sp,
                        color = corDestaque
                    )
                }
            } else if (capturando) {
                TextButton(
                    onClick = aoCancelar,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .focusRequester(foco)
                ) {
                    Text(
                        text = stringResource(R.string.atalhos_cancelar),
                        fontSize = 13.sp,
                        color = corDestaque
                    )
                }
            }
        }
        
        if (!hotkeyDesabilitada && textoCombo != stringResource(R.string.controles_indisponivel)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = aoAlternarHotkey)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.atalhos_usar_hotkey),
                    fontSize = 13.sp,
                    color = corTexto.copy(alpha = 0.9f)
                )
                Switch(
                    checked = usarHotkey,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = corDestaque,
                        checkedTrackColor = corDestaque.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.sizeIn(maxHeight = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun LinhaBotaoEditavel(
    rotulo: String,
    textoCapturado: String,
    capturando: Boolean,
    corTexto: Color,
    corDestaque: Color,
    corContorno: Color,
    aoCapturar: () -> Unit,
    aoCancelar: () -> Unit,
) {
    val foco = remember { FocusRequester() }

    LaunchedEffect(capturando) {
        if (capturando) foco.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, corContorno, RoundedCornerShape(12.dp))
            .clickable(enabled = !capturando, onClick = aoCapturar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rotulo,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (capturando) corDestaque else corTexto,
            )
            Text(
                text = if (capturando) stringResource(R.string.atalhos_pressione_botao) else textoCapturado,
                fontSize = 13.sp,
                color = if (capturando) corDestaque else corTexto.copy(alpha = 0.7f),
            )
        }
        if (capturando) {
            TextButton(
                onClick = aoCancelar,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .focusRequester(foco)
            ) {
                Text(
                    text = stringResource(R.string.atalhos_cancelar),
                    fontSize = 13.sp,
                    color = corDestaque
                )
            }
        }
    }
}

@Composable
private fun BotaoRestaurar(
    confirmar: Boolean,
    corTexto: Color,
    corDestaque: Color,
    corContorno: Color,
    aoConfirmar1: () -> Unit,
    aoConfirmar2: () -> Unit,
    textoBase: String = stringResource(R.string.atalhos_restaurar_padrao)
) {
    Box(
        modifier = Modifier
            .border(1.dp, corContorno, RoundedCornerShape(16.dp))
            .clickable(onClick = if (confirmar) aoConfirmar2 else aoConfirmar1)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (confirmar) stringResource(R.string.atalhos_confirme_novamente) else textoBase,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (confirmar) Color(0xFFE53935) else corTexto,
        )
    }
}

@Composable
private fun LinhaBotaoGenerico(
    texto: String,
    corTexto: Color,
    corDestaque: Color,
    corContorno: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, corContorno, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = texto,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = corTexto,
        )
    }
}
