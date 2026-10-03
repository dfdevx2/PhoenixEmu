package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
            Column(modifier = Modifier.fillMaxSize()) {

                // — Cabeçalho fixo —
                CabecalhoPausa(nomeDoJogo, tempoJogadoMinutos, aoFechar)

                // — Barra de abas fixa —
                BarraDeAbas(abaAtual, aoMudarAba)

                // Divisor sob a barra de abas
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // — Área rolável com peso —
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    when (abaAtual) {
                        AbaDoMenu.JOGO -> ConteudoAbaJogo(slots, slotSelecionado, aoSalvarEstado, aoCarregarEstado, aoMudarSlot)
                        AbaDoMenu.AJUSTES -> TextoEmBreve()
                        AbaDoMenu.CONTROLES -> TextoEmBreve()
                        else -> ConteudoAbaJogo(slots, slotSelecionado, aoSalvarEstado, aoCarregarEstado, aoMudarSlot)
                    }
                }

                // — Rodapé fixo —
                RodapeFixo(aoContinuar, aoReiniciar, aoSair)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Capa placeholder — ícone vetorial de controle sobre fundo translúcido
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A3E).copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(image = Icons.Outlined.Gamepad),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.7f)),
            )
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
    val nome = if (uri != null) {
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
    // Remove extensões comuns de ROM/ZIP sem diferenciar maiúsculas/minúsculas
    val extensao = nome.substringAfterLast('.', "").lowercase()
    if (extensao in setOf("zip", "7z", "sfc", "smc", "nes", "fig", "swc")) {
        return nome.substringBeforeLast('.').trim()
    }
    return nome
}

// =====================================================================
// Barra de abas — estilo flat com linha deslizante
// =====================================================================

private data class TabItem(
    val aba: AbaDoMenu,
    val labelRes: Int,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun BarraDeAbas(
    abaAtual: AbaDoMenu,
    aoMudarAba: (AbaDoMenu) -> Unit,
) {
    val ctx = LocalContext.current
    val abas = listOf(
        TabItem(AbaDoMenu.JOGO, R.string.pause_aba_jogo, Icons.Outlined.PlayArrow),
        TabItem(AbaDoMenu.AJUSTES, R.string.pause_aba_ajustes, Icons.Outlined.Settings),
        TabItem(AbaDoMenu.CONTROLES, R.string.pause_aba_controles, Icons.Outlined.Gamepad),
    )
    val primaria = MaterialTheme.colorScheme.primary
    val secundaria = MaterialTheme.colorScheme.secondary

    var abaIndex by remember { mutableIntStateOf(abas.indexOfFirst { it.aba == abaAtual }.takeIf { it >= 0 } ?: 0) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            abas.forEachIndexed { index, item ->
                val ativa = index == abaIndex
                val corTexto = if (ativa) primaria else secundaria

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = {
                            abaIndex = index
                            aoMudarAba(item.aba)
                        })
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ativa) {
                        // Fundo suave para aba ativa
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(primaria.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TabConteudo(item, corTexto, ativa, ctx)
                        }
                    } else {
                        TabConteudo(item, corTexto, ativa, ctx)
                    }
                }
            }
        }

        // Indicador deslizante — linha de 3dp BAIXO dos rótulos, sobre o divisor
        val larguraAba by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (abas.size > 0) {
                with(LocalDensity.current) {
                    val larguraTotalPx = LocalConfiguration.current.screenWidthDp.dp.toPx()
                    val paddingPx = 16.dp.toPx()
                    val gapPx = 8.dp.toPx()
                    ((larguraTotalPx - paddingPx * 2 - gapPx * 2) / abas.size).toDp()
                }
            } else 0.dp,
            label = "tabIndicatorWidth",
        )
        val indiceX by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (abaIndex < abas.size) {
                with(LocalDensity.current) {
                    val larguraTotalPx = LocalConfiguration.current.screenWidthDp.dp.toPx()
                    val paddingPx = 16.dp.toPx()
                    val gapPx = 8.dp.toPx()
                    val disponivel = larguraTotalPx - paddingPx * 2
                    val larguraAbaCalc = (disponivel - gapPx * 2) / abas.size
                    (paddingPx + larguraAbaCalc / 2 + abaIndex * (larguraAbaCalc + gapPx)).toDp()
                }
            } else 0.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "tabIndicatorX",
        )
        Box(
            modifier = Modifier
                .offset(x = indiceX, y = (-3).dp)
                .width(larguraAba)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(primaria),
        )
    }
}

