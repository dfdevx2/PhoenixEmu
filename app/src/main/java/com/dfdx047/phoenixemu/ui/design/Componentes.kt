package com.dfdx047.phoenixemu.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// =====================================================================
// SUPERFICIE BASE
// =====================================================================

/**
 * O container de vidro. Todo resto desta tela e feito dele.
 *
 * Caminho com desfoque (API 31+): sampleia a camada do fundo deslocada para a
 * posicao desta superficie e envolve esse desenho numa segunda camada que
 * carrega o BlurEffect.
 *
 * Caminho de scrim (API < 31 ou "Reduzir efeitos"): tinta mais opaca +
 * gradiente de brilho no topo + borda em gradiente. Nao e um degrade
 * envergonhado: e um visual proprio, e no Snapdragon 665 e o unico honesto.
 */
@Composable
fun SuperficieDeVidro(
    modifier: Modifier = Modifier,
    forma: Shape = CircleShape,
    forte: Boolean = false,
    /**
     * Desfoque ao vivo custa uma gravacao + um blur POR SUPERFICIE, POR
     * QUADRO. Nas pilulas flutuantes isso e constante e barato. Numa grade de
     * cartoes seria proporcional aos itens visiveis, e a rolagem morreria
     * exatamente na biblioteca grande que este app existe para aguentar.
     * Cartao passa `false` e fica no scrim -- que, sendo derivado do mesmo
     * estilo, continua parecendo o mesmo material.
     */
    desfocar: Boolean = true,
    estilo: EstiloDeVidro = LocalVidro.current,
    conteudo: @Composable BoxScope.() -> Unit
) {
    val fundo = LocalFundo.current
    // A escolha e estavel por ponto de chamada, entao compor caminhos
    // diferentes aqui e seguro -- e evita alocar uma GraphicsLayer (um
    // RenderNode de verdade) para cada cartao que entra e sai da tela.
    // LocalContentColor tem PRETO como padrao no Material 3. Quem o define e
    // Surface/Card, e esta superficie e um Box -- entao todo Text aqui dentro
    // sem `color =` explicito saia preto. Em tema escuro sem wallpaper isso
    // virava texto invisivel; com wallpaper claro, passava despercebido.
    // Fornecer a cor aqui conserta a tela inteira de uma vez.
    CompositionLocalProvider(LocalContentColor provides estilo.corDoConteudo) {
        if (desfocar && estilo.desfoqueReal && fundo != null) {
            VidroComDesfoque(modifier, forma, forte, estilo, fundo, conteudo)
        } else {
            VidroSimples(modifier, forma, forte, estilo, conteudo)
        }
    }
}

@Composable
private fun VidroComDesfoque(
    modifier: Modifier,
    forma: Shape,
    forte: Boolean,
    estilo: EstiloDeVidro,
    fundo: EstadoDeFundo,
    conteudo: @Composable BoxScope.() -> Unit
) {
    val camadaDoVidro = rememberGraphicsLayer()
    var minhaPosicao by remember { mutableStateOf(Offset.Zero) }
    val raioPx = with(LocalDensity.current) { estilo.raioDeDesfoque.toPx() }
    val tinta = if (forte) estilo.tintaForte else estilo.tinta

    Box(
        modifier = modifier
            .onGloballyPositioned { minhaPosicao = it.positionInWindow() }
            .shadow(estilo.sombra, forma)
            .clip(forma)
            .drawBehind {
                if (fundo.gravado) {
                    camadaDoVidro.renderEffect = efeitoDeDesfoque(raioPx)
                    camadaDoVidro.record(
                        IntSize(size.width.roundToInt(), size.height.roundToInt())
                    ) {
                        // Desloca a camada do fundo para que a regiao correta
                        // dele caia exatamente sob esta superficie.
                        val d = fundo.posicao - minhaPosicao
                        translate(d.x, d.y) { drawLayer(fundo.camada) }
                    }
                    drawLayer(camadaDoVidro)
                }
                drawRect(tinta)
                drawRect(
                    brush = estilo.pincelDoBrilho,
                    size = Size(size.width, size.height * 0.55f)
                )
            }
            .border(estilo.larguraDaBorda, estilo.pincelDaBorda, forma),
        content = conteudo
    )
}

