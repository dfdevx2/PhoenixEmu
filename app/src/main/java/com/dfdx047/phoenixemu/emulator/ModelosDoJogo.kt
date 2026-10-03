package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap

internal enum class Acao {
    SALVAR_ESTADO, CARREGAR_ESTADO, SLOT_ANTERIOR, SLOT_PROXIMO, AVANCAR, VOLTAR, MENU, REINICIAR
}

internal data class SlotData(
    val slotNumber: Int,
    val exists: Boolean,
    val dateText: String,
    val timeText: String,
    val bitmap: Bitmap?,
)
