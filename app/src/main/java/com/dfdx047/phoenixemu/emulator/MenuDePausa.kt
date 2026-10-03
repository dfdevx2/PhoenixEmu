package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.R

/**
 * Menu de pausa completo: fundo desfocado + painel de vidro com
 * cabeçalho, Salvar/Carregar, slots e botões Inferiores.
 *
 * Controle (teclado / gamepad):
 *   Cima / Baixo  – muda de linha
 *   Esquerda / Direita – muda dentro da linha
 *   A            – confirma
 *   B            – fecha o menu
 *   L1 / R1      – troca o slot selecionado
 */

internal data class CursorPos(
    val linha: Int = 1,
    val coluna: Int = 0,
)

@Composable
internal fun MenuDePausa(
    visivel: Boolean,
    fundo: Bitmap?,
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoFechar: () -> Unit,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    aoMudarSlot: (Int) -> Unit,
) {
    AnimatedVisibility(
        visible = visivel,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 4 }
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        ) + slideOutVertically(
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            targetOffsetY = { it / 4 }
        ),
    ) {
        MenuDePausaInner(
            fundo = fundo,
            nomeDoJogo = nomeDoJogo,
            tempoJogadoMinutos = tempoJogadoMinutos,
            slots = slots,
            slotSelecionado = slotSelecionado,
            aoFechar = aoFechar,
            aoSalvarEstado = aoSalvarEstado,
            aoCarregarEstado = aoCarregarEstado,
            aoContinuar = aoContinuar,
            aoReiniciar = aoReiniciar,
            aoSair = aoSair,
            aoMudarSlot = aoMudarSlot,
        )
    }
}

@Composable
private fun MenuDePausaInner(
    fundo: Bitmap?,
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoFechar: () -> Unit,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    aoMudarSlot: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo desfocado
        if (fundo != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = fundo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(24.dp) else it
                        },
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(onClick = aoFechar),
                    ) {}
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                Box(modifier = Modifier.fillMaxSize().clickable(onClick = aoFechar)) {}
            }
        }

        // Painel central (vidro)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CabecalhoPausa(nomeDoJogo, tempoJogadoMinutos)
                LinhaSalvarCarregar(
                    aoSalvar = { aoSalvarEstado(slotSelecionado) },
                    aoCarregar = { if (slots.getOrNull(slotSelecionado - 1)?.exists == true) aoCarregarEstado(slotSelecionado) },
                )
                LinhaSlots(slots, slotSelecionado, aoSelecionar = { aoMudarSlot(it) })
                Box(modifier = Modifier.weight(1f))
                LinhaBotoesInferiores(aoContinuar, aoReiniciar, aoSair)
            }
        }
    }
}

@Composable
private fun CabecalhoPausa(
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(128.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A3E), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "\uD83D\uDCF8", fontSize = 40.sp)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (nomeDoJogo.isNotBlank()) nomeDoJogo else "Sem jogo",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            val tempoTexto = when {
                tempoJogadoMinutos <= 0 -> ctx.getString(R.string.jogo_menu_tempo_jogado)
                else -> {
                    val h = tempoJogadoMinutos / 60
                    val m = tempoJogadoMinutos % 60
                    if (h > 0) "${h}h ${m}min" else "${m} min"
                }
            }
            Text(
                text = tempoTexto,
                style = TextStyle(fontSize = 14.sp, color = Color.LightGray.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun LinhaSalvarCarregar(
    aoSalvar: () -> Unit,
    aoCarregar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = aoSalvar,
            modifier = Modifier.weight(1f).height(72.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_salvar_estado),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = aoCarregar,
            modifier = Modifier.weight(1f).height(72.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_carregar_estado),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LinhaSlots(
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        for ((index, slot) in slots.withIndex()) {
            val numeroSlot = index + 1
            val isSelected = numeroSlot == slotSelecionado
            SlotCardMini(
                slot = slot,
                numeroSlot = numeroSlot,
                selecionado = isSelected,
                onClick = { aoSelecionar(numeroSlot) },
            )
        }
    }
}

@Composable
private fun SlotCardMini(
    slot: SlotData,
    numeroSlot: Int,
    selecionado: Boolean,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val borderColor = if (selecionado) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (selecionado) 2.dp else 0.dp

    Column(
        modifier = Modifier.aspectRatio(16f / 9f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                .background(if (slot.exists && slot.bitmap != null) Color.Transparent else Color.DarkGray.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            if (slot.exists && slot.bitmap != null) {
                Image(
                    bitmap = slot.bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = ctx.getString(R.string.jogo_menu_vazio),
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.5f),
                )
            }
        }

        Text(
            text = if (slot.exists) {
                "${ctx.getString(R.string.jogo_menu_slot, numeroSlot)} – ${slot.dateText}"
            } else {
                ctx.getString(R.string.jogo_menu_vazio)
            },
            fontSize = 10.sp,
            color = if (selecionado) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.7f),
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LinhaBotoesInferiores(
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = aoContinuar,
            modifier = Modifier.weight(1f).height(60.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_continuar),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = aoReiniciar,
            modifier = Modifier.weight(1f).height(60.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_reiniciar),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = aoSair,
            modifier = Modifier.weight(1f).height(60.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_sair),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
