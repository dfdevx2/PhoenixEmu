package com.dfdx047.phoenixemu.emulator

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.dfdx047.phoenixemu.data.ControleNaTela
import com.dfdx047.phoenixemu.data.TipoControleNaTela
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

interface SkinDeControles {
    val id: String
    val nome: String
    val autor: String
    
    fun desenharControle(
        escopoCanvas: DrawScope,
        controle: ControleNaTela,
        centro: Offset,
        raioOuTamanho: Float,
        pressionado: Boolean,
        corBase: Color,
        corAcento: Color,
        opacidade: Float,
        mask: Int,
        mostrarRotulos: Boolean,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    )
}

fun desenharRotulo(
    escopoCanvas: DrawScope,
    controle: ControleNaTela,
    centro: Offset,
    raioOuTamanho: Float,
    corTexto: Color,
    mostrarRotulos: Boolean,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    opacidade: Float
) {
    if (!mostrarRotulos) return
    val rotulo = controle.rotulo ?: controle.acao?.botaoVirtual?.name ?: return
    val textToDraw = if (rotulo.length > 2) rotulo.take(3).uppercase() else rotulo.uppercase()
    val textLayoutResult = textMeasurer.measure(textToDraw, textStyle)
    val textOffset = Offset(
        centro.x - textLayoutResult.size.width / 2f,
        centro.y - textLayoutResult.size.height / 2f
    )
    escopoCanvas.drawText(
        textMeasurer = textMeasurer,
        text = textToDraw,
        style = textStyle.copy(color = corTexto.copy(alpha = kotlin.math.max(opacidade, 0.6f))),
        topLeft = textOffset
    )
}

object SkinClassica8Bit : SkinDeControles {
    override val id = "classico_8bit"
    override val nome = "Clássico 8-bit"
    override val autor = "Phoenix"

    override fun desenharControle(
        escopoCanvas: DrawScope,
        controle: ControleNaTela,
        centro: Offset,
        raioOuTamanho: Float,
        pressionado: Boolean,
        corBase: Color,
        corAcento: Color,
        opacidade: Float,
        mask: Int,
        mostrarRotulos: Boolean,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        val alpha = opacidade
        val corFundo = (if (pressionado) corAcento else corBase).copy(alpha = alpha)
        val contorno = corBase.copy(alpha = alpha + 0.2f)
        val escala = if (pressionado) 0.94f else 1f
        val corTexto = (if (pressionado) corBase else corAcento).copy(alpha = alpha + 0.4f)

        with(escopoCanvas) {
            if (controle.tipo == TipoControleNaTela.DPAD) {
                val bw = raioOuTamanho * 0.7f * escala
                val bh = raioOuTamanho * 2.2f * escala
                
                drawRoundRect(color = corFundo, topLeft = Offset(centro.x - bh/2, centro.y - bw/2), size = Size(bh, bw), cornerRadius = CornerRadius(4.dp.toPx()))
                drawRoundRect(color = corFundo, topLeft = Offset(centro.x - bw/2, centro.y - bh/2), size = Size(bw, bh), cornerRadius = CornerRadius(4.dp.toPx()))
                drawCircle(color = contorno, radius = bw * 0.3f, center = centro)
            } else if (controle.formato == "PILULA") {
                val rw = raioOuTamanho * 2f * escala
                val rh = raioOuTamanho * 0.7f * escala
                drawRoundRect(color = corFundo, topLeft = Offset(centro.x - rw, centro.y - rh), size = Size(rw * 2, rh * 2), cornerRadius = CornerRadius(rh))
                drawRoundRect(color = contorno, topLeft = Offset(centro.x - rw, centro.y - rh), size = Size(rw * 2, rh * 2), cornerRadius = CornerRadius(rh), style = Stroke(1.5.dp.toPx()))
                desenharRotulo(this, controle, centro, raioOuTamanho, corTexto, mostrarRotulos, textMeasurer, textStyle, opacidade)
            } else {
                drawCircle(color = corFundo, radius = raioOuTamanho * escala, center = centro)
                drawCircle(color = contorno, radius = raioOuTamanho * escala, center = centro, style = Stroke(1.5.dp.toPx()))
                desenharRotulo(this, controle, centro, raioOuTamanho, corTexto, mostrarRotulos, textMeasurer, textStyle, opacidade)
            }
        }
    }
}

object SkinClassica16Bit : SkinDeControles {
    override val id = "classico_16bit"
    override val nome = "Clássico 16-bit"
    override val autor = "Phoenix"

