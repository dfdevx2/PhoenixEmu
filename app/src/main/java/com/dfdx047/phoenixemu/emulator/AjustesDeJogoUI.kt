package com.dfdx047.phoenixemu.emulator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dfdx047.phoenixemu.R
import kotlin.math.roundToInt

@Composable
internal fun ConteudoAbaAjustes(
    ajustes: AjustesDeJogo,
    aoMudarAjuste: (String, Any, String) -> Unit,
    aoLimparAjustesJogo: () -> Unit
) {
    val primária = MaterialTheme.colorScheme.primary
    var isSomenteEsteJogo by remember(ajustes.overrides) { mutableStateOf(ajustes.isSomenteEsteJogo()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        
        // --- Aplicar em ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, primária.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(if (!isSomenteEsteJogo) primária.copy(alpha = 0.4f) else Color.Transparent).clickable { 
                        isSomenteEsteJogo = false
                        aoMudarAjuste("escopo", false, "boolean")
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.ajustes_aplicar_todos), fontSize = 13.sp, fontWeight = if (!isSomenteEsteJogo) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(if (isSomenteEsteJogo) primária.copy(alpha = 0.4f) else Color.Transparent).clickable { 
                        isSomenteEsteJogo = true
                        aoMudarAjuste("escopo", true, "boolean")
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.ajustes_aplicar_este), fontSize = 13.sp, fontWeight = if (isSomenteEsteJogo) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                }
            }
            Text(
                text = if (isSomenteEsteJogo) stringResource(R.string.ajustes_estado_este) else stringResource(R.string.ajustes_estado_todos),
                fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.7f)
            )
        }

        // --- VÍDEO ---
        Text(text = stringResource(R.string.ajustes_secao_video), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primária.copy(alpha = 0.6f), letterSpacing = 2.sp)
        
        LinhaSegmentada(
            titulo = stringResource(R.string.ajustes_titulo_escala),
            dica = stringResource(R.string.ajustes_dica_escala),
            opcoes = listOf(stringResource(R.string.ajustes_op_escala_ajustar) to EscalaImagem.AJUSTAR.name, stringResource(R.string.ajustes_op_escala_1x) to EscalaImagem.X1.name, stringResource(R.string.ajustes_op_escala_2x) to EscalaImagem.X2.name, stringResource(R.string.ajustes_op_escala_3x) to EscalaImagem.X3.name),
            selecionado = ajustes.escala.name,
            onSelect = { aoMudarAjuste(AjustesDeJogo.CHAVE_ESCALA, it, "string") }
        )

        LinhaSegmentada(
            titulo = stringResource(R.string.ajustes_titulo_proporcao),
            dica = stringResource(R.string.ajustes_dica_proporcao),
            opcoes = listOf(stringResource(R.string.ajustes_op_proporcao_auto) to ProporcaoImagem.AUTOMATICA.name, stringResource(R.string.ajustes_op_proporcao_4_3) to ProporcaoImagem.RATIO_4_3.name, stringResource(R.string.ajustes_op_proporcao_esticar) to ProporcaoImagem.ESTICAR.name),
            selecionado = ajustes.proporcao.name,
            onSelect = { aoMudarAjuste(AjustesDeJogo.CHAVE_PROPORCAO, it, "string") }
        )

        LinhaInterruptor(
            titulo = stringResource(R.string.ajustes_titulo_mostrar_fps),
            dica = stringResource(R.string.ajustes_dica_mostrar_fps),
            checado = ajustes.mostrarFps,
            onCheckedChange = { aoMudarAjuste(AjustesDeJogo.CHAVE_MOSTRAR_FPS, it, "boolean") }
        )

        // --- ÁUDIO ---
        Text(text = stringResource(R.string.ajustes_secao_audio), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primária.copy(alpha = 0.6f), letterSpacing = 2.sp)

        LinhaSlider(
            titulo = stringResource(R.string.ajustes_titulo_volume),
            dica = stringResource(R.string.ajustes_dica_volume),
            valor = ajustes.volume,
            onValueChange = { aoMudarAjuste(AjustesDeJogo.CHAVE_VOLUME, it, "float") }
        )

        LinhaInterruptor(
            titulo = stringResource(R.string.ajustes_titulo_mudo),
            dica = stringResource(R.string.ajustes_dica_mudo),
            checado = ajustes.mudo,
            onCheckedChange = { aoMudarAjuste(AjustesDeJogo.CHAVE_MUDO, it, "boolean") }
        )

        // --- JOGO ---
        Text(text = stringResource(R.string.ajustes_secao_jogo), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primária.copy(alpha = 0.6f), letterSpacing = 2.sp)
        
        LinhaSegmentada(
            titulo = stringResource(R.string.ajustes_titulo_velocidade_ff),
            dica = stringResource(R.string.ajustes_dica_velocidade_ff),
            opcoes = listOf(stringResource(R.string.ajustes_op_ff_2x) to 2, stringResource(R.string.ajustes_op_ff_3x) to 3, stringResource(R.string.ajustes_op_ff_4x) to 4),
            selecionado = ajustes.velocidadeFF,
            onSelect = { aoMudarAjuste(AjustesDeJogo.CHAVE_VELOCIDADE_FF, it, "int") }
        )

        if (ajustes.isSomenteEsteJogo()) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = aoLimparAjustesJogo),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.ajustes_botao_limpar), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LinhaSegmentada(titulo: String, dica: String, opcoes: List<Pair<String, Any>>, selecionado: Any, onSelect: (Any) -> Unit) {
    var dicaAberta by remember { mutableStateOf(false) }
    val primária = MaterialTheme.colorScheme.primary
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = titulo, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { dicaAberta = !dicaAberta }, contentAlignment = Alignment.Center) {
                Text("?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        AnimatedVisibility(dicaAberta) {
            Text(text = dica, fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, primária.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            opcoes.forEach { (rotulo, valor) ->
                val isSelected = selecionado == valor
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(if (isSelected) primária.copy(alpha = 0.4f) else Color.Transparent).clickable { onSelect(valor) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = rotulo, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LinhaInterruptor(titulo: String, dica: String, checado: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var dicaAberta by remember { mutableStateOf(false) }
    val primária = MaterialTheme.colorScheme.primary
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = titulo, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked = checado, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = primária, checkedTrackColor = primária.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { dicaAberta = !dicaAberta }, contentAlignment = Alignment.Center) {
                Text("?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        AnimatedVisibility(dicaAberta) {
            Text(text = dica, fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun LinhaSlider(titulo: String, dica: String, valor: Float, onValueChange: (Float) -> Unit) {
    var dicaAberta by remember { mutableStateOf(false) }
    val primária = MaterialTheme.colorScheme.primary
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = titulo, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text(text = "${(valor * 100).roundToInt()}%", fontSize = 14.sp, color = primária, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { dicaAberta = !dicaAberta }, contentAlignment = Alignment.Center) {
                Text("?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        AnimatedVisibility(dicaAberta) {
            Text(text = dica, fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 8.dp))
        }
        Slider(
            value = valor, onValueChange = { onValueChange((it * 20).roundToInt() / 20f) },
            valueRange = 0f..1f, steps = 19,
            colors = SliderDefaults.colors(thumbColor = primária, activeTrackColor = primária)
        )
    }
}
