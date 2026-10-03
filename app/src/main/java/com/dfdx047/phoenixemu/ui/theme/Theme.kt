package com.dfdx047.phoenixemu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.dfdx047.phoenixemu.Acabamento
import com.dfdx047.phoenixemu.TemaApp
import com.dfdx047.phoenixemu.ui.design.LocalVidro
import com.dfdx047.phoenixemu.ui.design.estiloDeVidroPara

// Os 8 esquemas de cor foram preservados exatamente como estavam.
private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
private val LightColorScheme = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

private val NesUsColorScheme = lightColorScheme(
    background = NesUsBackground,
    surface = NesUsSurface,
    primary = NesUsPrimary,
    onPrimary = NesUsOnPrimary,
    primaryContainer = NesUsSecondary,
    onPrimaryContainer = NesUsOnPrimaryContainer,
    secondary = NesUsSecondary,
    tertiary = NesUsPrimary,
    secondaryContainer = NesUsContainer,
    surfaceVariant = NesUsContainer,
    onSurface = NesUsOnSurface,
    onSurfaceVariant = NesUsOnSurfaceVariant,
    outline = NesUsOutline
)

private val FamicomColorScheme = lightColorScheme(
    background = FamicomBackground,
    surface = FamicomSurface,
    primary = FamicomPrimary,
    onPrimary = FamicomOnPrimary,
    primaryContainer = FamicomPrimary,
    onPrimaryContainer = FamicomBackground,
    secondaryContainer = FamicomBackground
)

private val SnesUsColorScheme = lightColorScheme(
    background = SnesUsBackground,
    surface = SnesUsSurface,
    primary = SnesUsPrimary,
    onPrimary = SnesUsOnPrimary,
    primaryContainer = SnesUsPrimary,
    onPrimaryContainer = Color.White,
    secondaryContainer = SnesUsSecondary
)

private val SnesJpColorScheme = lightColorScheme(
    background = SnesJpBackground,
    surface = SnesJpSurface,
    primary = SnesJpPrimary,
    onPrimary = SnesJpOnPrimary,
    primaryContainer = SnesJpPrimary,
    onPrimaryContainer = Color.White,
    secondary = SnesJpSecondary,
    tertiary = SnesJpTertiary,
    secondaryContainer = SnesJpContainer,
    surfaceVariant = SnesJpContainer,
    onSurface = SnesJpOnSurface,
    onSurfaceVariant = SnesJpOnSurfaceVariant,
    outline = SnesJpOutline
)

/**
 * O esquema de cor cru de um tema, sem o acabamento aplicado.
 *
 * Existe para o seletor de temas poder mostrar uma amostra de cada tema sem
 * precisar aplica-lo: a lista antiga era so nomes, e "Super Famicom
 * (japones)" nao diz nada sobre o que voce vai ver.
 */
@Composable
fun esquemaDeAmostra(
    tema: TemaApp,
    darkTheme: Boolean = isSystemInDarkTheme()
): ColorScheme = when (tema) {
    TemaApp.NES_US -> NesUsColorScheme
    TemaApp.NES_JP -> FamicomColorScheme
    TemaApp.SNES_US -> SnesUsColorScheme
    TemaApp.SNES_JP -> SnesJpColorScheme
    TemaApp.DINAMICO -> {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }
}

@Composable
fun PhoenixEmuTheme(
    temaAtual: TemaApp,
    /** Vem das Configuracoes; desliga o desfoque ao vivo em aparelhos fracos. */
    reduzirEfeitos: Boolean = false,
    /** Preto absoluto sobre qualquer tema escuro. */
    amoled: Boolean = false,
    acabamento: Acabamento = Acabamento.VIDRO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Liga/desliga sombras em todos os componentes do app. */
    sombras: Boolean = false,
    content: @Composable () -> Unit
) {
    val esquemaBase = esquemaDeAmostra(temaAtual, darkTheme)

    // Preto absoluto e um acabamento, nao um tema: ele se aplica por cima de
    // qualquer esquema escuro em vez de existir como uma opcao isolada. Em
    // tema claro nao faz sentido nenhum, entao e simplesmente ignorado.
    val esquemaEhEscuro = esquemaBase.background.luminance() < 0.5f
    val colorScheme = if (amoled && esquemaEhEscuro) {
        esquemaBase.copy(
            background = AmoledBackground,
            surface = AmoledSurface,
            surfaceVariant = AmoledSurface,
            primaryContainer = AmoledBackground,
            secondaryContainer = AmoledSurface
        )
    } else {
        esquemaBase
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // `window.statusBarColor` foi removido daqui de proposito: com
        // targetSdk 37 o edge-to-edge e obrigatorio e o sistema ignora
        // aquela cor. Quem pinta a area da status bar agora e a propria UI
        // (a TopAppBar desenhando sob os insets).
        //
        // E a lista fixa de temas que decidia se os icones eram claros ou
        // escuros tambem saiu. Calculamos pela luminancia da cor, entao
        // qualquer tema novo acerta automaticamente, sem editar esta linha.
        val corAtrasDaStatusBar = colorScheme.primaryContainer
        val corAtrasDaNavBar = colorScheme.background
        SideEffect {
            val window = (view.context as Activity).window
            val controlador = WindowInsetsControllerCompat(window, view)
            controlador.isAppearanceLightStatusBars = corAtrasDaStatusBar.luminance() > 0.5f
            controlador.isAppearanceLightNavigationBars = corAtrasDaNavBar.luminance() > 0.5f
        }
    }

    // O estilo do vidro e derivado do esquema de cor ativo, entao os 8 temas
    // (e qualquer outro que voce crie) ganham um vidro coerente sem que este
    // arquivo precise saber deles.
    val ehEscuro = colorScheme.background.luminance() < 0.5f
    val estiloDeVidro = remember(temaAtual, colorScheme, ehEscuro, reduzirEfeitos, acabamento, amoled, sombras) {
        estiloDeVidroPara(temaAtual, colorScheme, ehEscuro, reduzirEfeitos, acabamento, amoled, sombras)
    }

    CompositionLocalProvider(LocalVidro provides estiloDeVidro) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
