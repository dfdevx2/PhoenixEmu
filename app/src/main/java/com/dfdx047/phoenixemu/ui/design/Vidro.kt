package com.dfdx047.phoenixemu.ui.design

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dfdx047.phoenixemu.Acabamento
import com.dfdx047.phoenixemu.TemaApp

/**
 * Parametros do vidro fosco.
 *
 * O ponto central do pedido era "adaptado a todos os temas". Em vez de uma
 * cor fixa de vidro (que ficaria suja no Famicom creme e invisivel no AMOLED),
 * tudo aqui e DERIVADO do ColorScheme ativo. Tema novo que voce criar depois
 * ganha vidro coerente sem tocar neste arquivo -- a unica excecao sao dois
 * temas que precisam de tratamento proprio, explicados abaixo.
 */
@Immutable
data class EstiloDeVidro(
    /** Tinta das superficies comuns (cartoes, chips). */
    val tinta: Color,
    /** Tinta das superficies que precisam de leitura garantida (nav, busca). */
    val tintaForte: Color,
    /** Brilho no topo: e o que faz o olho ler "vidro" e nao "retangulo translucido". */
    val brilhoSuperior: Color,
    /** Borda com gradiente: clara em cima, quase nula embaixo. */
    val bordaSuperior: Color,
    val bordaInferior: Color,
    val larguraDaBorda: Dp,
    val raioDeDesfoque: Dp,
    /** false = cai no scrim em camadas, sem desfoque ao vivo. */
    val desfoqueReal: Boolean,
    val sombra: Dp,
    /** Anel de foco para navegacao por D-pad / controle. */
    val corDoFoco: Color,
    val corDoConteudo: Color
) {
    val pincelDaBorda: Brush
        get() = Brush.verticalGradient(listOf(bordaSuperior, bordaInferior))

    val pincelDoBrilho: Brush
        get() = Brush.verticalGradient(listOf(brilhoSuperior, Color.Transparent))
}

val LocalVidro = staticCompositionLocalOf<EstiloDeVidro> {
    error("LocalVidro nao foi fornecido. Use PhoenixEmuTheme.")
}

/**
 * O desfoque de verdade depende de RenderEffect, que so existe a partir da
 * API 31. No Snapdragon 665 (Android 11 ou anterior) o caminho de scrim e o
 * unico disponivel -- e por isso ele foi feito bonito, nao apenas funcional.
 */
val SUPORTA_DESFOQUE: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun estiloDeVidroPara(
    tema: TemaApp,
    esquema: ColorScheme,
    escuro: Boolean,
    reduzirEfeitos: Boolean,
    acabamento: Acabamento = Acabamento.VIDRO,
    amoled: Boolean = false,
    sombras: Boolean = false
): EstiloDeVidro {
    val resultado = estiloDeVidroImpl(tema, esquema, escuro, reduzirEfeitos, acabamento, amoled)
    return if (sombras) resultado else resultado.copy(sombra = 0.dp)
}

