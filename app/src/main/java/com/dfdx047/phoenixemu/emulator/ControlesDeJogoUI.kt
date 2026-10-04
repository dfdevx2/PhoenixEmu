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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.dfdx047.phoenixemu.data.ConfigDeAtalhos
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.data.nomeDaTecla
import com.google.gson.JsonParser
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

        for ((nomeBotao, indice, ehDpad) in botoesJogo) {
            LinhaBotaoJogo(
                nomeBotao = nomeBotao,
                indice = indice,
                mapeamento = mapeamento,
                corTexto = corTexto,
                corContorno = corContorno,
                ehDpad = ehDpad,
            )
        }

        Text(
            text = stringResource(R.string.controles_dica_mudar_new),
            fontSize = 11.sp,
            color = corTexto.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.controles_em_breve),
                fontSize = 11.sp,
                color = corTexto.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LinhaBotaoJogo(
    nomeBotao: String,
    indice: Int,
    mapeamento: IntArray,
    corTexto: Color,
    corContorno: Color,
    ehDpad: Boolean,
) {
    val ctx = LocalContext.current
    val codigo = if (indice < mapeamento.size) mapeamento[indice] else 0
    val textoBotao = if (codigo == 0) {
        ctx.getString(R.string.controles_indisponivel)
    } else {
        val nome = nomeDaTecla(codigo)
        if (nome.isBlank() || nome == "0") ctx.getString(R.string.controles_indisponivel) else nome
    }

    val corFundo = if (ehDpad) {
        corContorno.copy(alpha = 0.08f)
    } else {
        corContorno.copy(alpha = 0.04f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(corFundo, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nomeBotao,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = corTexto,
            )
            Text(
                text = textoBotao,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corTexto.copy(alpha = 0.9f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinhaAtalho(
    acao: AcaoAtalho,
    configAtalhos: ConfigDeAtalhos,
    corTexto: Color,
    corContorno: Color,
) {
    val ctx = LocalContext.current
    val combo = configAtalhos.combo(acao)
    val textoCombo = if (combo.isEmpty()) {
        ctx.getString(R.string.controles_indisponivel)
    } else {
        combo.joinToString(" + ") { nomeDaTecla(it) }
    }

    val corFundo = corContorno.copy(alpha = 0.04f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(corFundo, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ctx.getString(acao.rotulo),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = corTexto,
            )
            Text(
                text = textoCombo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corTexto.copy(alpha = 0.9f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Linha de captura (placeholder) ───
@Composable
private fun LinhaCaptura(
    texto: String,
    aoCancelar: () -> Unit,
    corDestaque: Color,
    corTexto: Color,
    desabilitado: Boolean = false,
) {
    val corFundo = corDestaque.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(corFundo, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = texto,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corDestaque,
            )
            TextButton(
                onClick = aoCancelar,
                enabled = !desabilitado,
                modifier = Modifier.sizeIn(minHeight = 44.dp),
            ) {
                Text(
                    text = stringResource(R.string.atalhos_cancelar),
                    fontSize = 12.sp,
                    color = corTexto.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ─── Linha HOTKEY ───
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
    val nomeHotkey = if (hotkey == 0) {
        ctx.getString(R.string.controles_indisponivel)
    } else {
        nomeDaTecla(hotkey)
    }

    val corFundo = corContorno.copy(alpha = 0.04f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(corFundo, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // a) Título
        Text(
            text = stringResource(R.string.atalhos_hotkey_titulo),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = corTexto,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
        )

        // b) Status (linha de texto + botões)
        if (capturando) {
            Text(
                text = stringResource(R.string.atalhos_pressione_botao),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corDestaque,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
        } else {
            Text(
                text = nomeHotkey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corTexto.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
        }

        // c) Botões
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (capturando) {
                BotaoAcao(
                    texto = stringResource(R.string.atalhos_cancelar),
                    onClick = aoCancelar,
                    enabled = true,
                    corContorno = corContorno,
                    corDestaque = corDestaque,
                    corTexto = corTexto,
                    modifier = Modifier.weight(1f),
                )
            } else {
                BotaoAcao(
                    texto = stringResource(R.string.atalhos_alterar),
                    onClick = aoCapturar,
                    enabled = !desabilitado,
                    corContorno = corContorno,
                    corDestaque = corDestaque,
                    corTexto = corTexto,
                    modifier = Modifier.weight(1f),
                )
                BotaoAcao(
                    texto = stringResource(R.string.atalhos_limpar),
                    onClick = aoLimpar,
                    enabled = !desabilitado,
                    corContorno = corContorno,
                    corDestaque = corDestaque,
                    corTexto = corTexto,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─── Linha de atalho editável ───
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
    val corFundo = corContorno.copy(alpha = 0.04f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(corFundo, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // a) Título
        Text(
            text = stringResource(rotuloRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = corTexto,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
        )

        // b) Status
        if (capturando) {
            Text(
                text = stringResource(R.string.atalhos_pressione_botao),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corDestaque,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
        } else {
            Text(
                text = textoCombo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = corTexto.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
        }

        // c) Botões
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (capturando) {
                BotaoAcao(
                    texto = stringResource(R.string.atalhos_cancelar),
                    onClick = aoCancelar,
                    enabled = true,
                    corContorno = corContorno,
                    corDestaque = corDestaque,
                    corTexto = corTexto,
                    modifier = Modifier.weight(1f),
                )
            } else {
                BotaoAcao(
                    texto = stringResource(R.string.atalhos_alterar),
                    onClick = aoCapturar,
                    enabled = true,
                    corContorno = corContorno,
                    corDestaque = corDestaque,
                    corTexto = corTexto,
                    modifier = Modifier.weight(1f),
                )
                BotaoAcao(
                    texto = stringResource(R.string.atalhos_limpar),
                    onClick = aoLimpar,
                    enabled = true,
                    corContorno = corContorno,
                    corDestaque = corDestaque,
                    corTexto = corTexto,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // d) Switch "Usar hotkey"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.atalhos_usar_hotkey),
                fontSize = 11.sp,
                color = corTexto.copy(alpha = 0.7f),
            )
            if (hotkeyDesabilitada) {
                Text(
                    text = stringResource(R.string.atalhos_definir_hotkey_primeiro),
                    fontSize = 11.sp,
                    color = corTexto.copy(alpha = 0.4f),
                )
            } else {
                Switch(
                    checked = usarHotkey,
                    onCheckedChange = { aoAlternarHotkey() },
                    enabled = !capturando,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = corDestaque,
                        checkedTrackColor = corDestaque.copy(alpha = 0.5f),
                        uncheckedThumbColor = corTexto.copy(alpha = 0.6f),
                        uncheckedTrackColor = corTexto.copy(alpha = 0.2f),
                    ),
                )
            }
        }
    }
}

// ─── Botão de ação (Alterar / Limpar) ───
@Composable
private fun BotaoAcao(
    texto: String,
    onClick: () -> Unit,
    enabled: Boolean,
    corContorno: Color,
    corDestaque: Color,
    corTexto: Color,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(Modifier.sizeIn(minHeight = 44.dp))
            .border(
                width = 1.dp,
                color = corDestaque.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = texto,
            fontSize = 12.sp,
            color = if (enabled) corDestaque else corTexto.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
}

// ─── Botão Restaurar padrão (2 toques) ───
@Composable
private fun BotaoRestaurar(
    confirmar: Boolean,
    corTexto: Color,
    corDestaque: Color,
    corContorno: Color,
    aoConfirmar1: () -> Unit,
    aoConfirmar2: () -> Unit,
) {
    TextButton(
        onClick = {
            if (confirmar) {
                aoConfirmar2()
            } else {
                aoConfirmar1()
            }
        },
        modifier = Modifier
            .sizeIn(minHeight = 44.dp)
            .border(
                width = 1.dp,
                color = corDestaque.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = if (confirmar) {
                stringResource(R.string.atalhos_confirme_novamente)
            } else {
                stringResource(R.string.atalhos_restaurar_padrao)
            },
            fontSize = 12.sp,
            color = corDestaque,
        )
    }
}
