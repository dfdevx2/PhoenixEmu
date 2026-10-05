package com.dfdx047.phoenixemu.emulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Estado das conquistas do jogo atual, lido pela UI do menu de pausa. */
object EstadoRaJogo {
    var ativo by mutableStateOf(false)
    var titulo by mutableStateOf("")
    var total by mutableIntStateOf(0)
    var desbloqueadas by mutableIntStateOf(0)
    var pontosTotal by mutableIntStateOf(0)
    var pontosGanhos by mutableIntStateOf(0)
    var hardcore by mutableStateOf(false)
    var itens by mutableStateOf<List<ItemConquista>>(emptyList())

    fun definirResumo(titulo: String, total: Int, desbloqueadas: Int, pontosTotal: Int, pontosGanhos: Int) {
        this.titulo = titulo
        this.total = total
        this.desbloqueadas = desbloqueadas
        this.pontosTotal = pontosTotal
        this.pontosGanhos = pontosGanhos
        ativo = total > 0
    }
}
