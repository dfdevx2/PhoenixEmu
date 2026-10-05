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

enum class TipoAcaoControle {
    BOTAO, ATALHO, MACRO, COMBO, TOGGLE, TURBO
}

@Immutable
data class AcaoDoControle(
    val tipo: TipoAcaoControle = TipoAcaoControle.BOTAO,
    val botaoVirtual: BotaoVirtual? = null,
    val acaoAtalho: AcaoAtalho? = null,
    val idExtra: String? = null // Para macroId, etc (Fase C)
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
    val modoToque: ModoToqueControle = ModoToqueControle.SEGURAR,
    val opacidade: Float? = null, // Override individual
    val haptico: Boolean? = null, // Override individual
    val turboHz: Int? = null      // Fase C
)

@Immutable
data class OverlayConfigNova(
    val versao: Int = 2,
    val visivelModo: ModoVisibilidadeOverlay = ModoVisibilidadeOverlay.SEMPRE,
    val opacidadeOciosa: Float = 0.55f,
    val opacidadePressionado: Float = 0.8f,
    val escalaGlobal: Float = 1f,
    val margemSeguraDp: Float = 16f,
    val skinId: String = "classico_snes",
    val skinsRapidas: List<String>? = null,
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
            ControleNaTela("dpad", TipoControleNaTela.DPAD, 0.12f, 0.72f, formato = "CRUZ"),
            ControleNaTela("b", TipoControleNaTela.BOTAO, 0.78f, 0.72f, acao = AcaoDoControle(botaoVirtual = BotaoVirtual.B)),
            ControleNaTela("a", TipoControleNaTela.BOTAO, 0.88f, 0.72f, acao = AcaoDoControle(botaoVirtual = BotaoVirtual.A)),
            ControleNaTela("select", TipoControleNaTela.BOTAO, 0.42f, 0.90f, formato = "PILULA", rotulo = "SELECT", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.SELECT)),
            ControleNaTela("start", TipoControleNaTela.BOTAO, 0.58f, 0.90f, formato = "PILULA", rotulo = "START", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.START))
        )
        
        fun layoutSnes(): List<ControleNaTela> = listOf(
            ControleNaTela("dpad", TipoControleNaTela.DPAD, 0.12f, 0.72f, formato = "CRUZ"),
            ControleNaTela("y", TipoControleNaTela.BOTAO, 0.76f, 0.72f, formato = "LOSANGO", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.Y)),
            ControleNaTela("x", TipoControleNaTela.BOTAO, 0.84f, 0.58f, formato = "LOSANGO", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.X)),
            ControleNaTela("b", TipoControleNaTela.BOTAO, 0.84f, 0.86f, formato = "LOSANGO", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.B)),
            ControleNaTela("a", TipoControleNaTela.BOTAO, 0.92f, 0.72f, formato = "LOSANGO", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.A)),
            ControleNaTela("l", TipoControleNaTela.BOTAO, 0.12f, 0.22f, formato = "PILULA", rotulo = "L", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.L)),
            ControleNaTela("r", TipoControleNaTela.BOTAO, 0.88f, 0.22f, formato = "PILULA", rotulo = "R", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.R)),
            ControleNaTela("select", TipoControleNaTela.BOTAO, 0.42f, 0.90f, formato = "PILULA", rotulo = "SELECT", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.SELECT)),
            ControleNaTela("start", TipoControleNaTela.BOTAO, 0.58f, 0.90f, formato = "PILULA", rotulo = "START", acao = AcaoDoControle(botaoVirtual = BotaoVirtual.START))
        )
        
        fun layoutModerno(): List<ControleNaTela> = layoutSnes().map { it.copy(formato = "CIRCULO") }
        
        fun converterAntigo(antigo: ConfigDoOverlay): OverlayConfigNova {
            val vis = if (antigo.visivel) ModoVisibilidadeOverlay.SEMPRE else ModoVisibilidadeOverlay.NUNCA
            return OverlayConfigNova(
                versao = 2,
                visivelModo = vis,
                opacidadeOciosa = antigo.opacidade,
                escalaGlobal = antigo.escala,
                controles = layoutSnes()
            )
        }
    }
}
