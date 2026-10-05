package com.dfdx047.phoenixemu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Padrao / Ligado / Desligado. O valor guardado e PADRAO, LIGADO ou DESLIGADO. */
@Composable
fun SeletorTresEstados(valor: String, onMudar: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "PADRAO" to R.string.conq_opcao_padrao,
            "LIGADO" to R.string.conq_opcao_ligado,
            "DESLIGADO" to R.string.conq_opcao_desligado
        ).forEach { (v, rotulo) ->
            val sel = valor == v
            val forma = RoundedCornerShape(10.dp)
            val cor = MaterialTheme.colorScheme.primary
            val texto = MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(forma)
                    .background(if (sel) cor.copy(alpha = 0.25f) else texto.copy(alpha = 0.08f))
                    .border(1.dp, if (sel) cor else texto.copy(alpha = 0.2f), forma)
                    .clickable { onMudar(v) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(rotulo),
                    style = MaterialTheme.typography.labelLarge,
                    color = texto,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
