package com.dfdx047.phoenixemu.ui.telas

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dfdx047.phoenixemu.data.OverlayConfigNova
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BotaoExportarImportarLayout(
    configAtual: OverlayConfigNova,
    onImportar: (OverlayConfigNova) -> Unit
) {
    val ctx = LocalContext.current
    val gson = Gson()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(gson.toJson(configAtual).toByteArray())
                    }
                } catch (e: Exception) {
                    Log.e("PhoenixAjustes", "Erro exportando layout", e)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { inp ->
                        val text = inp.bufferedReader().readText()
                        val cfg = gson.fromJson(text, OverlayConfigNova::class.java)
                        if (cfg != null && cfg.controles != null) {
                            onImportar(cfg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PhoenixAjustes", "Erro importando layout", e)
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = { exportLauncher.launch("meu_layout_phoenix.json") }) {
            Text("Exportar JSON")
        }
        Button(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
            Text("Importar JSON")
        }
    }
}
