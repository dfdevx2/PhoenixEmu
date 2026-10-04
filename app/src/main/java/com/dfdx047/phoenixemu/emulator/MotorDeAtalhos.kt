package com.dfdx047.phoenixemu.emulator

import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject

internal class MotorDeAtalhos(
    private val estaPausado: () -> Boolean,
    private val aoExecutarAcao: (Acao) -> Unit,
    private val aoMudarAvanco: (Boolean) -> Unit,
    private val aoMudarRewind: (Boolean) -> Unit,
    private val aoMudarBotoes: (Int) -> Unit
) {
    var keyMask = 0
    var axisMask = 0
    var touchMask = 0
    val keyToBit = mutableMapOf<Int, Int>()

    val atalhos = mutableMapOf<Acao, List<Int>>()
    private val teclasPressionadas = mutableSetOf<Int>()
    private val teclasConsumidas = mutableSetOf<Int>()
    private val acoesDisparadas = mutableSetOf<Acao>()

    var leftTriggerPressed = false
    var rightTriggerPressed = false
    var dpadUpPressed = false
    var dpadDownPressed = false
    var dpadLeftPressed = false
    var dpadRightPressed = false

    fun bindKey(keyCode: Int, bit: Int) {
        keyToBit[keyCode] = (keyToBit[keyCode] ?: 0) or bit
    }

    fun limparBotoes() { keyToBit.clear() }

    fun definirPadroes(debug: Boolean) {
        atalhos[Acao.AVANCAR] = listOf(KeyEvent.KEYCODE_BUTTON_R2)
        atalhos[Acao.VOLTAR] = listOf(KeyEvent.KEYCODE_BUTTON_L2)
        if (debug) {
            atalhos[Acao.SALVAR_ESTADO] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_R1)
            atalhos[Acao.CARREGAR_ESTADO] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1)
            atalhos[Acao.SLOT_ANTERIOR] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_DPAD_LEFT)
            atalhos[Acao.SLOT_PROXIMO] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_DPAD_RIGHT)
            atalhos[Acao.MENU] = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)
            atalhos[Acao.REINICIAR] = emptyList()
        } else {
            atalhos[Acao.SALVAR_ESTADO] = emptyList()
            atalhos[Acao.CARREGAR_ESTADO] = emptyList()
            atalhos[Acao.SLOT_ANTERIOR] = emptyList()
            atalhos[Acao.SLOT_PROXIMO] = emptyList()
            atalhos[Acao.MENU] = emptyList()
            atalhos[Acao.REINICIAR] = emptyList()
        }
    }

    fun carregarDoJson(jsonString: String?) {
        if (jsonString != null) {
            try {
                val json = JSONObject(jsonString)
                Acao.entries.forEach { acao ->
                    if (json.has(acao.name)) {
                        val arr = json.getJSONArray(acao.name)
                        val list = mutableListOf<Int>()
                        for (i in 0 until arr.length()) list.add(arr.getInt(i))
                        atalhos[acao] = list
                    }
                }
            } catch (e: Exception) {}
        }
    }

    fun processarEntradaVirtual(keyCode: Int, isDown: Boolean) {
        if (isDown) {
            teclasPressionadas.add(keyCode)
        } else {
            teclasPressionadas.remove(keyCode)
            teclasConsumidas.remove(keyCode)
        }
        verificarAtalhos()
    }

    private fun verificarAtalhos() {
        if (estaPausado()) return

        val acoesAtivas = mutableSetOf<Acao>()

        for ((acao, combo) in atalhos) {
            if (combo.isEmpty()) continue
            if (teclasPressionadas.containsAll(combo)) {
                acoesAtivas.add(acao)
            }
        }

        for (acao in acoesAtivas) {
            val combo = atalhos[acao] ?: continue
            if (acao != Acao.AVANCAR && acao != Acao.VOLTAR) {
                if (!acoesDisparadas.contains(acao)) {
                    acoesDisparadas.add(acao)
                    Log.i("PhoenixInput", "atalho=${acao.name}")
                    aoExecutarAcao(acao)
                    combo.forEach { teclasConsumidas.add(it) }
                }
            } else {
                combo.forEach { teclasConsumidas.add(it) }
            }
        }

        val toRemove = mutableListOf<Acao>()
        for (acao in acoesDisparadas) {
            val combo = atalhos[acao] ?: continue
            if (!teclasPressionadas.containsAll(combo)) {
                toRemove.add(acao)
            }
        }
        acoesDisparadas.removeAll(toRemove)

        val voltarAtivo = acoesAtivas.contains(Acao.VOLTAR)
        val avancarAtivo = acoesAtivas.contains(Acao.AVANCAR)
        aoMudarRewind(voltarAtivo)
        aoMudarAvanco(avancarAtivo)

        var newKeyMask = 0
        for (key in teclasPressionadas) {
            if (!teclasConsumidas.contains(key)) {
                keyToBit[key]?.let { newKeyMask = newKeyMask or it }
            }
        }
        if (newKeyMask != keyMask) {
            keyMask = newKeyMask
            aoMudarBotoes(keyMask or axisMask or touchMask)
        }
    }

    fun atualizarAxisMask(newAxisMask: Int) {
        if (newAxisMask != axisMask) {
            axisMask = newAxisMask
            aoMudarBotoes(keyMask or axisMask or touchMask)
        }
    }

    fun atualizarTouchMask(newTouchMask: Int) {
        if (newTouchMask != touchMask) {
            touchMask = newTouchMask
            aoMudarBotoes(keyMask or axisMask or touchMask)
        }
    }

    fun resetar() {
        teclasPressionadas.clear()
        teclasConsumidas.clear()
        acoesDisparadas.clear()
        leftTriggerPressed = false
        rightTriggerPressed = false
        dpadUpPressed = false
        dpadDownPressed = false
        dpadLeftPressed = false
        dpadRightPressed = false
        aoMudarRewind(false)
        aoMudarAvanco(false)
        keyMask = 0
        axisMask = 0
        touchMask = 0
        aoMudarBotoes(0)
    }
}