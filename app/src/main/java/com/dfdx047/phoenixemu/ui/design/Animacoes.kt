package com.dfdx047.phoenixemu.ui.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.staticCompositionLocalOf

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
