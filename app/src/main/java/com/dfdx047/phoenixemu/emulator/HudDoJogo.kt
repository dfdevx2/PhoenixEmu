package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
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
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            BasicText(
                text = statsTexto,
                style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }
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