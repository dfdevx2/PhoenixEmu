package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.R

/**
 * Menu de pausa completo: fundo desfocado + painel lateral esquerdo com
 * cabecalho, abas e conteudo da aba selecionada.
 *
 * Controle (teclado / gamepad):
 *   L1 / R1      – troca a aba (quando menu visivel)
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
    abaAtual: AbaDoMenu,
    aoFechar: () -> Unit,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    aoMudarSlot: (Int) -> Unit,
    aoMudarAba: (AbaDoMenu) -> Unit,
) {
    AnimatedVisibility(
        visible = visivel,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 4 },
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        ),
    ) {
        MenuDePausaInner(
            fundo = fundo,
            nomeDoJogo = nomeDoJogo,
            tempoJogadoMinutos = tempoJogadoMinutos,
            slots = slots,
            slotSelecionado = slotSelecionado,
            abaAtual = abaAtual,
            aoFechar = aoFechar,
            aoSalvarEstado = aoSalvarEstado,
            aoCarregarEstado = aoCarregarEstado,
            aoContinuar = aoContinuar,
            aoReiniciar = aoReiniciar,
            aoSair = aoSair,
            aoMudarSlot = aoMudarSlot,
            aoMudarAba = aoMudarAba,
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
    abaAtual: AbaDoMenu,
    aoFechar: () -> Unit,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    aoMudarSlot: (Int) -> Unit,
    aoMudarAba: (AbaDoMenu) -> Unit,
) {
    val config = LocalConfiguration.current
    val telaLarguraDp = config.screenWidthDp.dp
    val painelLarguraDp = (telaLarguraDp * 0.42f).coerceIn(340.dp, 420.dp)

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
                    // Area toda clicavel fecha o menu
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

        // Painel lateral esquerdo (vidro)
        Surface(
            modifier = Modifier
                .width(painelLarguraDp)
                .fillMaxHeight()
                .align(Alignment.CenterStart),
            shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            color = Color.Black.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Cabecalho
                CabecalhoPausa(nomeDoJogo, tempoJogadoMinutos, aoFechar)

                // Barra de abas em pílula
                BarraDeAbas(abaAtual, aoMudarAba)

                // Conteudo da aba selecionada
                when (abaAtual) {
                    AbaDoMenu.JOGO -> ConteudoAbaJogo(slots, slotSelecionado, aoSalvarEstado, aoCarregarEstado, aoMudarSlot, aoReiniciar, aoSair)
                    AbaDoMenu.AJUSTES -> TextoEmBreve()
                    AbaDoMenu.CONTROLES -> TextoEmBreve()
                    else -> ConteudoAbaJogo(slots, slotSelecionado, aoSalvarEstado, aoCarregarEstado, aoMudarSlot, aoReiniciar, aoSair)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Botoes Inferiores
                LinhaBotoesInferiores(aoContinuar, aoReiniciar, aoSair)
            }
        }
    }
}

// =====================================================================
// Cabecalho
// =====================================================================

@Composable
private fun CabecalhoPausa(
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
    aoFechar: () -> Unit,
) {
    val ctx = LocalContext.current
    val nomeAmigavel = rememberNomeAmigavel(nomeDoJogo)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Capa placeholder 48x64dp
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A3E), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "\uD83D\uDCF8", fontSize = 24.sp)
        }

        // Nome + tempo
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = nomeAmigavel,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                style = TextStyle(fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.7f)),
            )
        }

        // Botao X 40dp
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = aoFechar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u2715",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

private fun rememberNomeAmigavel(raw: String): String {
    val uri = try { Uri.parse(raw) } catch (e: Exception) { null }
    return if (uri != null) {
        val decoded = Uri.decode(uri.lastPathSegment ?: raw)
        decoded
            .split("/", ":")
            .lastOrNull { it.isNotBlank() } ?: decoded
            .substringBeforeLast('.')
            .trim()
    } else {
        raw
            .substringBeforeLast('.')
            .trim()
    }
}

// =====================================================================
// Barra de abas em pílula
// =====================================================================

private val AbaInfo = listOf(
    AbaDoMenu.JOGO to R.string.pause_aba_jogo,
    AbaDoMenu.AJUSTES to R.string.pause_aba_ajustes,
    AbaDoMenu.CONTROLES to R.string.pause_aba_controles,
)

@Composable
private fun BarraDeAbas(
    abaSelecionada: AbaDoMenu,
    aoMudarAba: (AbaDoMenu) -> Unit,
) {
    val primária = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.3f)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbaInfo.forEach { (aba, labelRes) ->
            val ativa = aba == abaSelecionada
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (ativa) primária else Color.Transparent)
                    .clickable(onClick = { aoMudarAba(aba) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 13.sp,
                    fontWeight = if (ativa) FontWeight.Bold else FontWeight.Normal,
                    color = if (ativa) Color.White else Color.LightGray.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// =====================================================================
// Conteúdo da aba "Jogo"
// =====================================================================

@Composable
private fun ConteudoAbaJogo(
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoMudarSlot: (Int) -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 1 — Botões Salvar / Carregar
        LinhaSalvarCarregarVidro(
            aoSalvar = { aoSalvarEstado(slotSelecionado) },
            aoCarregar = {
                if (slots.getOrNull(slotSelecionado - 1)?.exists == true) {
                    aoCarregarEstado(slotSelecionado)
                }
            },
            slotVazio = slots.getOrNull(slotSelecionado - 1)?.exists != true,
        )

        // 2 — Rótulo SLOTS
        RótuloSeção(texto = "SLOTS")

        // 3 — Linha dos 4 slots
        LinhaSlots(slots, slotSelecionado, aoSelecionar = { aoMudarSlot(it) })

        // 4 — Botões Reiniciar / Sair
        LinhaReiniciarSairVidro(
            aoReiniciar = aoReiniciar,
            aoSair = aoSair,
        )
    }
}

// ---- Botões de vidro (sem preenchimento sólido) ----

@Composable
private fun BotaoVidro(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emoji: String,
    labelRes: Int,
    ctx: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
) {
    val corPrimaria = MaterialTheme.colorScheme.primary
    val alphaEnabled = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = corPrimaria.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(alphaEnabled),
        ) {
            Text(text = emoji, fontSize = 16.sp)
            Text(
                text = ctx.getString(labelRes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color.LightGray.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun RótuloSeção(texto: String) {
    Text(
        text = texto,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        letterSpacing = 2.sp,
    )
}

@Composable
private fun LinhaSalvarCarregarVidro(
    aoSalvar: () -> Unit,
    aoCarregar: () -> Unit,
    slotVazio: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BotaoVidro(
            onClick = aoSalvar,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = true,
            emoji = "\uD83D\uDCBE",
            labelRes = R.string.jogo_menu_salvar_estado,
        )
        BotaoVidro(
            onClick = aoCarregar,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = !slotVazio,
            emoji = "\uD83D\uDCE5",
            labelRes = R.string.jogo_menu_carregar_estado,
        )
    }
}

@Composable
private fun LinhaReiniciarSairVidro(
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BotaoVidro(
            onClick = aoReiniciar,
            modifier = Modifier.weight(1f).height(44.dp),
            enabled = true,
            emoji = "\uD83D\uDD04",
            labelRes = R.string.jogo_menu_reiniciar,
        )
        BotaoVidro(
            onClick = aoSair,
            modifier = Modifier.weight(1f).height(44.dp),
            enabled = true,
            emoji = "\uD83D\uDEAA",
            labelRes = R.string.jogo_menu_sair,
        )
    }
}

@Composable
private fun TextoEmBreve() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.em_breve),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LinhaSalvarCarregar(
    aoSalvar: () -> Unit,
    aoCarregar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = aoSalvar,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_salvar_estado),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = aoCarregar,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_carregar_estado),
                fontSize = 14.sp,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
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
            fontSize = 9.sp,
            color = if (selecionado) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.7f),
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// =====================================================================
// Botoes Inferiores
// =====================================================================

@Composable
private fun LinhaBotoesInferiores(
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = aoContinuar,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_continuar),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = aoReiniciar,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_reiniciar),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = aoSair,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.jogo_menu_sair),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
