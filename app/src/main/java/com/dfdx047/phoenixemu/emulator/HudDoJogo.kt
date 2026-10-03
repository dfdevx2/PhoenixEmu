package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable internal fun BoxScope.HudDoJogo(
    rewindAtivo: Boolean, rewindSegundos: Float,
    velocidadeAvanco: Int,
    statsTexto: String, mostrarStats: Boolean,
    avisoTexto: String
) {
    if (rewindAtivo) {
        BasicText(
            text = "◀◀ ${rewindSegundos.roundToInt()}s",
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 16.dp)
        )
    } else if (velocidadeAvanco > 1) {
        BasicText(
            text = "▶▶ ${velocidadeAvanco}x",
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 16.dp)
        )
    }

    if (statsTexto.isNotEmpty() && mostrarStats) {
        BasicText(
            text = statsTexto,
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp)
        )
    }

    if (avisoTexto.isNotEmpty()) {
        BasicText(
            text = avisoTexto,
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 48.dp)
        )
    }
}