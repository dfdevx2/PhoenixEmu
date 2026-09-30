package com.dfdx047.phoenixemu.data

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
// Apelidado de proposito: uma das constantes deste enum se chama `R` (o botao
// de ombro direito), e dentro do corpo do enum esse nome VENCE a classe de
// recursos. `R.string.botao_l` ali dentro resolve para a constante, nao para os
// recursos, e o compilador reclama que a entrada ainda nao foi inicializada.
import com.dfdx047.phoenixemu.R as Recursos

/**
 * Os botoes que o emulador entende.
 *
 * Sao os do SNES, que contem os do NES -- entao um mapa so serve aos dois
 * sistemas, e o NES simplesmente ignora X, Y, L e R.
 *
 * A ordem aqui e a ordem em que aparecem na tela de configuracao, e tambem a
 * ordem dos bits que a Fase 4 vai enviar ao nucleo (veja
 * NucleoLibretro.Botao).
 */
enum class BotaoVirtual(@StringRes val rotulo: Int, val padrao: Int) {
    CIMA(Recursos.string.botao_cima, KeyEvent.KEYCODE_DPAD_UP),
    BAIXO(Recursos.string.botao_baixo, KeyEvent.KEYCODE_DPAD_DOWN),
    ESQUERDA(Recursos.string.botao_esquerda, KeyEvent.KEYCODE_DPAD_LEFT),
    DIREITA(Recursos.string.botao_direita, KeyEvent.KEYCODE_DPAD_RIGHT),
    A(Recursos.string.botao_a, KeyEvent.KEYCODE_BUTTON_B),
    B(Recursos.string.botao_b, KeyEvent.KEYCODE_BUTTON_A),
    X(Recursos.string.botao_x, KeyEvent.KEYCODE_BUTTON_Y),
    Y(Recursos.string.botao_y, KeyEvent.KEYCODE_BUTTON_X),
    L(Recursos.string.botao_l, KeyEvent.KEYCODE_BUTTON_L1),
    R(Recursos.string.botao_r, KeyEvent.KEYCODE_BUTTON_R1),
    SELECT(Recursos.string.botao_select, KeyEvent.KEYCODE_BUTTON_SELECT),
    START(Recursos.string.botao_start, KeyEvent.KEYCODE_BUTTON_START);

    companion object {
        /**
         * O padrao de A/B e X/Y esta TROCADO de proposito em relacao ao nome
         * do botao fisico.
         *
         * O A do SNES fica a direita, na mesma posicao fisica do B de um
         * controle de Xbox. Mapear "A do SNES" para "A do Xbox" poe o botao
         * de pulo no lugar errado, e todo mundo percebe no primeiro jogo.
         * Este e o mesmo padrao que o RetroArch usa.
         */
        fun padrao(): Map<BotaoVirtual, Int> = entries.associateWith { it.padrao }
    }
}

/** Posicao e tamanho de um botao do controle na tela, em fracao da area util. */
@Immutable
data class BotaoNaTela(
    val botao: BotaoVirtual,
    /** 0..1 a partir da esquerda. */
    val x: Float,
    /** 0..1 a partir do topo. */
    val y: Float
)

@Immutable
data class ConfigDoOverlay(
    val visivel: Boolean = true,
    val opacidade: Float = 0.55f,
    val escala: Float = 1f,
    val posicoes: List<BotaoNaTela> = padrao()
) {
    companion object {
        /**
         * Layout inicial: direcional a esquerda, botoes de acao a direita,
         * ombros no topo e Start/Select no centro inferior. E o arranjo que
         * todo emulador de handheld usa, e ele existe porque funciona com os
         * dois polegares em repouso.
         */
        fun padrao(): List<BotaoNaTela> = listOf(
            BotaoNaTela(BotaoVirtual.CIMA, 0.12f, 0.58f),
            BotaoNaTela(BotaoVirtual.BAIXO, 0.12f, 0.86f),
            BotaoNaTela(BotaoVirtual.ESQUERDA, 0.04f, 0.72f),
            BotaoNaTela(BotaoVirtual.DIREITA, 0.20f, 0.72f),
            BotaoNaTela(BotaoVirtual.A, 0.94f, 0.72f),
            BotaoNaTela(BotaoVirtual.B, 0.86f, 0.86f),
            BotaoNaTela(BotaoVirtual.X, 0.86f, 0.58f),
            BotaoNaTela(BotaoVirtual.Y, 0.78f, 0.72f),
            BotaoNaTela(BotaoVirtual.L, 0.06f, 0.22f),
            BotaoNaTela(BotaoVirtual.R, 0.94f, 0.22f),
            BotaoNaTela(BotaoVirtual.SELECT, 0.42f, 0.90f),
            BotaoNaTela(BotaoVirtual.START, 0.58f, 0.90f)
        )
    }
}

/**
 * Nome legivel de um codigo de tecla.
 *
 * `KeyEvent.keyCodeToString` devolve coisas como "KEYCODE_BUTTON_L1", que
 * nao serve para mostrar a alguem. Aqui encurtamos para o que a pessoa ve
 * impresso no controle.
 */
fun nomeDaTecla(codigo: Int): String = when (codigo) {
    KeyEvent.KEYCODE_DPAD_UP -> "D-pad ↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "D-pad ↓"
    KeyEvent.KEYCODE_DPAD_LEFT -> "D-pad ←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "D-pad →"
    KeyEvent.KEYCODE_BUTTON_A -> "A"
    KeyEvent.KEYCODE_BUTTON_B -> "B"
    KeyEvent.KEYCODE_BUTTON_X -> "X"
    KeyEvent.KEYCODE_BUTTON_Y -> "Y"
    KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    KeyEvent.KEYCODE_BUTTON_START -> "Start"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
    KeyEvent.KEYCODE_BUTTON_MODE -> "Home"
    else -> KeyEvent.keyCodeToString(codigo).removePrefix("KEYCODE_").replace('_', ' ')
}