    override fun desenharControle(
        escopoCanvas: DrawScope,
        controle: ControleNaTela,
        centro: Offset,
        raioOuTamanho: Float,
        pressionado: Boolean,
        corBase: Color,
        corAcento: Color,
        opacidade: Float,
        mask: Int,
        mostrarRotulos: Boolean,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        val alpha = opacidade
        val corFundo = (if (pressionado) corAcento else corBase).copy(alpha = alpha)
        val contorno = corBase.copy(alpha = alpha + 0.2f)
        val escala = if (pressionado) 0.94f else 1f
        val corTexto = (if (pressionado) corBase else corAcento).copy(alpha = alpha + 0.4f)

        with(escopoCanvas) {
            if (controle.tipo == TipoControleNaTela.DPAD) {
                val bw = raioOuTamanho * 0.7f * escala
                val bh = raioOuTamanho * 2.2f * escala
                drawRect(color = corFundo, topLeft = Offset(centro.x - bh/2, centro.y - bw/2), size = Size(bh, bw))
                drawRect(color = corFundo, topLeft = Offset(centro.x - bw/2, centro.y - bh/2), size = Size(bw, bh))
                drawRect(color = contorno, topLeft = Offset(centro.x - bh/2, centro.y - bw/2), size = Size(bh, bw), style = Stroke(1.5.dp.toPx()))
                drawRect(color = contorno, topLeft = Offset(centro.x - bw/2, centro.y - bh/2), size = Size(bw, bh), style = Stroke(1.5.dp.toPx()))
                drawRect(color = corFundo, topLeft = Offset(centro.x - bw/2 + 2, centro.y - bw/2 + 2), size = Size(bw - 4, bw - 4))
            } else if (controle.formato == "PILULA") {
                val rw = raioOuTamanho * 1.5f * escala
                val rh = raioOuTamanho * 0.7f * escala
                drawRoundRect(color = corFundo, topLeft = Offset(centro.x - rw, centro.y - rh), size = Size(rw * 2, rh * 2), cornerRadius = CornerRadius(rh))
                drawRoundRect(color = contorno, topLeft = Offset(centro.x - rw, centro.y - rh), size = Size(rw * 2, rh * 2), cornerRadius = CornerRadius(rh), style = Stroke(1.5.dp.toPx()))
                desenharRotulo(this, controle, centro, raioOuTamanho, corTexto, mostrarRotulos, textMeasurer, textStyle, opacidade)
            } else {
                // Diamond buttons
                drawCircle(color = corFundo, radius = raioOuTamanho * escala, center = centro)
                drawCircle(color = contorno, radius = raioOuTamanho * escala, center = centro, style = Stroke(1.5.dp.toPx()))
                desenharRotulo(this, controle, centro, raioOuTamanho, corTexto, mostrarRotulos, textMeasurer, textStyle, opacidade)
            }
        }
    }
}

object SkinModerna : SkinDeControles {
    override val id = "moderno"
    override val nome = "Moderno"
    override val autor = "Phoenix"

    override fun desenharControle(
        escopoCanvas: DrawScope,
        controle: ControleNaTela,
        centro: Offset,
        raioOuTamanho: Float,
        pressionado: Boolean,
        corBase: Color,
        corAcento: Color,
        opacidade: Float,
        mask: Int,
        mostrarRotulos: Boolean,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        val alpha = opacidade
        val corFundo = Color.Black.copy(alpha = alpha * 0.6f)
        val contorno = corBase.copy(alpha = alpha + 0.3f)
        val escala = if (pressionado) 0.94f else 1f
        val corTexto = Color.White.copy(alpha = alpha + 0.4f)

        with(escopoCanvas) {
            if (controle.tipo == TipoControleNaTela.DPAD) {
                drawCircle(color = corFundo, radius = raioOuTamanho * 1.2f * escala, center = centro)
                drawCircle(color = contorno, radius = raioOuTamanho * 1.2f * escala, center = centro, style = Stroke(1.5.dp.toPx()))
                // Setas...
            } else if (controle.formato == "PILULA") {
                val rw = raioOuTamanho * 1.5f * escala
                val rh = raioOuTamanho * 0.7f * escala
                drawRoundRect(color = corFundo, topLeft = Offset(centro.x - rw, centro.y - rh), size = Size(rw * 2, rh * 2), cornerRadius = CornerRadius(rh))
                drawRoundRect(color = contorno, topLeft = Offset(centro.x - rw, centro.y - rh), size = Size(rw * 2, rh * 2), cornerRadius = CornerRadius(rh), style = Stroke(1.5.dp.toPx()))
                desenharRotulo(this, controle, centro, raioOuTamanho, corTexto, mostrarRotulos, textMeasurer, textStyle, opacidade)
            } else {
                drawCircle(color = corFundo, radius = raioOuTamanho * escala, center = centro)
                drawCircle(color = contorno, radius = raioOuTamanho * escala, center = centro, style = Stroke(1.5.dp.toPx()))
                desenharRotulo(this, controle, centro, raioOuTamanho, corTexto, mostrarRotulos, textMeasurer, textStyle, opacidade)
            }
            if (pressionado) {
                drawCircle(color = corAcento.copy(alpha = 0.5f), radius = raioOuTamanho * escala, center = centro)
            }
        }
    }
}

object Skins {
    val embutidas: List<SkinDeControles> = listOf(SkinClassica8Bit, SkinClassica16Bit, SkinModerna)

    private var tick by mutableIntStateOf(0)
    @Volatile private var cache: List<SkinDeControles>? = null

    /** Embutidas + instaladas. Le o `tick` pra a UI recompor depois de instalar/remover. */
    val todas: List<SkinDeControles>
        get() {
            if (tick < 0) return embutidas
            return cache ?: embutidas
        }

    /** Carrega as skins instaladas uma vez por processo. Seguro de chamar varias vezes. */
    fun garantir(context: Context) {
        if (cache == null) cache = embutidas + CarregadorDeSkins.carregarInstaladas(context.applicationContext)
    }

    /** Depois de instalar ou remover uma skin. */
    fun recarregar(context: Context) {
        cache = embutidas + CarregadorDeSkins.carregarInstaladas(context.applicationContext)
        tick++
    }

    const val MAX_RAPIDAS = 3

    /** Skins mostradas no menu de pausa: as marcadas (max 3) + a ativa. Sem marcacao, as embutidas. */
    fun doMenuRapido(rapidas: List<String>?, ativaId: String): List<SkinDeControles> {
        val marcadas = rapidas.orEmpty().mapNotNull { id -> todas.firstOrNull { it.id == id } }
        val base = (if (marcadas.isEmpty()) embutidas else marcadas).take(MAX_RAPIDAS)
        val ativa = todas.firstOrNull { it.id == ativaId }
        return if (ativa != null && base.none { it.id == ativa.id }) base + ativa else base
    }

    fun porId(id: String?): SkinDeControles =
        todas.firstOrNull { it.id == id } ?: SkinClassica16Bit
}