private fun estiloDeVidroImpl(
    tema: TemaApp,
    esquema: ColorScheme,
    escuro: Boolean,
    reduzirEfeitos: Boolean,
    acabamento: Acabamento,
    amoled: Boolean
): EstiloDeVidro {
    // Acabamento solido: mesmas cores, mesmas formas, material diferente.
    // Superficie quase opaca, sem brilho e sem desfoque -- para quem nao gosta
    // do vidro fosco, e para aparelho em que ele custa caro.
    if (acabamento == Acabamento.SOLIDO) {
        val base = if (amoled && escuro) Color.Black else esquema.surface
        val luz = esquema.onSurface
        return EstiloDeVidro(
            tinta = base.copy(alpha = 0.94f),
            tintaForte = base.copy(alpha = 0.99f),
            brilhoSuperior = Color.Transparent,
            bordaSuperior = luz.copy(alpha = if (escuro) 0.16f else 0.10f),
            bordaInferior = luz.copy(alpha = if (escuro) 0.16f else 0.10f),
            larguraDaBorda = 1.dp,
            raioDeDesfoque = 0.dp,
            desfoqueReal = false,
            sombra = if (escuro) 2.dp else 6.dp,
            corDoFoco = esquema.primary,
            corDoConteudo = esquema.onSurface
        )
    }

    val desfoque = SUPORTA_DESFOQUE && !reduzirEfeitos

    // Sem desfoque ao vivo, o vidro precisa de mais opacidade para o texto
    // continuar legivel sobre um wallpaper agitado.
    val reforco = if (desfoque) 0f else 0.16f

    // A base do vidro e a superficie do tema; a "luz" e o onSurface. Assim o
    // vidro herda a temperatura de cor de cada tema: creme no Famicom, cinza
    // gelo no SNES americano, azulado no Super Famicom.
    val base = esquema.surface
    val luz = esquema.onSurface

    // Com o preto absoluto ligado, a superficie do tema NAO serve de base:
    // ela deixaria o vidro cinzento sobre o preto e mataria o proposito. A
    // tinta vira preta e quem desenha a pilula e a borda.
    if (amoled && escuro) {
        return EstiloDeVidro(
            tinta = Color.Black.copy(alpha = 0.42f + reforco),
            tintaForte = Color.Black.copy(alpha = 0.62f + reforco),
            brilhoSuperior = esquema.primary.copy(alpha = 0.10f),
            bordaSuperior = esquema.primary.copy(alpha = 0.45f),
            bordaInferior = esquema.primary.copy(alpha = 0.06f),
            larguraDaBorda = 1.dp,
            raioDeDesfoque = 28.dp,
            desfoqueReal = desfoque,
            sombra = 0.dp, // sombra em preto absoluto nao aparece; a borda faz o trabalho
            corDoFoco = esquema.primary,
            corDoConteudo = esquema.onSurface
        )
    }

    return when (tema) {
        // Famicom: plastico creme. Um vidro neutro aqui puxa para cinza e suja
        // o tema, entao a tinta vem do proprio creme, levemente clareada.
        TemaApp.NES_JP -> EstiloDeVidro(
            tinta = base.mistura(Color.White, 0.35f).copy(alpha = 0.55f + reforco),
            tintaForte = base.mistura(Color.White, 0.45f).copy(alpha = 0.78f + reforco),
            brilhoSuperior = Color.White.copy(alpha = 0.40f),
            bordaSuperior = Color.White.copy(alpha = 0.75f),
            bordaInferior = esquema.primary.copy(alpha = 0.10f),
            larguraDaBorda = 1.dp,
            raioDeDesfoque = 24.dp,
            desfoqueReal = desfoque,
            sombra = 6.dp,
            corDoFoco = esquema.primary,
            corDoConteudo = esquema.onSurface
        )

        else -> {
            // Caminho generico: vale para Material You, claro, escuro e os tres
            // temas de console restantes, e para qualquer tema que voce
            // acrescentar depois.
            val ehClaro = base.luminance() > 0.5f
            EstiloDeVidro(
                tinta = base.copy(alpha = (if (ehClaro) 0.50f else 0.38f) + reforco),
                tintaForte = base.copy(alpha = (if (ehClaro) 0.74f else 0.62f) + reforco),
                brilhoSuperior = if (ehClaro) {
                    Color.White.copy(alpha = 0.38f)
                } else {
                    luz.copy(alpha = 0.10f)
                },
                bordaSuperior = if (ehClaro) {
                    Color.White.copy(alpha = 0.70f)
                } else {
                    luz.copy(alpha = 0.26f)
                },
                bordaInferior = luz.copy(alpha = 0.05f),
                larguraDaBorda = 1.dp,
                raioDeDesfoque = if (escuro) 26.dp else 22.dp,
                desfoqueReal = desfoque,
                sombra = if (ehClaro) 8.dp else 4.dp,
                corDoFoco = esquema.primary,
                corDoConteudo = esquema.onSurface
            )
        }
    }
}

/** Mistura linear entre duas cores opacas. */
internal fun Color.mistura(outra: Color, fracao: Float): Color {
    val f = fracao.coerceIn(0f, 1f)
    return Color(
        red = red + (outra.red - red) * f,
        green = green + (outra.green - green) * f,
        blue = blue + (outra.blue - blue) * f,
        alpha = alpha + (outra.alpha - alpha) * f
    )
}
