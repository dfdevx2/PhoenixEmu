package com.dfdx047.phoenixemu.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

val LocalReduzirEfeitos = staticCompositionLocalOf { false }

object Anim {
    const val RAPIDA_MS = 180
    const val MEDIA_MS = 260
    const val LENTA_MS = 380

    fun <T> mola(): SpringSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
    fun <T> molaSuave(): SpringSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)
    fun <T> rapida(): TweenSpec<T> =
        tween(RAPIDA_MS, easing = FastOutSlowInEasing)
    fun <T> media(): TweenSpec<T> =
        tween(MEDIA_MS, easing = FastOutSlowInEasing)
    fun <T> lenta(): TweenSpec<T> =
        tween(LENTA_MS, easing = FastOutSlowInEasing)

    /** Se reduzido, não anima: troca direto. */
    fun <T> quando(reduzido: Boolean, spec: AnimationSpec<T>): AnimationSpec<T> =
        if (reduzido) snap() else spec
}

object EntradaDaBiblioteca {
    @Volatile var concluida = false
}

fun Modifier.entradaDeCartao(indice: Int): Modifier = composed {
    val reduzido = LocalReduzirEfeitos.current
    val animar = remember { !reduzido && !EntradaDaBiblioteca.concluida }
    if (!animar) {
        Modifier
    } else {
        val progresso = remember { Animatable(0f) }
        val deslocamentoPx = with(LocalDensity.current) { 28.dp.toPx() }
        LaunchedEffect(Unit) {
            delay(indice.coerceIn(0, 14) * 40L)
            progresso.animateTo(1f, tween(Anim.MEDIA_MS + 60, easing = FastOutSlowInEasing))
        }
        Modifier.graphicsLayer {
            val p = progresso.value
            alpha = p
            translationY = (1f - p) * deslocamentoPx
            val s = 0.94f + 0.06f * p
            scaleX = s
            scaleY = s
        }
    }
}
