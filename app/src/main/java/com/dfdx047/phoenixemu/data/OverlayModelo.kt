package com.dfdx047.phoenixemu.data

import androidx.compose.runtime.Immutable

enum class ModoVisibilidadeOverlay {
    SEMPRE, AUTO_ESCONDER_COM_CONTROLE, NUNCA
}

enum class TipoControleNaTela {
    BOTAO, DPAD
}

enum class ModoToqueControle {
    SEGURAR, ALTERNAR
}

@Immutable
data class AcaoDoControle(
    val botaoVirtual: BotaoVirtual? = null
)

@Immutable
data class ControleNaTela(
    val id: String,
    val tipo: TipoControleNaTela,
    val x: Float,
    val y: Float,
    val tamanhoBase: Float = 1f,
    val formato: String = "CIRCULO",
    val rotulo: String? = null,
    val acao: AcaoDoControle? = null,
    val visivel: Boolean = true,
    val modoToque: ModoToqueControle = ModoToqueControle.SEGURAR
)

@Immutable
data class OverlayConfigNova(
    val versao: Int = 1,
    val visivelModo: ModoVisibilidadeOverlay = ModoVisibilidadeOverlay.SEMPRE,
    val opacidadeOciosa: Float = 0.55f,
    val opacidadePressionado: Float = 0.8f,
    val escalaGlobal: Float = 1f,
    val margemSeguraDp: Float = 16f,
    val skinId: String = "classico_snes",
    val hapticoAtivo: Boolean = true,
    val hapticoIntensidade: Int = 1,
    val mostrarRotulos: Boolean = true,
    val mostrarAreaDeToque: Boolean = false,
    val zonaMortaDpad: Float = 0.2f,
    val dpadDiagonal: Boolean = true,
    val dpadDeslizar: Boolean = true,
    val ocultarNoMenu: Boolean = true,
    val controles: List<ControleNaTela> = emptyList()
) {
    companion object {
        fun layoutNes(): List<ControleNaTela> = listOf(
            ControleNaTela("dpad", TipoControleNaTela.DPAD, 0.12f, 0.72f),
            ControleNaTela("b", TipoControleNaTela.BOTAO, 0.78f, 0.72f, acao = AcaoDoControle(BotaoVirtual.B)),
            ControleNaTela("a", TipoControleNaTela.BOTAO, 0.88f, 0.72f, acao = AcaoDoControle(BotaoVirtual.A)),
            ControleNaTela("select", TipoControleNaTela.BOTAO, 0.42f, 0.90f, formato = "PILULA", rotulo = "SELECT", acao = AcaoDoControle(BotaoVirtual.SELECT)),
            ControleNaTela("start", TipoControleNaTela.BOTAO, 0.58f, 0.90f, formato = "PILULA", rotulo = "START", acao = AcaoDoControle(BotaoVirtual.START))
        )
        
        fun layoutSnes(): List<ControleNaTela> = listOf(
            ControleNaTela("dpad", TipoControleNaTela.DPAD, 0.12f, 0.72f),
            ControleNaTela("y", TipoControleNaTela.BOTAO, 0.76f, 0.72f, acao = AcaoDoControle(BotaoVirtual.Y)),
            ControleNaTela("x", TipoControleNaTela.BOTAO, 0.84f, 0.58f, acao = AcaoDoControle(BotaoVirtual.X)),
            ControleNaTela("b", TipoControleNaTela.BOTAO, 0.84f, 0.86f, acao = AcaoDoControle(BotaoVirtual.B)),
            ControleNaTela("a", TipoControleNaTela.BOTAO, 0.92f, 0.72f, acao = AcaoDoControle(BotaoVirtual.A)),
            ControleNaTela("l", TipoControleNaTela.BOTAO, 0.12f, 0.22f, formato = "PILULA", rotulo = "L", acao = AcaoDoControle(BotaoVirtual.L)),
            ControleNaTela("r", TipoControleNaTela.BOTAO, 0.88f, 0.22f, formato = "PILULA", rotulo = "R", acao = AcaoDoControle(BotaoVirtual.R)),
            ControleNaTela("select", TipoControleNaTela.BOTAO, 0.42f, 0.90f, formato = "PILULA", rotulo = "SELECT", acao = AcaoDoControle(BotaoVirtual.SELECT)),
            ControleNaTela("start", TipoControleNaTela.BOTAO, 0.58f, 0.90f, formato = "PILULA", rotulo = "START", acao = AcaoDoControle(BotaoVirtual.START))
        )
        
        fun layoutModerno(): List<ControleNaTela> = layoutSnes() // Adaptaremos visualmente na skin
        
        fun converterAntigo(antigo: ConfigDoOverlay): OverlayConfigNova {
            val vis = if (antigo.visivel) ModoVisibilidadeOverlay.SEMPRE else ModoVisibilidadeOverlay.NUNCA
            return OverlayConfigNova(
                versao = 1,
                visivelModo = vis,
                opacidadeOciosa = antigo.opacidade,
                escalaGlobal = antigo.escala,
                controles = layoutSnes()
            )
        }
    }
}
