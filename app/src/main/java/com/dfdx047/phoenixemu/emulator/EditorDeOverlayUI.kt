package com.dfdx047.phoenixemu.emulator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.dfdx047.phoenixemu.R
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.foundation.focusable
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.ControleNaTela
import com.dfdx047.phoenixemu.data.OverlayConfigNova
import com.dfdx047.phoenixemu.data.TipoControleNaTela
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.hypot

enum class EditorMode {
    SELECT, MOVE, RESIZE
}

data class EditorState(
    val controles: List<ControleNaTela>,
    val selectedId: String? = null,
    val mode: EditorMode = EditorMode.SELECT,
    val snapToGrid: Boolean = true,
    val gridSize: Int = 20,
    val showPropsDialog: Boolean = false
)

@Composable
fun EditorDeOverlayUI(
    configInicial: OverlayConfigNova,
    corPrimaria: Color,
    corAcento: Color,
    onSave: (OverlayConfigNova) -> Unit,
    onCancel: () -> Unit,
    fundoOpaco: Boolean = false
) {
    // Undo/Redo stacks
    var history by remember { mutableStateOf(listOf(configInicial.controles)) }
    var historyIndex by remember { mutableIntStateOf(0) }

    var state by remember { mutableStateOf(EditorState(controles = configInicial.controles)) }

    fun pushState(novos: List<ControleNaTela>) {
        val newHistory = history.take(historyIndex + 1).toMutableList()
        newHistory.add(novos)
        history = newHistory
        historyIndex = newHistory.size - 1
        state = state.copy(controles = novos)
    }

    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            state = state.copy(controles = history[historyIndex])
        }
    }

    fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            state = state.copy(controles = history[historyIndex])
        }
    }

    val densidade = LocalDensity.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
    
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (fundoOpaco) 0.88f else 0.5f))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val key = event.key.nativeKeyCode
                    var passo = 0.01f // 1% da tela normal
                    if (event.isCtrlPressed) passo = 0.05f 
                    
                    if (state.mode == EditorMode.MOVE && state.selectedId != null) {
                        val c = state.controles.find { it.id == state.selectedId }
                        if (c != null) {
                            var nx = c.x
                            var ny = c.y
                            when (key) {
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> nx -= passo
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> nx += passo
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> ny -= passo
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> ny += passo
                                android.view.KeyEvent.KEYCODE_BUTTON_B -> state = state.copy(mode = EditorMode.SELECT)
                            }
                            if (nx != c.x || ny != c.y) {
                                val novos = state.controles.map { if (it.id == c.id) it.copy(x = nx.coerceIn(0f, 1f), y = ny.coerceIn(0f, 1f)) else it }
                                state = state.copy(controles = novos)
                            }
                            return@onKeyEvent true
                        }
                    } else if (state.mode == EditorMode.SELECT) {
                        when (key) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (state.controles.isNotEmpty()) {
                                    val idx = state.controles.indexOfFirst { it.id == state.selectedId }
                                    val nIdx = if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) (idx + 1) % state.controles.size else (if (idx <= 0) state.controles.size - 1 else idx - 1)
                                    state = state.copy(selectedId = state.controles[nIdx].id)
                                }
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                                if (state.selectedId != null) state = state.copy(mode = EditorMode.MOVE)
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> {
                                if (state.selectedId != null) state = state.copy(showPropsDialog = true)
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_START -> {
                                onSave(configInicial.copy(controles = state.controles))
                                return@onKeyEvent true
                            }
                        }
                    }
                }
                false
            }
    ) {
        // Toolbar superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text(stringResource(R.string.editor_overlay_sair)) }
                Button(onClick = { onSave(configInicial.copy(controles = state.controles)) }) { Text(stringResource(R.string.editor_overlay_salvar)) }
                Button(onClick = { 
                    val novo = ControleNaTela("btn_${System.currentTimeMillis()}", TipoControleNaTela.BOTAO, 0.5f, 0.5f, rotulo = "NOVO", acao = com.dfdx047.phoenixemu.data.AcaoDoControle())
                    pushState(state.controles + novo)
                    state = state.copy(selectedId = novo.id, mode = EditorMode.MOVE)
                }) { Text(stringResource(R.string.editor_overlay_add)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::undo, enabled = historyIndex > 0) { Text(stringResource(R.string.editor_overlay_desfazer)) }
                Button(onClick = ::redo, enabled = historyIndex < history.size - 1) { Text(stringResource(R.string.editor_overlay_refazer)) }
                Button(onClick = { state = state.copy(snapToGrid = !state.snapToGrid) }, colors = ButtonDefaults.buttonColors(containerColor = if (state.snapToGrid) corPrimaria else Color.DarkGray)) {
                    Text(stringResource(R.string.editor_overlay_grade))
                }
            }
        }

        // Propriedades laterais / bottom se selecionado
        if (state.selectedId != null && state.showPropsDialog) {
            val controle = state.controles.find { it.id == state.selectedId }
            if (controle != null) {
                PropriedadesControleDialog(
                    controle = controle,
                    onSalvar = { editado ->
                        pushState(state.controles.map { if (it.id == editado.id) editado else it })
                        state = state.copy(showPropsDialog = false)
                    },
                    onCancelar = { state = state.copy(showPropsDialog = false) },
                    onRemover = {
                        pushState(state.controles.filter { it.id != controle.id })
                        state = state.copy(selectedId = null, showPropsDialog = false)
                    },
                    onDuplicar = {
                        val novo = controle.copy(id = "duplicado_${System.currentTimeMillis()}", x = (controle.x + 0.05f).coerceIn(0f, 1f), y = (controle.y + 0.05f).coerceIn(0f, 1f))
                        pushState(state.controles + novo)
                        state = state.copy(selectedId = novo.id, showPropsDialog = false)
                    }
                )
            }
        }

        // Area de edição (o canvas)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp) // Abaixo da toolbar
        ) {
            // Desenhar grid se ativado
            if (state.snapToGrid) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val step = state.gridSize.dp.toPx()
                    var x = 0f
                    while (x < w) {
                        drawLine(Color.White.copy(alpha = 0.1f), start = Offset(x, 0f), end = Offset(x, h))
                        x += step
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(Color.White.copy(alpha = 0.1f), start = Offset(0f, y), end = Offset(w, y))
                        y += step
                    }
                }
            }

            // Desenhar controles (como no OverlayDeToque, mas permitindo arraste)
            state.controles.forEach { controle ->

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val w = maxWidth.value * densidade.density
                    val h = maxHeight.value * densidade.density
                    
                    val px = controle.x * w
                    val py = controle.y * h
                    val raio = Math.min(w, h) * 0.08f * configInicial.escalaGlobal * controle.tamanhoBase

                    Box(
                        modifier = Modifier
                            .offset { IntOffset((px - raio*2).roundToInt(), (py - raio*2).roundToInt()) }
                            .size((raio*4/densidade.density).dp)
                            .pointerInput(controle.id) {
                                detectDragGestures(
                                    onDragStart = { state = state.copy(selectedId = controle.id) },
                                    onDragEnd = {
                                        val atual = state.controles.first { it.id == controle.id }
                                        val mudou = atual.x != controle.x || atual.y != controle.y
                                        if (mudou) pushState(state.controles)
                                    }
                                ) { mudanca, arrasto ->
                                    mudanca.consume()
                                    val atual = state.controles.first { it.id == controle.id }
                                    var newX = atual.x + (arrasto.x / w)
                                    var newY = atual.y + (arrasto.y / h)

                                    if (state.snapToGrid) {
                                        // Converter para px, snap, voltar para fração
                                        val stepPx = state.gridSize.dp.toPx()
                                        val curPxX = newX * w
                                        val curPxY = newY * h
                                        val snapX = (curPxX / stepPx).roundToInt() * stepPx
                                        val snapY = (curPxY / stepPx).roundToInt() * stepPx
                                        newX = snapX / w
                                        newY = snapY / h
                                    }
                                    
                                    val novos = state.controles.map {
                                        if (it.id == controle.id) it.copy(x = newX.coerceIn(0f, 1f), y = newY.coerceIn(0f, 1f)) else it
                                    }
                                    state = state.copy(controles = novos)
                                }
                            }
                            .clickable {
                                state = state.copy(selectedId = controle.id, showPropsDialog = true)
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Desenha um bounding box se estiver selecionado
                            if (state.selectedId == controle.id) {
                                drawRect(corPrimaria.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }

            // O desenho real da skin
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val rRef = Math.min(w, h) * 0.08f * configInicial.escalaGlobal
                val skin = if (configInicial.skinId == "classico_8bit") SkinClassica8Bit 
                           else if (configInicial.skinId == "moderno") SkinModerna 
                           else SkinClassica16Bit

                for (c in state.controles) {
                    val cx = c.x * w
                    val cy = c.y * h
                    val raio = rRef * c.tamanhoBase

                    skin.desenharControle(
                        escopoCanvas = this,
                        controle = c,
                        centro = Offset(cx, cy),
                        raioOuTamanho = raio,
                        pressionado = false,
                        corBase = corPrimaria,
                        corAcento = corAcento,
                        opacidade = c.opacidade ?: configInicial.opacidadeOciosa,
                        mask = 0,
                        mostrarRotulos = configInicial.mostrarRotulos,
                        textMeasurer = textMeasurer,
                        textStyle = textStyle
                    )
                }
            }
        }
    }
}

