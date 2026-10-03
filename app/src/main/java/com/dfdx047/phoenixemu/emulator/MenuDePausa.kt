package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun MenuDePausa(
    infoMessage: String,
    mensagemFeedback: String,
    menuState: MenuState,
    slots: List<SlotData>,
    focusRequester: FocusRequester,
    continuar: () -> Unit,
    abrirSalvar: () -> Unit,
    abrirCarregar: () -> Unit,
    reiniciar: () -> Unit,
    sair: () -> Unit,
    salvarSlot: (Int) -> Unit,
    carregarSlot: (Int) -> Unit,
    voltarAoMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            if (infoMessage.isNotEmpty()) {
                BasicText(
                    text = infoMessage,
                    style = TextStyle(color = Color.White, fontSize = 12.sp)
                )
            }

            if (mensagemFeedback.isNotEmpty()) {
                BasicText(
                    text = mensagemFeedback,
                    style = TextStyle(color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
            }

            when (menuState) {
                MenuState.MAIN -> {
                    Button(
                        onClick = continuar,
                        modifier = Modifier.focusRequester(focusRequester)
                    ) { Text("Continuar") }

                    Button(onClick = abrirSalvar) { Text("Salvar estado") }

                    Button(onClick = abrirCarregar) { Text("Carregar estado") }

                    Button(onClick = reiniciar) { Text("Reiniciar") }

                    Button(onClick = sair) { Text("Sair") }
                }

                MenuState.SAVE_SLOTS, MenuState.LOAD_SLOTS -> {
                    val isSaving = (menuState == MenuState.SAVE_SLOTS)
                    Text(
                        text = if (isSaving) "Salvar estado" else "Carregar estado",
                        style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )

                    slots.forEachIndexed { index, slot ->
                        val isFirst = (index == 0)
                        Button(
                            onClick = {
                                if (isSaving) {
                                    salvarSlot(slot.slotNumber)
                                } else {
                                    carregarSlot(slot.slotNumber)
                                }
                            },
                            enabled = if (isSaving) true else slot.exists,
                            modifier = if (isFirst) Modifier.focusRequester(focusRequester) else Modifier
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(0.6f)
                            ) {
                                if (slot.bitmap != null) {
                                    Image(
                                        bitmap = slot.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(width = 80.dp, height = 60.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 80.dp, height = 60.dp)
                                            .background(Color.DarkGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (slot.exists) "Sem img" else "Vazio",
                                            style = TextStyle(color = Color.LightGray, fontSize = 12.sp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Slot ${slot.slotNumber}",
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = slot.dateText,
                                        style = TextStyle(fontSize = 12.sp, color = Color.LightGray)
                                    )
                                }
                            }
                        }
                    }

                    Button(onClick = voltarAoMenu) {
                        Text("Voltar")
                    }
                }
            }
        }
    }

    LaunchedEffect(menuState) {
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }
}