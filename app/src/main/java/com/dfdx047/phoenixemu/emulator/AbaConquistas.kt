package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dfdx047.phoenixemu.R

/** Aba "Conquistas" do menu de pausa: resumo + lista (bloqueadas primeiro). */
@Composable
internal fun ConteudoAbaConquistas(texto: Color, destaque: Color) {
    val itens = EstadoRaJogo.itens
    SeletorHardcoreDoJogo(texto, destaque)
    LaunchedEffect(EstadoRaJogo.ativo) {
        if (EstadoRaJogo.ativo) {
            val novos = withContext(Dispatchers.IO) { RaNativo.lista() }
            if (novos.isNotEmpty()) EstadoRaJogo.itens = novos
        }
    }
    if (!EstadoRaJogo.ativo) {
        Text(stringResource(R.string.conq_menu_vazio), color = texto.copy(alpha = 0.6f), fontSize = 12.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            EstadoRaJogo.titulo,
            color = texto,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(
                R.string.conq_aviso_inicio_texto,
                EstadoRaJogo.desbloqueadas, EstadoRaJogo.total,
                EstadoRaJogo.pontosGanhos, EstadoRaJogo.pontosTotal
            ),
            color = texto.copy(alpha = 0.75f),
            fontSize = 12.sp
        )
        if (EstadoRaJogo.hardcore) Text(
            stringResource(R.string.conq_hardcore_nome),
            color = destaque,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        val frac = if (EstadoRaJogo.total > 0) (EstadoRaJogo.desbloqueadas.toFloat() / EstadoRaJogo.total).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(texto.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .background(destaque)
            )
        }
        if (itens.isEmpty()) {
            Text(stringResource(R.string.conq_menu_vazio), color = texto.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        val ordenadas = itens.sortedBy { if (it.estado == 0) 0 else 1 }
        ordenadas.forEach { item ->
            val bloqueada = item.estado == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (bloqueada) 0.55f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = item.badgeUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.titulo,
                        color = texto,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.descricao,
                        color = texto.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bloqueada && item.progresso.isNotBlank()) {
                        Text(item.progresso, color = destaque, fontSize = 11.sp)
                    }
                }
                Text(
                    item.pontos.toString(),
                    color = if (bloqueada) texto.copy(alpha = 0.6f) else destaque,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun SeletorHardcoreDoJogo(texto: Color, destaque: Color) {
    val atual = EstadoRaJogo.hardcoreJogo
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.conq_hardcore_jogo), color = texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "PADRAO" to R.string.conq_opcao_padrao,
                "LIGADO" to R.string.conq_opcao_ligado,
                "DESLIGADO" to R.string.conq_opcao_desligado
            ).forEach { (valor, rotulo) ->
                val sel = atual == valor
                val forma = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(forma)
                        .background(if (sel) destaque.copy(alpha = 0.25f) else texto.copy(alpha = 0.08f))
                        .border(1.dp, if (sel) destaque else texto.copy(alpha = 0.2f), forma)
                        .clickable {
                            EstadoRaJogo.hardcoreJogo = valor
                            EstadoRaJogo.hardcoreJogoMudou = true
                            EstadoRaJogo.aoEscolherHardcore?.invoke(valor)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(rotulo),
                        color = texto,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Text(stringResource(R.string.conq_hardcore_jogo_dica), color = texto.copy(alpha = 0.65f), fontSize = 11.sp)
        Text(
            stringResource(if (EstadoRaJogo.hardcore) R.string.conq_hardcore_sessao_on else R.string.conq_hardcore_sessao_off),
            color = destaque,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (EstadoRaJogo.hardcoreJogoMudou) {
            Text(stringResource(R.string.conq_hardcore_salvo), color = texto.copy(alpha = 0.85f), fontSize = 11.sp)
        }
    }
}
