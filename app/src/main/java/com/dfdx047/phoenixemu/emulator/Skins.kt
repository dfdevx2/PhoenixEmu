package com.dfdx047.phoenixemu.emulator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dfdx047.phoenixemu.data.ControleNaTela

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
        opacidade: Float
    )
}

object SkinClassicaSnes : SkinDeControles {
    override val id = "classico_snes"
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
        opacidade: Float
    ) {
        with(escopoCanvas) {
            val alpha = opacidade
            val corFundo = if (pressionado) corAcento.copy(alpha = alpha) else corBase.copy(alpha = alpha)
            val corTexto = (if (pressionado) corBase else corAcento).copy(alpha = alpha)
            
            if (controle.tipo == com.dfdx047.phoenixemu.data.TipoControleNaTela.DPAD) {
                // Desenhar cruz
                val larguraBraco = raioOuTamanho * 0.7f
                val comprimentoBraco = raioOuTamanho * 2.2f
                
                // Horizontal
                drawRect(
                    color = corFundo,
                    topLeft = Offset(centro.x - comprimentoBraco/2, centro.y - larguraBraco/2),
                    size = Size(comprimentoBraco, larguraBraco)
                )
                // Vertical
                drawRect(
                    color = corFundo,
                    topLeft = Offset(centro.x - larguraBraco/2, centro.y - comprimentoBraco/2),
                    size = Size(larguraBraco, comprimentoBraco)
                )
                
            } else {
                if (controle.formato == "PILULA") {
                    drawRoundRect(
                        color = corFundo,
                        topLeft = Offset(centro.x - raioOuTamanho * 1.5f, centro.y - raioOuTamanho * 0.5f),
                        size = Size(raioOuTamanho * 3f, raioOuTamanho),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(raioOuTamanho * 0.5f)
                    )
                } else {
                    drawCircle(
                        color = corFundo,
                        radius = raioOuTamanho,
                        center = centro
                    )
                }
            }
        }
    }
}