@Composable
private fun VidroSimples(
    modifier: Modifier,
    forma: Shape,
    forte: Boolean,
    estilo: EstiloDeVidro,
    conteudo: @Composable BoxScope.() -> Unit
) {
    val tinta = if (forte) estilo.tintaForte else estilo.tinta
    Box(
        modifier = modifier
            .shadow(estilo.sombra, forma)
            .clip(forma)
            .drawBehind {
                drawRect(tinta)
                drawRect(
                    brush = estilo.pincelDoBrilho,
                    size = Size(size.width, size.height * 0.55f)
                )
            }
            .border(estilo.larguraDaBorda, estilo.pincelDaBorda, forma),
        content = conteudo
    )
}

/**
 * Halo em volta do item selecionado ou em foco.
 *
 * Desenhado ATRAS do conteudo, com gradiente radial que morre antes da borda
 * do retangulo -- assim o brilho parece emanar da capa em vez de ser uma
 * moldura colada nela.
 */
fun Modifier.halo(
    intensidade: Float,
    cor: Color
): Modifier = this.drawBehind {
    if (intensidade <= 0.01f) return@drawBehind
    val raio = size.maxDimension * 0.75f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cor.copy(alpha = 0.55f * intensidade),
                cor.copy(alpha = 0.18f * intensidade),
                Color.Transparent
            ),
            center = center,
            radius = raio
        ),
        radius = raio,
        center = center
    )
}

// =====================================================================
// FOCO PARA CONTROLE / D-PAD
// =====================================================================

/**
 * Num handheld (Odin, Retroid) a interface inteira precisa ser navegavel sem
 * tocar na tela. `clickable` ja torna o elemento focavel; o que falta e o
 * foco ser VISIVEL. Adaptar isso depois custa muito mais do que nascer com.
 */
@Composable
private fun Modifier.anelDeFoco(
    focado: Boolean,
    forma: Shape,
    estilo: EstiloDeVidro
): Modifier {
    val cor by animateColorAsState(
        targetValue = if (focado) estilo.corDoFoco else Color.Transparent,
        label = "anelDeFoco"
    )
    return this.border(2.dp, SolidColor(cor), forma)
}

// =====================================================================
// NAVEGACAO INFERIOR
// =====================================================================

@Immutable
data class ItemDeNavegacao(
    val icone: ImageVector,
    val rotulo: String
)

@Composable
fun PilulaDeNavegacao(
    itens: List<ItemDeNavegacao>,
    indiceSelecionado: Int,
    onSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloDeVidro = LocalVidro.current
) {
    SuperficieDeVidro(modifier = modifier, forma = CircleShape, forte = true, estilo = estilo) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itens.forEachIndexed { indice, item ->
                ItemDaPilula(
                    item = item,
                    selecionado = indice == indiceSelecionado,
                    estilo = estilo,
                    onClick = { onSelecionar(indice) }
                )
            }
        }
    }
}

@Composable
private fun ItemDaPilula(
    item: ItemDeNavegacao,
    selecionado: Boolean,
    estilo: EstiloDeVidro,
    onClick: () -> Unit
) {
    var focado by remember { mutableStateOf(false) }
    val corDoConteudo by animateColorAsState(
        targetValue = if (selecionado) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            estilo.corDoConteudo.copy(alpha = 0.75f)
        },
        label = "corDoItem"
    )
    val fundoSelecionado by animateColorAsState(
        targetValue = if (selecionado) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        } else {
            Color.Transparent
        },
        label = "fundoDoItem"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(fundoSelecionado)
            .anelDeFoco(focado, CircleShape, estilo)
            .onFocusChanged { focado = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icone, contentDescription = item.rotulo, tint = corDoConteudo, modifier = Modifier.size(20.dp))
        // O rotulo so aparece no item ativo: mantem a pilula curta e faz a
        // selecao "respirar" ao trocar de secao.
        AnimatedVisibility(
            visible = selecionado,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    item.rotulo,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = corDoConteudo,
                    maxLines = 1
                )
            }
        }
    }
}

// =====================================================================
// SELETOR SEGMENTADO (substitui as abas + pager aninhado)
// =====================================================================

@Composable
fun SeletorSegmentado(
    opcoes: List<String>,
    indiceSelecionado: Int,
    onSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloDeVidro = LocalVidro.current
) {
    SuperficieDeVidro(modifier = modifier, forma = CircleShape, estilo = estilo) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            opcoes.forEachIndexed { indice, texto ->
                SegmentoUnico(
                    texto = texto,
                    selecionado = indice == indiceSelecionado,
                    estilo = estilo,
                    onClick = { onSelecionar(indice) }
                )
            }
        }
    }
}

