package com.dfdx047.phoenixemu.ui.design

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Deteccao e apresentacao do controle conectado.
 *
 * Mostrar "A para jogar" para quem esta com um DualSense na mao, onde o botao
 * se chama ✕, e pequeno -- mas e o tipo de coisa que separa um frontend que
 * parece caseiro de um que parece produto.
 */
enum class TipoDeControle {
    PLAYSTATION,
    XBOX,
    NINTENDO,
    GENERICO,
    NENHUM;

    val confirmar: String
        get() = if (this == PLAYSTATION) "✕" else "A"   // ✕

    val voltar: String
        get() = if (this == PLAYSTATION) "○" else "B"   // ○

    val opcoes: String
        get() = if (this == PLAYSTATION) "△" else "Y"   // △

    /** Cor da marca no glifo. Unspecified = desenha so o contorno. */
    val cor: Color
        get() = when (this) {
            PLAYSTATION -> Color(0xFF2E6FF2)
            XBOX -> Color(0xFF107C10)
            NINTENDO -> Color(0xFFE60012)
            else -> Color.Unspecified
        }
}

/**
 * Identificacao por vendor id, nao pelo nome do dispositivo.
 *
 * O nome varia demais entre fabricantes, drivers e camadas de compatibilidade
 * ("Xbox Wireless Controller", "Microsoft X-Box 360 pad", "Generic X-Box
 * pad"...). O vendor id e estavel e vem do mesmo dado que o kernel expoe.
 */
private fun identificar(): TipoDeControle {
    var achado = TipoDeControle.NENHUM
    for (id in InputDevice.getDeviceIds()) {
        val dispositivo = InputDevice.getDevice(id) ?: continue
        if (dispositivo.isVirtual) continue

        val fontes = dispositivo.sources
        val ehGamepad =
            (fontes and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (fontes and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!ehGamepad) continue

        val tipo = when (dispositivo.vendorId) {
            0x054C -> TipoDeControle.PLAYSTATION  // Sony
            0x045E -> TipoDeControle.XBOX         // Microsoft
            0x057E -> TipoDeControle.NINTENDO     // Nintendo
            else -> TipoDeControle.GENERICO
        }
        // Controle de marca ganha do generico: handhelds expoem tambem um
        // gamepad sem marca para os botoes embutidos, e ele apareceria primeiro.
        if (tipo != TipoDeControle.GENERICO) return tipo
        achado = tipo
    }
    return achado
}

/**
 * Reavalia quando um controle e conectado ou removido, em vez de olhar uma
 * unica vez: plugar o controle com o app ja aberto e o caso comum.
 */
@Composable
fun lembrarTipoDeControle(): TipoDeControle {
    val context = LocalContext.current
    var tipo by remember { mutableStateOf(identificar()) }

    DisposableEffect(context) {
        val gerenciador = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val ouvinte = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { tipo = identificar() }
            override fun onInputDeviceRemoved(deviceId: Int) { tipo = identificar() }
            override fun onInputDeviceChanged(deviceId: Int) { tipo = identificar() }
        }
        gerenciador.registerInputDeviceListener(ouvinte, null)
        onDispose { gerenciador.unregisterInputDeviceListener(ouvinte) }
    }

    return tipo
}

/**
 * Barra de dicas. So aparece quando ha controle: no toque ela seria ruido.
 */
@Composable
fun BarraDeDicas(
    tipo: TipoDeControle,
    dicas: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    estilo: EstiloDeVidro = LocalVidro.current
) {
    if (tipo == TipoDeControle.NENHUM || dicas.isEmpty()) return

    SuperficieDeVidro(modifier = modifier, forma = CircleShape, forte = true, estilo = estilo) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            dicas.forEach { (glifo, acao) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glifo(glifo, tipo, estilo)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        acao,
                        style = MaterialTheme.typography.labelSmall,
                        color = estilo.corDoConteudo.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun Glifo(texto: String, tipo: TipoDeControle, estilo: EstiloDeVidro) {
    val corDaMarca = tipo.cor
    val temMarca = corDaMarca != Color.Unspecified

    Box(
        modifier = Modifier
            .size(19.dp)
            .clip(CircleShape)
            .then(
                if (temMarca) {
                    Modifier.background(corDaMarca.copy(alpha = 0.9f))
                } else {
                    Modifier.border(1.dp, estilo.corDoConteudo.copy(alpha = 0.6f), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (temMarca) Color.White else estilo.corDoConteudo
        )
    }
}
