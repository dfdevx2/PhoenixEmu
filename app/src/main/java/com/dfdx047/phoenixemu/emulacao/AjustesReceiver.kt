package com.dfdx047.phoenixemu.emulacao

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.Preferencias
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AjustesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.dfdx047.phoenixemu.AJUSTE_MUDOU") return
        val pendingResult = goAsync()
        
        val idDoJogo = intent.getStringExtra("idDoJogo") ?: ""
        val escopo = intent.getStringExtra("escopo") ?: "GLOBAL" // "GLOBAL" ou "JOGO"
        val acao = intent.getStringExtra("acao") // pode ser "limpar"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Preferencias.obter(context.applicationContext)
                
                when (acao) {
                    "atalho_definir" -> {
                        val nome = intent.getStringExtra("acaoAtalho") ?: return@launch
                        val tecla = intent.getIntExtra("tecla", 0)
                        runCatching { AcaoAtalho.valueOf(nome) }.onSuccess { acaoAtalho ->
                            prefs.definirAtalho(acaoAtalho, tecla)
                            Log.i("PhoenixAjustes", "atalho acao=$acao nome=$nome tecla=$tecla")
                        }
                    }
                    "atalho_usar_hotkey" -> {
                        val nome = intent.getStringExtra("acaoAtalho") ?: return@launch
                        val usar = intent.getBooleanExtra("usar", true)
                        runCatching { AcaoAtalho.valueOf(nome) }.onSuccess { acaoAtalho ->
                            prefs.definirUsarHotkey(acaoAtalho, usar)
                            Log.i("PhoenixAjustes", "atalho acao=$acao nome=$nome tecla=$usar")
                        }
                    }
                    "atalho_hotkey" -> {
                        val tecla = intent.getIntExtra("tecla", 0)
                        prefs.definirHotkey(tecla)
                        Log.i("PhoenixAjustes", "atalho acao=$acao nome=HOTKEY tecla=$tecla")
                    }
                    "atalho_restaurar" -> {
                        prefs.restaurarAtalhos()
                        Log.i("PhoenixAjustes", "atalho acao=$acao nome=restaurar tecla=0")
                    }
                    "botao_definir" -> {
                        val botao = intent.getStringExtra("botao") ?: return@launch
                        val tecla = intent.getIntExtra("tecla", 0)
                        runCatching { BotaoVirtual.valueOf(botao) }.onSuccess { b ->
                            prefs.definirTecla(b, tecla)
                            Log.i("PhoenixAjustes", "botao acao=$acao botao=$botao tecla=$tecla")
                        }
                    }
                    "botao_restaurar" -> {
                        prefs.restaurarMapeamento()
                        Log.i("PhoenixAjustes", "botao acao=$acao nome=restaurar tecla=0")
                    }
                    "overlay_salvar" -> {
                        val json = intent.getStringExtra("json") ?: return@launch
                        val config = com.google.gson.Gson().fromJson(json, com.dfdx047.phoenixemu.data.OverlayConfigNova::class.java)
                        
                        if (escopo == "JOGO" && idDoJogo.isNotEmpty()) {
                            prefs.setAjusteString("aj_jogo_${idDoJogo}_overlay_controle", json)
                            Log.i("PhoenixAjustes", "overlay_salvar JOGO $idDoJogo")
                        } else {
                            prefs.definirOverlay(config)
                            Log.i("PhoenixAjustes", "overlay_salvar GLOBAL")
                        }
                    }
                    "overlay_restaurar" -> {
                        if (escopo == "JOGO" && idDoJogo.isNotEmpty()) {
                            prefs.limparOverrides(listOf("aj_jogo_${idDoJogo}_overlay_controle"))
                            Log.i("PhoenixAjustes", "overlay_restaurar JOGO $idDoJogo")
                        } else {
                            prefs.restaurarOverlay()
                            Log.i("PhoenixAjustes", "overlay_restaurar GLOBAL")
                        }
                    }
                    "limpar" -> {
                        if (idDoJogo.isNotEmpty()) {
                            prefs.limparOverrides(listOf(
                                "aj_jogo_${idDoJogo}_escala",
                                "aj_jogo_${idDoJogo}_proporcao",
                                "aj_jogo_${idDoJogo}_mostrarFps",
                                "aj_jogo_${idDoJogo}_volume",
                                "aj_jogo_${idDoJogo}_mudo",
                                "aj_jogo_${idDoJogo}_velocidadeFF"
                            ))
                            Log.i("PhoenixAjustes", "Limpos overrides do jogo $idDoJogo")
                        }
                    }
                    else -> {
                        val chave = intent.getStringExtra("chave") ?: return@launch
                        val tipo = intent.getStringExtra("tipo") ?: return@launch
                        
                        val prefixo = if (escopo == "JOGO" && idDoJogo.isNotEmpty()) "aj_jogo_${idDoJogo}_" else "aj_global_"
                        val chaveCompleta = prefixo + chave
                        
                        when (tipo) {
                            "string" -> prefs.setAjusteString(chaveCompleta, intent.getStringExtra("valor") ?: "")
                            "int" -> prefs.setAjusteInt(chaveCompleta, intent.getIntExtra("valor", 0))
                            "float" -> prefs.setAjusteFloat(chaveCompleta, intent.getFloatExtra("valor", 0f))
                            "boolean" -> prefs.setAjusteBoolean(chaveCompleta, intent.getBooleanExtra("valor", false))
                        }
                        Log.i("PhoenixAjustes", "Gravado $chaveCompleta = ${intent.extras?.get("valor")}")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