@Composable
private fun SegmentoUnico(
    texto: String,
    selecionado: Boolean,
    estilo: EstiloDeVidro,
    onClick: () -> Unit
) {
    var focado by remember { mutableStateOf(false) }
    val fundo by animateColorAsState(
        targetValue = if (selecionado) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
        } else {
            Color.Transparent
        },
        label = "fundoDoSegmento"
    )
    val cor by animateColorAsState(
        targetValue = if (selecionado) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            estilo.corDoConteudo.copy(alpha = 0.8f)
        },
        label = "corDoSegmento"
    )

    Text(
        text = texto,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
        color = cor,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(fundo)
            .anelDeFoco(focado, CircleShape, estilo)
            .onFocusChanged { focado = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

// =====================================================================
// CHIP
// =====================================================================

@Composable
fun ChipDeVidro(
    texto: String,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    estilo: EstiloDeVidro = LocalVidro.current
) {
    var focado by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(if (focado) 1.04f else 1f, label = "escalaDoChip")

    SuperficieDeVidro(
        modifier = modifier
            .scale(escala)
            .anelDeFoco(focado, CircleShape, estilo)
            .onFocusChanged { focado = it.isFocused }
            .clickable(onClick = onClick),
        forma = CircleShape,
        estilo = estilo
    ) {
        Row(
            modifier = Modifier
                .background(
                    if (selecionado) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cor = if (selecionado) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                estilo.corDoConteudo.copy(alpha = 0.85f)
            }
            if (icone != null) {
                Icon(icone, null, tint = cor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                texto,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = cor,
                maxLines = 1
            )
        }
    }
}

// =====================================================================
// BOTAO PILULA ESTENDIDO (o FAB)
// =====================================================================

@Composable
fun BotaoPilula(
    icone: ImageVector,
    texto: String,
    expandido: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloDeVidro = LocalVidro.current
) {
    var focado by remember { mutableStateOf(false) }

    SuperficieDeVidro(
        modifier = modifier
            .anelDeFoco(focado, CircleShape, estilo)
            .onFocusChanged { focado = it.isFocused }
            .clickable(onClick = onClick),
        forma = CircleShape,
        forte = true,
        estilo = estilo
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icone,
                contentDescription = texto,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            AnimatedVisibility(
                visible = expandido,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        texto,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =====================================================================
// BUSCA
// =====================================================================

@Composable
fun BarraDeBusca(
    texto: String,
    onTexto: (String) -> Unit,
    dica: String,
    modifier: Modifier = Modifier,
    iconeInicial: ImageVector? = null,
    acaoFinal: @Composable (() -> Unit)? = null,
    estilo: EstiloDeVidro = LocalVidro.current
) {
    var focado by remember { mutableStateOf(false) }

    SuperficieDeVidro(
        modifier = modifier.anelDeFoco(focado, CircleShape, estilo),
        forma = CircleShape,
        forte = true,
        estilo = estilo
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconeInicial != null) {
                Icon(
                    iconeInicial,
                    null,
                    tint = estilo.corDoConteudo.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                if (texto.isEmpty()) {
                    Text(
                        dica,
                        style = MaterialTheme.typography.bodyMedium,
                        color = estilo.corDoConteudo.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
                CompositionLocalProvider(LocalContentColor provides estilo.corDoConteudo) {
                    BasicTextField(
                        value = texto,
                        onValueChange = onTexto,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(
                            MaterialTheme.typography.bodyMedium
                        ).copy(color = estilo.corDoConteudo),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focado = it.isFocused }
                    )
                }
            }
            if (acaoFinal != null) {
                Spacer(Modifier.width(8.dp))
                acaoFinal()
            }
        }
    }
}

// =====================================================================
// CARTAO (telas secundarias)
// =====================================================================

@Composable
fun CartaoDeVidro(
    modifier: Modifier = Modifier,
    desfocar: Boolean = false,
    estilo: EstiloDeVidro = LocalVidro.current,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    SuperficieDeVidro(
        modifier = modifier.fillMaxWidth(),
        forma = RoundedCornerShape(22.dp),
        desfocar = desfocar,
        estilo = estilo
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = conteudo
        )
    }
}

/** Espacador com a altura da pilula de navegacao, para a lista nao terminar sob ela. */
@Composable
fun EspacoDaNavegacao() {
    Spacer(Modifier.height(96.dp))
}
