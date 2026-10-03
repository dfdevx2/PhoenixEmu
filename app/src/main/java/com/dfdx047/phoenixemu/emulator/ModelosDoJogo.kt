package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap

internal enum class MenuState {
    MAIN,
    SAVE_SLOTS,
    LOAD_SLOTS
}

internal enum class Acao {
    SALVAR_ESTADO, CARREGAR_ESTADO, SLOT_ANTERIOR, SLOT_PROXIMO, AVANCAR, VOLTAR, MENU, REINICIAR
}

internal data class SlotData(
    val slotNumber: Int,
    val exists: Boolean,
    val dateText: String,
    val bitmap: Bitmap?
)
