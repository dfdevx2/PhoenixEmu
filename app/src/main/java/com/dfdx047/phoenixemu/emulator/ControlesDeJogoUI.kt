package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun ConteudoAbaControles(
    paleta: PaletaMenu,
    mapeamento: IntArray,
    atalhosJson: String,
    plataforma: String,
) {
    val ctx = LocalContext.current
    val corTexto = paleta.texto
    val corDestaque = paleta.destaque
    val corContorno = paleta.contorno

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
            text = stringResource(R.string.controles_atalhos),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = corDestaque,
        )

        val configAtalhos = try {
            val json = JsonParser.parseString(atalhosJson).asJsonObject
            val hotkeyJson = json.get("hotkey")
            val hotkey = if (hotkeyJson != null && !hotkeyJson.isJsonNull) hotkeyJson.asInt else 0
            val acoesMap = mutableMapOf<String, AtalhoDaAcao>()
            for (acao in AcaoAtalho.entries) {
                val acaoJson = json.get(acao.name)
                if (acaoJson != null && !acaoJson.isJsonNull && acaoJson.isJsonObject) {
                    val obj = acaoJson.asJsonObject
                    val tecla = if (obj.has("tecla") && !obj.get("tecla").isJsonNull) obj.get("tecla").asInt else 0
                    val usarHotkey = if (obj.has("usarHotkey") && !obj.get("usarHotkey").isJsonNull) obj.get("usarHotkey").asBoolean else true
                    acoesMap[acao.name] = AtalhoDaAcao(tecla, usarHotkey)
                } else {
                    acoesMap[acao.name] = AtalhoDaAcao()
                }
            }
            ConfigDeAtalhos(hotkey, acoesMap)
        } catch (_: Exception) {
            ConfigDeAtalhos.padrao()
        }

        for (acao in AcaoAtalho.entries) {
            LinhaAtalho(
                acao = acao,
                configAtalhos = configAtalhos,
                corTexto = corTexto,
                corContorno = corContorno,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.controles_dica_mudar),
                fontSize = 11.sp,
                color = corTexto.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

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
