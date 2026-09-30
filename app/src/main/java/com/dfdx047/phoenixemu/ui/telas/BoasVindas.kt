package com.dfdx047.phoenixemu.ui.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dfdx047.phoenixemu.Acabamento
import com.dfdx047.phoenixemu.R
import com.dfdx047.phoenixemu.TemaApp
import com.dfdx047.phoenixemu.ui.design.CartaoDeVidro
import com.dfdx047.phoenixemu.ui.design.FundoDoTema
import com.dfdx047.phoenixemu.ui.design.SeletorSegmentado
import com.dfdx047.phoenixemu.ui.theme.esquemaDeAmostra

/**
 * Primeira execucao.
 *
 * Tres perguntas, todas com resposta visivel na hora: o tema e o acabamento
 * sao aplicados enquanto a pessoa escolhe, entao a propria tela e o preview.
 * Nenhuma delas e irreversivel -- tudo reaparece em Ajustes.
 */
@Composable
fun BoasVindas(
    idiomaAtual: String,
    temaAtual: TemaApp,
    acabamentoAtual: Acabamento,
    amoledAtual: Boolean,
    onIdioma: (String) -> Unit,
    onTema: (TemaApp) -> Unit,
    onAcabamento: (Acabamento) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onConcluir: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        FundoDoTema()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Icon(
                painter = painterResource(R.mipmap.ic_launcher_monochrome),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.boasvindas_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            // ---------------------------------------------------- idioma
            CartaoDeVidro {
                Text(stringResource(R.string.boasvindas_idioma), fontWeight = FontWeight.Bold)
                val rotulos = listOf(
                    stringResource(R.string.idioma_sistema),
                    stringResource(R.string.idioma_portugues),
                    stringResource(R.string.idioma_ingles)
                )
                val tags = listOf("", "pt", "en")
                SeletorSegmentado(
                    opcoes = rotulos,
                    indiceSelecionado = tags.indexOf(idiomaAtual).coerceAtLeast(0),
                    onSelecionar = { onIdioma(tags[it]) }
                )
                Text(
                    stringResource(R.string.boasvindas_idioma_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ------------------------------------------------------ tema
            CartaoDeVidro {
                Text(stringResource(R.string.config_tema), fontWeight = FontWeight.Bold)
                TemaApp.entries.forEach { opcao ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = temaAtual == opcao,
                                role = Role.RadioButton,
                                onClick = { onTema(opcao) }
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = temaAtual == opcao, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(opcao.rotulo), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        AmostraRapida(opcao)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.config_amoled), modifier = Modifier.weight(1f))
                    Switch(checked = amoledAtual, onCheckedChange = onAmoled)
                }
            }

            // ------------------------------------------------ acabamento
            CartaoDeVidro {
                Text(stringResource(R.string.config_acabamento), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.config_acabamento_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SeletorSegmentado(
                    opcoes = Acabamento.entries.map { stringResource(it.rotulo) },
                    indiceSelecionado = Acabamento.entries.indexOf(acabamentoAtual),
                    onSelecionar = { onAcabamento(Acabamento.entries[it]) }
                )
            }

            Button(
                onClick = onConcluir,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text(stringResource(R.string.boasvindas_comecar)) }

            Text(
                stringResource(R.string.boasvindas_rodape),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AmostraRapida(tema: TemaApp) {
    val esquema = esquemaDeAmostra(tema)
    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
        listOf(esquema.primary, esquema.tertiary, esquema.surface, esquema.background)
            .forEach { cor ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(cor)
                )
            }
    }
}
