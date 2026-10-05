package com.dfdx047.phoenixemu.emulator

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.OverlayConfigNova
import com.dfdx047.phoenixemu.data.TipoControleNaTela
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun OverlayDeToque(
    config: OverlayConfigNova,
    visivel: Boolean,
    corPrimaria: Color,
    corAcento: Color,
    aoMudarToque: (Int) -> Unit,
    aoAtalhoNaTela: (com.dfdx047.phoenixemu.data.AcaoAtalho, Boolean) -> Unit = { _, _ -> },
    plataforma: String = "SNES"
) {
    var touchMaskAtual by remember { mutableIntStateOf(0) }
    val atalhosAtivos = remember { mutableMapOf<String, com.dfdx047.phoenixemu.data.AcaoAtalho>() }
    val idsAtalhoEstado = remember { androidx.compose.runtime.mutableStateOf(emptySet<String>()) }
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )

    DisposableEffect(visivel) {
        onDispose {
            if (touchMaskAtual != 0) {
                touchMaskAtual = 0
                aoMudarToque(0)
            }
            if (atalhosAtivos.isNotEmpty()) {
                atalhosAtivos.values.toList().forEach { aoAtalhoNaTela(it, false) }
                atalhosAtivos.clear()
                idsAtalhoEstado.value = emptySet()
            }
        }
    }

    if (!visivel || config.visivelModo == com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.NUNCA) return

    val skinId = config.skinId
    val ctxSkins = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember(ctxSkins) { Skins.garantir(ctxSkins); 0 }
    val skin = Skins.porId(skinId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(config.margemSeguraDp.dp)
            .pointerInput(config) {
                awaitPointerEventScope {
                    while (true) {
                        val evento = awaitPointerEvent()
                        var novaMascara = 0
                        val novosAtalhos = mutableMapOf<String, com.dfdx047.phoenixemu.data.AcaoAtalho>()
                        
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val rRef = Math.min(w, h) * 0.08f * config.escalaGlobal

                        val changes = evento.changes
                        for (i in changes.indices) {
                            val pointer = changes[i]
                            if (!pointer.pressed) continue

                            val px = pointer.position.x
                            val py = pointer.position.y

                            var distMin = Float.MAX_VALUE
                            var botaoProximo: BotaoVirtual? = null
                            var controleProximo: com.dfdx047.phoenixemu.data.ControleNaTela? = null

                            for (j in config.controles.indices) {
                                val c = config.controles[j]
                                val cx = c.x * w
                                val cy = c.y * h
                                val dx = px - cx
                                val dy = py - cy
                                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                val raio = rRef * c.tamanhoBase

                                if (c.tipo == TipoControleNaTela.DPAD) {
                                    val raioDpadToque = raio * 1.5f
                                    if (dist < raioDpadToque && dist > config.zonaMortaDpad * raioDpadToque) {
                                        val ang = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI
                                        if (ang > -67.5 && ang < 67.5) novaMascara = novaMascara or (1 shl 7) // DIR
                                        if (ang > 22.5 && ang < 157.5) novaMascara = novaMascara or (1 shl 5) // BAIXO
                                        if (ang < -22.5 && ang > -157.5) novaMascara = novaMascara or (1 shl 4) // CIMA
                                        if (ang > 112.5 || ang < -112.5) novaMascara = novaMascara or (1 shl 6) // ESQ
                                    }
                                } else {
                                    val raioToque = if (c.formato == "PILULA") raio * 2.0f else raio * 1.5f
                                    if (dist < raioToque && dist < distMin) {
                                        distMin = dist
                                        botaoProximo = c.acao?.botaoVirtual
                                        controleProximo = c
                                    }
                                }
                            }

                            if (botaoProximo != null) {
                                val bit = when(botaoProximo) {
                                    BotaoVirtual.CIMA -> (1 shl 4)
                                    BotaoVirtual.BAIXO -> (1 shl 5)
                                    BotaoVirtual.ESQUERDA -> (1 shl 6)
                                    BotaoVirtual.DIREITA -> (1 shl 7)
                                    BotaoVirtual.A -> (1 shl 8)
                                    BotaoVirtual.B -> (1 shl 0)
                                    BotaoVirtual.X -> (1 shl 9)
                                    BotaoVirtual.Y -> (1 shl 1)
                                    BotaoVirtual.L -> (1 shl 10)
                                    BotaoVirtual.R -> (1 shl 11)
                                    BotaoVirtual.SELECT -> (1 shl 2)
                                    BotaoVirtual.START -> (1 shl 3)
                                }
                                novaMascara = novaMascara or bit
                            }
                            val ctlProx = controleProximo
                            val acaoCtl = ctlProx?.acao
                            val atalhoCtl = acaoCtl?.acaoAtalho
                            if (ctlProx != null && acaoCtl != null && atalhoCtl != null &&
                                acaoCtl.tipo == com.dfdx047.phoenixemu.data.TipoAcaoControle.ATALHO) {
                                novosAtalhos[ctlProx.id] = atalhoCtl
                            }
                        }

                        val atalhosApertados = novosAtalhos.filterKeys { it !in atalhosAtivos }
                        val atalhosSoltos = atalhosAtivos.filterKeys { it !in novosAtalhos }
                        if (atalhosApertados.isNotEmpty() || atalhosSoltos.isNotEmpty()) {
                            atalhosSoltos.values.forEach { aoAtalhoNaTela(it, false) }
                            atalhosApertados.values.forEach { aoAtalhoNaTela(it, true) }
                            atalhosAtivos.clear()
                            atalhosAtivos.putAll(novosAtalhos)
                            idsAtalhoEstado.value = novosAtalhos.keys.toSet()
                        }
                        val novosPressionados = novaMascara and touchMaskAtual.inv()
                        if ((novosPressionados != 0 || atalhosApertados.isNotEmpty()) && config.hapticoAtivo) {
                            view.performHapticFeedback(
                                if (config.hapticoIntensidade > 1) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                        }

                        if (novaMascara != touchMaskAtual) {
                            touchMaskAtual = novaMascara
                            aoMudarToque(novaMascara)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rRef = Math.min(w, h) * 0.08f * config.escalaGlobal

            for (i in config.controles.indices) {
                val c = config.controles[i]
                val cx = c.x * w
                val cy = c.y * h
                val raio = rRef * c.tamanhoBase

                val bit = if (c.tipo == TipoControleNaTela.DPAD) {
                    0 // dpad handles its own press state inside skin drawing by receiving the whole mask
                } else {
                    when(c.acao?.botaoVirtual) {
                        BotaoVirtual.CIMA -> (1 shl 4)
                        BotaoVirtual.BAIXO -> (1 shl 5)
                        BotaoVirtual.ESQUERDA -> (1 shl 6)
                        BotaoVirtual.DIREITA -> (1 shl 7)
                        BotaoVirtual.A -> (1 shl 8)
                        BotaoVirtual.B -> (1 shl 0)
                        BotaoVirtual.X -> (1 shl 9)
                        BotaoVirtual.Y -> (1 shl 1)
                        BotaoVirtual.L -> (1 shl 10)
                        BotaoVirtual.R -> (1 shl 11)
                        BotaoVirtual.SELECT -> (1 shl 2)
                        BotaoVirtual.START -> (1 shl 3)
                        else -> 0
                    }
                }

                val pressionado = if (c.tipo == TipoControleNaTela.DPAD) {
                    // if any dpad direction is pressed
                    (touchMaskAtual and ((1 shl 4) or (1 shl 5) or (1 shl 6) or (1 shl 7))) != 0
                } else {
                    (touchMaskAtual and bit) != 0 || c.id in idsAtalhoEstado.value
                }

                val opacidade = if (pressionado) config.opacidadePressionado else config.opacidadeOciosa

                skin.desenharControle(
                    escopoCanvas = this,
                    controle = c,
                    centro = Offset(cx, cy),
                    raioOuTamanho = raio,
                    pressionado = pressionado,
                    corBase = corPrimaria,
                    corAcento = corAcento,
                    opacidade = opacidade,
                    mask = touchMaskAtual,
                    mostrarRotulos = config.mostrarRotulos,
                    textMeasurer = textMeasurer,
                    textStyle = textStyle
                )
            }
        }
    }
}
