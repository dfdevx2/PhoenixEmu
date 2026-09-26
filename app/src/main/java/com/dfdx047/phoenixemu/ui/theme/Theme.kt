package com.dfdx047.phoenixemu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.dfdx047.phoenixemu.TemaApp

// Os 8 esquemas de cor foram preservados exatamente como estavam.
private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
private val LightColorScheme = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

private val AmoledColorScheme = darkColorScheme(
    background = AmoledBackground,
    surface = AmoledSurface,
    primary = AmoledPrimary,
    onPrimary = AmoledOnPrimary,
    primaryContainer = AmoledBackground,
    onPrimaryContainer = AmoledPrimary,
    secondaryContainer = AmoledSurface
)

private val NesUsColorScheme = lightColorScheme(
    background = NesUsBackground,
    surface = NesUsSurface,
    primary = NesUsPrimary,
    onPrimary = NesUsOnPrimary,
    primaryContainer = NesUsSecondary,
    onPrimaryContainer = NesUsPrimary,
    secondaryContainer = NesUsBackground
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
    primaryContainer = SnesJpSurface,
    onPrimaryContainer = SnesJpPrimary,
    secondaryContainer = SnesJpBackground
)

@Composable
fun PhoenixEmuTheme(
    temaAtual: TemaApp,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (temaAtual) {
        TemaApp.AMOLED -> AmoledColorScheme
        TemaApp.NES_US -> NesUsColorScheme
        TemaApp.NES_JP -> FamicomColorScheme
        TemaApp.SNES_US -> SnesUsColorScheme
        TemaApp.SNES_JP -> SnesJpColorScheme
        TemaApp.CLARO -> LightColorScheme
        TemaApp.ESCURO -> DarkColorScheme
        TemaApp.DINAMICO -> {
            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
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

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