@Composable
private fun TabConteudo(
    item: TabItem,
    corTexto: Color,
    ativa: Boolean,
    ctx: android.content.Context,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberVectorPainter(image = item.iconVector),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(corTexto),
        )
        Text(
            text = ctx.getString(item.labelRes),
            fontSize = 12.sp,
            fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal,
            color = corTexto,
        )
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
            numeroSlot = slotSelecionado,
        )

        // 2 — Rótulo SLOTS
        RótuloSeção(texto = "SLOTS")

        // 3 — Linha dos 4 slots
        LinhaSlots(slots, slotSelecionado, aoSelecionar = { aoMudarSlot(it) })
    }
}

// ---- Botões de vidro (sem preenchimento sólido) ----

@Composable
private fun BotaoVidro(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconRes: Int,
    labelRes: Int,
    ctx: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
) {
    val primária = MaterialTheme.colorScheme.primary
    val alphaEnabled = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = primária.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(alphaEnabled),
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (enabled) Color.White else Color.LightGray.copy(alpha = 0.5f),
                ),
            )
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
    numeroSlot: Int,
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BotaoVidro(
            onClick = aoSalvar,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = true,
            iconRes = R.drawable.ic_menu_salvar,
            labelRes = R.string.jogo_menu_salvar_estado,
            ctx = ctx,
        )
        BotaoVidro(
            onClick = aoCarregar,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = !slotVazio,
            iconRes = R.drawable.ic_menu_carregar,
            labelRes = R.string.jogo_menu_carregar_estado,
            ctx = ctx,
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
private fun LinhaSlots(
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
    ) {
        val cardW = ((maxWidth - 8.dp * 3) / 4).coerceAtLeast(60.dp)

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
                    cardWidth = cardW,
                )
            }
        }
    }
}

@Composable
private fun SlotCardMini(
    slot: SlotData,
    numeroSlot: Int,
    selecionado: Boolean,
    onClick: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
) {
    val ctx = LocalContext.current
    val corPrimaria = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(cardWidth),
    ) {
        // (a) Miniatura numa Box com emblema sobreposto
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardWidth * 3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (selecionado) 2.dp else 1.dp,
                    color = if (selecionado) corPrimaria else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                )
                .background(
                    if (slot.exists && slot.bitmap != null) Color.Transparent else Color.DarkGray.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onClick),
        ) {
            // Conteúdo da miniatura
            if (slot.exists && slot.bitmap != null) {
                Image(
                    bitmap = slot.bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ctx.getString(R.string.jogo_slot_vazio_text),
                        fontSize = 10.sp,
                        color = Color.LightGray.copy(alpha = 0.5f),
                    )
                }
            }

            // (b) Emblema circular 16dp no canto superior esquerdo
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selecionado) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(corPrimaria),
                    ) {
                        Text(
                            text = numeroSlot.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.5.dp, corPrimaria, androidx.compose.foundation.shape.CircleShape),
                    ) {
                        Text(
                            text = numeroSlot.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = corPrimaria,
                        )
                    }
                }
            }
        }

        // (c) Duas linhas pequenas abaixo — data e hora
        if (slot.exists && slot.dateText.isNotBlank() && slot.timeText.isNotBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = slot.dateText,
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = slot.timeText,
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// =====================================================================
// Rodapé fixo — 3 botões com estilo vidro
// =====================================================================

@Composable
private fun RodapeFixo(
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
) {
    val ctx = LocalContext.current
    val primária = MaterialTheme.colorScheme.primary

    Column {
        // Divisor de 1dp no topo (alpha 0.12)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(primária.copy(alpha = 0.12f)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RodapeBotao(
                modifier = Modifier.weight(1f),
                onClick = aoContinuar,
                labelRes = R.string.jogo_menu_continuar,
                cor = primária,
                alphaFundo = 0.24f,
                ctx = ctx,
            )
            RodapeBotao(
                modifier = Modifier.weight(1f),
                onClick = aoReiniciar,
                labelRes = R.string.jogo_menu_reiniciar,
                cor = primária,
                alphaFundo = 0.12f,
                ctx = ctx,
            )
            RodapeBotao(
                modifier = Modifier.weight(1f),
                onClick = aoSair,
                labelRes = R.string.jogo_menu_sair,
                cor = primária,
                alphaFundo = 0.12f,
                ctx = ctx,
            )
        }
    }
}

@Composable
private fun RodapeBotao(
    modifier: Modifier,
    onClick: () -> Unit,
    labelRes: Int,
    cor: Color,
    alphaFundo: Float,
    ctx: android.content.Context,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .border(
                width = 1.dp,
                color = cor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .background(cor.copy(alpha = alphaFundo), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ctx.getString(labelRes),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
