package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class AbaDoMenu {
    JOGO, ESTADOS, AJUSTES, CONTROLES, CONQUISTAS
}

@Composable
internal fun MenuLateralDoJogo(
    visivel: Boolean,
    abaAtual: AbaDoMenu,
    aoTrocarAba: (AbaDoMenu) -> Unit,
    aoFechar: () -> Unit,
    fundo: Bitmap?,
    aspectoDoJogo: Float,
    conteudo: @Composable (AbaDoMenu) -> Unit,
) {
    AnimatedVisibility(
        visible = visivel,
        enter = slideInHorizontally(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            initialOffsetX = { -it }
        ) + fadeIn(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ),
        exit = slideOutHorizontally(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            targetOffsetX = { -it }
        ) + fadeOut(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ),
    ) {
        MenuLateralDoJogoInner(
            abaAtual = abaAtual, aoTrocarAba = aoTrocarAba, aoFechar = aoFechar,
            fundo = fundo, aspectoDoJogo = aspectoDoJogo, conteudo = conteudo,
        )
    }
}

@Composable
private fun MenuLateralDoJogoInner(
    abaAtual: AbaDoMenu, aoTrocarAba: (AbaDoMenu) -> Unit, aoFechar: () -> Unit,
    fundo: Bitmap?, aspectoDoJogo: Float, conteudo: @Composable (AbaDoMenu) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        if (fundo != null && aspectoDoJogo > 0f) {
            val larguraFundo = 480
            val alturaFundo = (larguraFundo / aspectoDoJogo).toInt().coerceAtLeast(1)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.background(Color.Black).fillMaxSize()) {
                    Box(
                        modifier = Modifier.width(larguraFundo.dp).height(alturaFundo.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = fundo.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier),
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clickable(onClick = aoFechar)
                            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier),
                    ) {}
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.73f))) {
                Box(modifier = Modifier.fillMaxSize().clickable(onClick = aoFechar)) {}
            }
        }
        MenuPainelLateral(abaAtual, aoTrocarAba, aoFechar, conteudo)
    }
}

@Composable
private fun MenuPainelLateral(
    abaAtual: AbaDoMenu, aoTrocarAba: (AbaDoMenu) -> Unit, aoFechar: () -> Unit,
    conteudo: @Composable (AbaDoMenu) -> Unit,
) {
    Box(modifier = Modifier.width(400.dp).fillMaxHeight()) {
        Surface(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)),
            color = Color.Black.copy(alpha = 0.92f),
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                MenuCabecalho(abaAtual, aoTrocarAba)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { conteudo(abaAtual) }
            }
        }
    }
}

@Composable
private fun MenuCabecalho(abaAtual: AbaDoMenu, aoTrocarAba: (AbaDoMenu) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbaDoMenu.entries.forEachIndexed { index, aba ->
            val isActive = aba == abaAtual
            Row(
                modifier = Modifier.padding(start = if (index > 0) 24.dp else 0.dp)
                    .selectable(selected = isActive, onClick = { aoTrocarAba(aba) }, role = Role.Tab),
            ) {
                Text(
                    text = when (aba) {
                        AbaDoMenu.JOGO -> "Jogo"
                        AbaDoMenu.ESTADOS -> "Estados"
                        AbaDoMenu.AJUSTES -> "Ajustes"
                        AbaDoMenu.CONTROLES -> "Controles"
                        AbaDoMenu.CONQUISTAS -> "Conquistas"
                    },
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current.copy(alpha = 0.7f),
                    ),
                )
                if (isActive) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
        }
    }
}

@Composable
internal fun ConteudoJogo(
    continuar: () -> Unit, reiniciar: () -> Unit, sair: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = continuar, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
        Button(onClick = reiniciar, modifier = Modifier.fillMaxWidth()) { Text("Reiniciar") }
        Button(onClick = sair, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
    }
}

@Composable
internal fun ConteudoEstados(
    slots: List<SlotData>, mensagem: String,
    aoSalvar: (Int) -> Unit, aoCarregar: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (mensagem.isNotEmpty()) {
            Text(text = mensagem, color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        slots.forEachIndexed { index, slot ->
            SlotCard(slot, index, aoSalvar, aoCarregar)
        }
    }
}

@Composable
private fun SlotCard(
    slot: SlotData, index: Int, aoSalvar: (Int) -> Unit, aoCarregar: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (slot.bitmap != null) {
                Image(bitmap = slot.bitmap.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.width(120.dp).aspectRatio(16f / 9f))
            } else {
                Box(modifier = Modifier.width(120.dp).height(75.dp)
                    .background(Color.DarkGray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text(text = "Vazio", color = Color.LightGray, fontSize = 12.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Slot " + slot.slotNumber.toString(), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(text = slot.dateText, fontSize = 12.sp, color = Color.LightGray)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { aoSalvar(slot.slotNumber) }, modifier = Modifier.weight(1f), enabled = true) {
                Text("Salvar")
            }
            Button(onClick = { aoCarregar(slot.slotNumber) }, modifier = Modifier.weight(1f), enabled = slot.exists) {
                Text("Carregar")
            }
        }
    }
}