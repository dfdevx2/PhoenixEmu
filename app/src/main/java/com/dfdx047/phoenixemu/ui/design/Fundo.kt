package com.dfdx047.phoenixemu.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * Desfoque do que esta ATRAS (backdrop blur).
 *
 * Por que isto precisou ser escrito a mao:
 *
 * `Modifier.blur` do Compose desfoca o proprio conteudo do composable, nao o
 * que esta atras dele. Aplicado numa pilula, ele borra o texto da pilula e
 * deixa o fundo nitido -- o oposto de vidro fosco. O Android tambem nao
 * oferece backdrop blur por view: `setRenderEffect` sofre do mesmo problema, e
 * o blur de janela (API 31) so vale para a janela inteira.
 *
 * A tecnica que funciona, e a que bibliotecas como a Haze usam, e:
 *
 *  1. gravar o conteudo do fundo numa GraphicsLayer;
 *  2. dentro da pilula, desenhar essa mesma camada deslocada, de modo que a
 *     regiao correta do fundo apareca sob ela;
 *  3. aplicar o desfoque numa segunda camada, que envolve esse desenho.
 *
 * Optei por escrever em vez de usar a Haze por um motivo pratico: as APIs
 * aqui (GraphicsLayer, BlurEffect) sao estaveis e eu tenho como raciocinar
 * sobre elas; a superficie publica da Haze mudou bastante entre versoes e eu
 * nao teria como conferir qual esta valendo. Se voce preferir a Haze depois,
 * a troca e local: so este arquivo e o `SuperficieDeVidro` sabem como o
 * desfoque acontece.
 *
 * IMPORTANTE: quem usa `fonteDeFundo` nunca pode conter uma superficie de
 * vidro. A pilula sampleia o fundo; se ela estivesse dentro do fundo,
 * sampearia a si mesma e viraria realimentacao.
 */
@Stable
class EstadoDeFundo internal constructor(
    internal val camada: GraphicsLayer
) {
    internal var posicao by mutableStateOf(Offset.Zero)
    internal var gravado by mutableStateOf(false)
}

@Composable
fun lembrarEstadoDeFundo(): EstadoDeFundo {
    val camada = rememberGraphicsLayer()
    return remember(camada) { EstadoDeFundo(camada) }
}

/** null quando a tela nao tem fundo capturavel; o vidro cai no scrim. */
val LocalFundo = compositionLocalOf<EstadoDeFundo?> { null }

/**
 * Marca o conteudo que serve de fundo para o vidro.
 *
 * O conteudo e gravado numa camada e desenhado a partir dela. O custo e uma
 * gravacao por quadro do que esta dentro -- e por isso que a lista inteira
 * entra aqui, mas as pilulas nao.
 */
fun Modifier.fonteDeFundo(estado: EstadoDeFundo?, ativo: Boolean = true): Modifier {
    // Sem desfoque ao vivo (API < 31 ou "Reduzir efeitos") nao ha razao para
    // gravar nada: o custo cairia em cheio justo no aparelho mais fraco.
    if (estado == null || !ativo) return this
    return this
        .onGloballyPositioned { estado.posicao = it.positionInWindow() }
        .drawWithContent {
            estado.camada.record { this@drawWithContent.drawContent() }
            drawLayer(estado.camada)
            // mutableStateOf so invalida quando o valor muda, entao isto nao
            // recompoe a cada quadro.
            estado.gravado = true
        }
}

/**
 * Fundo do app quando NAO ha wallpaper.
 *
 * Antes era uma cor chapada, e isso criava dois problemas de uma vez: o vidro
 * nao tinha nada para desfocar (virava um retangulo translucido sobre cor
 * lisa) e a primeira abertura do app -- quando ninguem escolheu wallpaper
 * ainda -- era a tela mais feia do aplicativo.
 *
 * Agora sao dois halos suaves em primary e tertiary sobre a cor de fundo do
 * tema. E discreto o bastante para nao competir com as capas, e da ao vidro
 * variacao de luminosidade suficiente para o desfoque aparecer.
 */
@Composable
fun FundoDoTema(modifier: Modifier = Modifier) {
    val esquema = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(esquema.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            esquema.primary.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.08f),
                        radius = size.maxDimension * 0.75f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            esquema.tertiary.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.95f),
                        radius = size.maxDimension * 0.65f
                    )
                )
            }
    )
}

/**
 * Constroi o efeito de desfoque para a camada do vidro.
 *
 * TileMode.Clamp e deliberado: com Decal, as bordas da pilula puxam
 * transparencia de fora da regiao amostrada e o vidro fica com halo escuro
 * nas quinas.
 */
internal fun efeitoDeDesfoque(raioPx: Float): BlurEffect =
    BlurEffect(raioPx, raioPx, TileMode.Clamp)
