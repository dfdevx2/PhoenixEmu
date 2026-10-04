package com.dfdx047.phoenixemu.emulator

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dfdx047.phoenixemu.data.OverlayConfigNova
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.TipoControleNaTela
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun OverlayDeToque(
    config: OverlayConfigNova,
    visivel: Boolean,
    corPrimaria: Color,
    corAcento: Color,
    aoMudarToque: (Int) -> Unit
) {
    if (!visivel || config.visivelModo == com.dfdx047.phoenixemu.data.ModoVisibilidadeOverlay.NUNCA) return

    val skin = SkinClassicaSnes // Usaremos a clássica por enquanto
    
    // Estado interno para rastrear ponteiros e botões pressionados
    var botoesPressionados by remember { mutableStateOf(setOf<BotaoVirtual>()) }
    var direcoesDpad by remember { mutableStateOf(setOf<BotaoVirtual>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(config.margemSeguraDp.dp)
            .pointerInput(config) {
                awaitPointerEventScope {
                    while (true) {
                        val evento = awaitPointerEvent()
                        val posicoes = evento.changes.map { it.position }
                        
                        val novosBotoes = mutableSetOf<BotaoVirtual>()
                        val novasDirecoes = mutableSetOf<BotaoVirtual>()
                        
                        for (pos in posicoes) {
                            // Converter coords 0..1
                            val px = pos.x / size.width
                            val py = pos.y / size.height
                            
                            // Verificar colisão
                            for (controle in config.controles) {
                                val dx = px - controle.x
                                val dy = py - controle.y
                                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                
                                val raioBase = 0.08f * config.escalaGlobal * controle.tamanhoBase
                                
                                if (controle.tipo == TipoControleNaTela.DPAD) {
                                    if (dist < raioBase * 2.5f && dist > config.zonaMortaDpad * raioBase) {
                                        val angulo = atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI
                                        // Mapear angulo para cima, baixo, esq, dir
                                        // -45 a 45: Direita
                                        // 45 a 135: Baixo
                                        // 135 a -135: Esquerda
                                        // -135 a -45: Cima
                                        if (angulo > -67.5 && angulo < 67.5) novasDirecoes.add(BotaoVirtual.DIREITA)
                                        if (angulo > 22.5 && angulo < 157.5) novasDirecoes.add(BotaoVirtual.BAIXO)
                                        if (angulo < -22.5 && angulo > -157.5) novasDirecoes.add(BotaoVirtual.CIMA)
                                        if (angulo > 112.5 || angulo < -112.5) novasDirecoes.add(BotaoVirtual.ESQUERDA)
                                    }
                                } else if (dist < raioBase * 1.5f) {
                                    controle.acao?.botaoVirtual?.let { novosBotoes.add(it) }
                                }
                            }
                        }
                        
                        if (novosBotoes != botoesPressionados || novasDirecoes != direcoesDpad) {
                            botoesPressionados = novosBotoes
                            direcoesDpad = novasDirecoes
                            
                            var mascara = 0
                            val combinados = novosBotoes + novasDirecoes
                            
                            // Mapear para keycodes ou bits diretamente
                            // Para facilitar com o motor existente, simulamos keycodes
                            val bitMap = mapOf(
                                BotaoVirtual.CIMA to (1 shl 4),
                                BotaoVirtual.BAIXO to (1 shl 5),
                                BotaoVirtual.ESQUERDA to (1 shl 6),
                                BotaoVirtual.DIREITA to (1 shl 7),
                                BotaoVirtual.A to (1 shl 8),
                                BotaoVirtual.B to (1 shl 0),
                                BotaoVirtual.X to (1 shl 9),
                                BotaoVirtual.Y to (1 shl 1),
                                BotaoVirtual.L to (1 shl 10),
                                BotaoVirtual.R to (1 shl 11),
                                BotaoVirtual.SELECT to (1 shl 2),
                                BotaoVirtual.START to (1 shl 3)
                            )
                            
                            for (b in combinados) {
                                bitMap[b]?.let { mascara = mascara or it }
                            }
                            aoMudarToque(mascara)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (controle in config.controles) {
                val cx = controle.x * size.width
                val cy = controle.y * size.height
                val raio = 0.08f * config.escalaGlobal * controle.tamanhoBase * Math.min(size.width, size.height)
                
                val pressionado = if (controle.tipo == TipoControleNaTela.DPAD) {
                    direcoesDpad.isNotEmpty()
                } else {
                    botoesPressionados.contains(controle.acao?.botaoVirtual)
                }
                
                val opacidade = if (pressionado) config.opacidadePressionado else config.opacidadeOciosa
                
                skin.desenharControle(
                    escopoCanvas = this,
                    controle = controle,
                    centro = Offset(cx, cy),
                    raioOuTamanho = raio,
                    pressionado = pressionado,
                    corBase = corPrimaria,
                    corAcento = corAcento,
                    opacidade = opacidade
                )
            }
        }
    }
}
