package com.dfdx047.phoenixemu.emulator

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.R
import com.dfdx047.phoenixemu.ui.design.SuperficieDeVidro
import coil.compose.AsyncImage
import com.dfdx047.phoenixemu.ui.design.LocalReduzirEfeitos
import kotlinx.coroutines.delay

/** Uma conquista a ser anunciada na tela. `uid` distingue avisos iguais na fila. */
data class ConquistaAviso(
    val titulo: String,
    val descricao: String,
    val pontos: Int,
    val badgeUrl: String? = null,
    val uid: Long = System.nanoTime()
)

enum class PosicaoAviso { TOPO_ESQUERDA, TOPO_CENTRO, TOPO_DIREITA, BAIXO_ESQUERDA, BAIXO_CENTRO, BAIXO_DIREITA }

data class ConfigAvisoConquista(
    val ativo: Boolean = true,
    val som: Boolean = true,
    val vibrar: Boolean = true,
    val duracaoMs: Long = 4000L,
    val posicao: PosicaoAviso = PosicaoAviso.TOPO_CENTRO,
    val volume: Float = 1f
)

/** Som curto de conquista. Funciona em qualquer processo (tem o proprio SoundPool). */
class SomDeConquista(contexto: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var id = 0
    @Volatile private var pronto = false

    init {
        pool.setOnLoadCompleteListener { _, _, status -> pronto = status == 0 }
        id = pool.load(contexto.applicationContext, R.raw.sfx_conquista, 1)
    }

    fun tocar(volume: Float) {
        if (pronto) pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun liberar() = pool.release()
}

private fun vibrarConquista(view: View) {
    val tipo = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
    else HapticFeedbackConstants.LONG_PRESS
    view.performHapticFeedback(tipo, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
}

/**
 * Pop-up de conquista desbloqueada. Mostra um aviso por vez, tirando-o da fila.
 * Nao intercepta toques (o jogo continua jogavel). Anima so por graphicsLayer.
 */
@Composable
fun BoxScope.PopupDeConquista(
    fila: SnapshotStateList<ConquistaAviso>,
    corPrimaria: Color,
    config: ConfigAvisoConquista = ConfigAvisoConquista()
) {
    val contexto = LocalContext.current
    val view = LocalView.current
    val reduzir = LocalReduzirEfeitos.current
    val som = remember { SomDeConquista(contexto) }
    DisposableEffect(Unit) { onDispose { som.liberar() } }

    val progresso = remember { Animatable(0f) }
    var exibido by remember { mutableStateOf<ConquistaAviso?>(null) }
    val proximo = fila.firstOrNull()

    LaunchedEffect(proximo?.uid) {
        val aviso = proximo ?: return@LaunchedEffect
        if (!config.ativo) {
            fila.remove(aviso)
            return@LaunchedEffect
        }
        exibido = aviso
        if (config.som) som.tocar(config.volume)
        if (config.vibrar) vibrarConquista(view)
        progresso.snapTo(0f)
        progresso.animateTo(1f, tween(if (reduzir) 120 else 280, easing = FastOutSlowInEasing))
        delay(config.duracaoMs)
        progresso.animateTo(0f, tween(if (reduzir) 120 else 220, easing = FastOutLinearInEasing))
        exibido = null
        fila.remove(aviso)
    }

    val aviso = exibido ?: return
    val noTopo = config.posicao == PosicaoAviso.TOPO_ESQUERDA || config.posicao == PosicaoAviso.TOPO_CENTRO ||
        config.posicao == PosicaoAviso.TOPO_DIREITA
    val alinhamento = when (config.posicao) {
        PosicaoAviso.TOPO_ESQUERDA -> Alignment.TopStart
        PosicaoAviso.TOPO_CENTRO -> Alignment.TopCenter
        PosicaoAviso.TOPO_DIREITA -> Alignment.TopEnd
        PosicaoAviso.BAIXO_ESQUERDA -> Alignment.BottomStart
        PosicaoAviso.BAIXO_CENTRO -> Alignment.BottomCenter
        PosicaoAviso.BAIXO_DIREITA -> Alignment.BottomEnd
    }
    val forma = RoundedCornerShape(16.dp)

    SuperficieDeVidro(
        forma = forma,
        desfocar = false,
        modifier = Modifier
            .align(alinhamento)
            .padding(16.dp)
            .widthIn(max = 380.dp)
            .graphicsLayer {
                val v = progresso.value
                alpha = v
                if (!reduzir) {
                    translationY = (1f - v) * 40.dp.toPx() * (if (noTopo) -1f else 1f)
                    val s = 0.96f + 0.04f * v
                    scaleX = s
                    scaleY = s
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (aviso.badgeUrl != null) {
            AsyncImage(
                model = aviso.badgeUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(44.dp), tint = corPrimaria)
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                stringResource(R.string.conquista_desbloqueada).uppercase(),
                color = corPrimaria,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                aviso.titulo,
                color = LocalContentColor.current,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (aviso.descricao.isNotBlank()) {
                Text(
                    aviso.descricao,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            "+${aviso.pontos}",
            color = corPrimaria,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        }
    }
}
