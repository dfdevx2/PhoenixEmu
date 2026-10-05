package com.dfdx047.phoenixemu.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

enum class ModoAbertura { GRADE, CARROSSEL }

private const val MAX_CAPAS_GRADE = 12
private const val MAX_CAPAS_CARROSSEL = 7
private const val ENTRADA_MS = 900
private const val SAIDA_MS = 220
private const val SIMPLES_MS = 350L

private fun easeOutBack(t: Float): Float {
    val c1 = 1.1f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

@Composable
fun AberturaCascata(
    capas: List<Any>,
    modo: ModoAbertura,
    fundo: Color,
    reduzido: Boolean,
    aoTerminar: () -> Unit,
    logo: @Composable () -> Unit,
) {
    val limite = if (modo == ModoAbertura.GRADE) MAX_CAPAS_GRADE else MAX_CAPAS_CARROSSEL
    val itens = remember(capas, modo) { capas.take(limite) }
    val simples = reduzido || itens.isEmpty()
    val progresso = remember { Animatable(0f) }
    val saida = remember { Animatable(0f) }
    var finalizado by remember { mutableStateOf(false) }
    var pular by remember { mutableStateOf(false) }
    val terminarAtual by rememberUpdatedState(aoTerminar)

    LaunchedEffect(Unit) {
        if (simples) {
            delay(SIMPLES_MS)
        } else {
            progresso.animateTo(1f, tween(ENTRADA_MS, easing = LinearEasing))
        }
        saida.animateTo(1f, tween(SAIDA_MS, easing = FastOutSlowInEasing))
        if (!finalizado) { finalizado = true; terminarAtual() }
    }
    LaunchedEffect(pular) {
        if (pular) {
            saida.animateTo(1f, tween(120))
            if (!finalizado) { finalizado = true; terminarAtual() }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - saida.value }
            .background(fundo)
            .pointerInput(Unit) { detectTapGestures { pular = true } }
    ) {
        val larguraDp = maxWidth.value
        val alturaDp = maxHeight.value
        val densidade = LocalDensity.current
        val larguraPx = with(densidade) { maxWidth.toPx() }
        val alturaPx = with(densidade) { maxHeight.toPx() }

        if (!simples) {
            val n = itens.size
            if (modo == ModoAbertura.GRADE) {
                val colunas = min(n, 6)
                val linhas = ceil(n / colunas.toFloat()).toInt()
                val folga = 10f
                val altPorAltura = (alturaDp * 0.8f - folga * (linhas - 1)) / linhas
                val larguraMax = (larguraDp * 0.9f - folga * (colunas - 1)) / colunas
                val largCard = min(larguraMax, altPorAltura * 0.72f)
                val altCard = largCard / 0.72f
                val totalW = largCard * colunas + folga * (colunas - 1)
                val totalH = altCard * linhas + folga * (linhas - 1)
                val x0 = (larguraDp - totalW) / 2f
                val y0 = (alturaDp - totalH) / 2f
                itens.forEachIndexed { i, fonte ->
                    val col = i % colunas
                    val lin = i / colunas
                    val inicio = col * 0.05f + lin * 0.03f
                    val distancia = 0.6f + col * 0.12f
                    CapaDeAbertura(
                        fonte = fonte,
                        modifier = Modifier
                            .offset(
                                (x0 + (largCard + folga) * col).dp,
                                (y0 + (altCard + folga) * lin).dp
                            )
                            .size(largCard.dp, altCard.dp)
                            .graphicsLayer {
                                val t = ((progresso.value - inicio) / 0.45f).coerceIn(0f, 1f)
                                val e = easeOutBack(t)
                                translationY = (1f - e) * alturaPx * distancia
                                alpha = t
                                val s = 0.85f + 0.15f * t
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            } else {
                val meio = (n - 1) / 2f
                val altCard = alturaDp * 0.5f
                val largCard = altCard * 0.72f
                val passo = largCard * 0.85f
                val xBase = (larguraDp - largCard) / 2f
                val yBase = (alturaDp - altCard) / 2f
                itens.indices.sortedByDescending { abs(it - meio) }.forEach { i ->
                    val d = abs(i - meio)
                    val inicio = d * 0.07f
                    val escalaFinal = (1.15f - 0.09f * d).coerceAtLeast(0.62f)
                    val alphaFinal = (1f - 0.1f * d).coerceAtLeast(0.4f)
                    CapaDeAbertura(
                        fonte = itens[i],
                        modifier = Modifier
                            .offset((xBase + passo * (i - meio)).dp, yBase.dp)
                            .size(largCard.dp, altCard.dp)
                            .graphicsLayer {
                                val t = ((progresso.value - inicio) / 0.45f).coerceIn(0f, 1f)
                                val e = easeOutBack(t)
                                translationX = (1f - e) * larguraPx * 0.9f
                                alpha = t * alphaFinal
                                val s = escalaFinal * (0.85f + 0.15f * t)
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val p = progresso.value
                    alpha = if (simples) 1f else (1f - (p - 0.25f) / 0.3f).coerceIn(0f, 1f)
                    val s = if (simples) 1f else 0.9f + 0.1f * (p / 0.3f).coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                }
        ) { logo() }
    }
}

@Composable
private fun CapaDeAbertura(fonte: Any, modifier: Modifier) {
    val context = LocalContext.current
    val pedido = remember(fonte) {
        ImageRequest.Builder(context)
            .data(fonte)
            .size(Size(240, 336))
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = pedido,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
    )
}
