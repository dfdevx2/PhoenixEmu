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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dfdx047.phoenixemu.TemaApp

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
    primaryContainer = NesUsSecondary, // Barra Preta
    onPrimaryContainer = NesUsPrimary, // Texto Vermelho
    secondaryContainer = NesUsBackground
)

private val FamicomColorScheme = lightColorScheme(
    background = FamicomBackground,
    surface = FamicomSurface,
    primary = FamicomPrimary,
    onPrimary = FamicomOnPrimary,
    primaryContainer = FamicomPrimary, // Barra Vinho
    onPrimaryContainer = FamicomBackground, // Texto Creme
    secondaryContainer = FamicomBackground
)

private val SnesUsColorScheme = lightColorScheme(
    background = SnesUsBackground,
    surface = SnesUsSurface,
    primary = SnesUsPrimary,
    onPrimary = SnesUsOnPrimary,
    primaryContainer = SnesUsPrimary, // Barra Roxo Escuro
    onPrimaryContainer = Color.White,
    secondaryContainer = SnesUsSecondary // Cartões com fundo Lilás
)

private val SnesJpColorScheme = lightColorScheme(
    background = SnesJpBackground,
    surface = SnesJpSurface,
    primary = SnesJpPrimary,
    onPrimary = SnesJpOnPrimary,
    primaryContainer = SnesJpSurface, // Barra Cinza Escuro
    onPrimaryContainer = SnesJpPrimary, // Azul
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
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primaryContainer.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkTheme && temaAtual != TemaApp.AMOLED && temaAtual != TemaApp.ESCURO && temaAtual != TemaApp.SNES_US && temaAtual != TemaApp.NES_JP && temaAtual != TemaApp.NES_US
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}