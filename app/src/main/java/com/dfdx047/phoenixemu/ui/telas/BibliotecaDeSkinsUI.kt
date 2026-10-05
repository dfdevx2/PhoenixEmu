package com.dfdx047.phoenixemu.ui.telas

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dfdx047.phoenixemu.R
import com.dfdx047.phoenixemu.emulator.RepositorioDeSkins
import com.dfdx047.phoenixemu.emulator.ResultadoLista
import com.dfdx047.phoenixemu.emulator.ResultadoSkin
import com.dfdx047.phoenixemu.emulator.SkinDeArquivo
import com.dfdx047.phoenixemu.emulator.SkinOnline
import com.dfdx047.phoenixemu.emulator.Skins
import com.dfdx047.phoenixemu.ui.design.SuperficieDeVidro
import kotlinx.coroutines.launch

private sealed interface EstadoBiblioteca {
    data object Carregando : EstadoBiblioteca
    data object Falha : EstadoBiblioteca
    data class Pronto(val skins: List<SkinOnline>) : EstadoBiblioteca
}

fun mensagemErroSkin(ctx: Context, codigo: String): String = ctx.getString(
    when (codigo) {
        "rede" -> R.string.skin_biblioteca_erro_rede
        "arquivo_proibido" -> R.string.skin_erro_arquivo_proibido
        "grande" -> R.string.skin_erro_grande
        "sem_manifesto" -> R.string.skin_erro_sem_manifesto
        "manifesto_invalido" -> R.string.skin_erro_manifesto_invalido
        "imagem_invalida" -> R.string.skin_erro_imagem_invalida
        "id_reservado" -> R.string.skin_erro_id_reservado
        "limite" -> R.string.skin_erro_limite
        else -> R.string.skin_erro_zip_invalido
    }
)

@Composable
fun BibliotecaDeSkinsDialog(onFechar: () -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var estado by remember { mutableStateOf<EstadoBiblioteca>(EstadoBiblioteca.Carregando) }
    var tentativa by remember { mutableIntStateOf(0) }
    val ocupadas = remember { mutableStateMapOf<String, Boolean>() }
    var mensagem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tentativa) {
        estado = EstadoBiblioteca.Carregando
        estado = when (val r = RepositorioDeSkins.listar()) {
            is ResultadoLista.Ok -> EstadoBiblioteca.Pronto(r.skins)
            ResultadoLista.Erro -> EstadoBiblioteca.Falha
        }
    }

    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SuperficieDeVidro(
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 480.dp),
            forma = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.skin_biblioteca_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(Modifier.weight(1f, fill = false)) {
                    when (val e = estado) {
                        EstadoBiblioteca.Carregando -> CircularProgressIndicator()
                        EstadoBiblioteca.Falha -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.skin_biblioteca_erro_rede))
                            Button(onClick = { tentativa++ }) { Text(stringResource(R.string.skin_biblioteca_tentar)) }
                        }
                        is EstadoBiblioteca.Pronto ->
                            if (e.skins.isEmpty()) Text(stringResource(R.string.skin_biblioteca_vazia))
                            else LazyColumn {
                                items(e.skins, key = { it.id }) { s ->
                                    val instalada = Skins.todas.filterIsInstance<SkinDeArquivo>().firstOrNull { it.id == s.id }
                                    LinhaSkinOnline(s, instalada, ocupadas[s.id] == true) {
                                        escopo.launch {
                                            ocupadas[s.id] = true
                                            val r = RepositorioDeSkins.baixarEInstalar(ctx, s)
                                            ocupadas.remove(s.id)
                                            mensagem = when (r) {
                                                is ResultadoSkin.Ok -> {
                                                    Skins.recarregar(ctx)
                                                    ctx.getString(R.string.skin_importada, r.skin.nome)
                                                }
                                                is ResultadoSkin.Erro -> mensagemErroSkin(ctx, r.codigo)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
                mensagem?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = onFechar) { Text(stringResource(R.string.skin_biblioteca_fechar)) }
            }
        }
    }
}

@Composable
private fun LinhaSkinOnline(skin: SkinOnline, instalada: SkinDeArquivo?, ocupada: Boolean, onInstalar: () -> Unit) {
    val atualizavel = instalada != null && instalada.versao < skin.versao
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(skin.nome, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.skin_biblioteca_por, skin.autor.ifBlank { "?" }, skin.versao),
                style = MaterialTheme.typography.bodySmall
            )
            if (skin.descricao.isNotBlank()) Text(skin.descricao, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onInstalar, enabled = !ocupada && (instalada == null || atualizavel)) {
            Text(
                stringResource(
                    when {
                        ocupada -> R.string.skin_biblioteca_baixando
                        instalada == null -> R.string.skin_biblioteca_instalar
                        atualizavel -> R.string.skin_biblioteca_atualizar
                        else -> R.string.skin_biblioteca_instalada
                    }
                )
            )
        }
    }
}