@Composable
fun PropriedadesControleDialog(
    controle: ControleNaTela,
    onSalvar: (ControleNaTela) -> Unit,
    onCancelar: () -> Unit,
    onRemover: () -> Unit,
    onDuplicar: () -> Unit
) {
    var tamanho by remember { mutableFloatStateOf(controle.tamanhoBase) }
    var opacidade by remember { mutableFloatStateOf(controle.opacidade ?: 1f) }
    var usarOpacidadePropria by remember { mutableStateOf(controle.opacidade != null) }
    
    // Implement properties here
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.editor_overlay_props_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.editor_overlay_props_tamanho, (tamanho * 100).toInt()))
                Slider(value = tamanho, onValueChange = { tamanho = it }, valueRange = 0.5f..2.5f)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = usarOpacidadePropria, onCheckedChange = { usarOpacidadePropria = it })
                    Text(stringResource(R.string.editor_overlay_props_opacidade))
                }
                if (usarOpacidadePropria) {
                    Slider(value = opacidade, onValueChange = { opacidade = it }, valueRange = 0.0f..1.0f)
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        onClick = { onSalvar(controle.copy(x = 1f - controle.x)) }
                    ) { Text(stringResource(R.string.editor_overlay_props_espelhar_x), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        onClick = { onSalvar(controle.copy(x = 0.5f)) }
                    ) { Text(stringResource(R.string.editor_overlay_props_centro_x), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        onClick = { onSalvar(controle.copy(y = 0.5f)) }
                    ) { Text(stringResource(R.string.editor_overlay_props_centro_y), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSalvar(controle.copy(tamanhoBase = tamanho, opacidade = if (usarOpacidadePropria) opacidade else null)) }) { Text(stringResource(R.string.editor_overlay_props_ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemover) { Text(stringResource(R.string.editor_overlay_props_remover), color = Color.Red) }
                TextButton(onClick = onDuplicar) { Text(stringResource(R.string.editor_overlay_props_duplicar)) }
                TextButton(onClick = onCancelar) { Text(stringResource(R.string.editor_overlay_props_cancelar)) }
            }
        }
    )
}
