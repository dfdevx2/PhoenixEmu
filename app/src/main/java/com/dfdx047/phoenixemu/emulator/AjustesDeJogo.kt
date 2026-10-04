package com.dfdx047.phoenixemu.emulator

enum class EscalaImagem { AJUSTAR, X1, X2, X3 }
enum class ProporcaoImagem { AUTOMATICA, PIXELS_QUADRADOS, RATIO_4_3, RATIO_16_9, ESTICAR }

data class AjustesDeJogo(
    val escala: EscalaImagem = EscalaImagem.AJUSTAR,
    val proporcao: ProporcaoImagem = ProporcaoImagem.AUTOMATICA,
    val mostrarFps: Boolean = false,
    val volume: Float = 1.0f,
    val mudo: Boolean = false,
    val velocidadeFF: Int = 2,
    val overrides: Set<String> = emptySet(),
    val idDoJogo: String = ""
) {
    fun isSomenteEsteJogo(): Boolean = overrides.isNotEmpty()

    companion object {
        const val CHAVE_ESCALA = "escala"
        const val CHAVE_PROPORCAO = "proporcao"
        const val CHAVE_MOSTRAR_FPS = "mostrarFps"
        const val CHAVE_VOLUME = "volume"
        const val CHAVE_MUDO = "mudo"
        const val CHAVE_VELOCIDADE_FF = "velocidadeFF"
    }
}
