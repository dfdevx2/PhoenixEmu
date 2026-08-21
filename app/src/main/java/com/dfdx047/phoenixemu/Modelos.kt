package com.dfdx047.phoenixemu

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// =====================================================================
// TEMAS DO APLICATIVO
// =====================================================================
enum class TemaApp {
    DINAMICO, CLARO, ESCURO, AMOLED, NES_US, NES_JP, SNES_US, SNES_JP
}

fun obterNomeDoTema(tema: TemaApp): String {
    return when(tema) {
        TemaApp.DINAMICO -> "Material You (Sistema)"
        TemaApp.CLARO -> "Tema Claro"
        TemaApp.ESCURO -> "Tema Escuro"
        TemaApp.AMOLED -> "Preto AMOLED Absoluto"
        TemaApp.NES_US -> "NES (Nintendinho Americano)"
        TemaApp.NES_JP -> "Famicom (Japonês)"
        TemaApp.SNES_US -> "Super Nintendo (Americano)"
        TemaApp.SNES_JP -> "Super Famicom (Japonês)"
    }
}

// =====================================================================
// MODELOS GLOBAIS DO EMULADOR
// =====================================================================
data class Jogo(
    val nome: String,
    val nomeArquivoOriginal: String,
    val extensao: String,
    val uriString: String, // A MÁGICA AQUI: Guardado como Texto para o Gson conseguir salvar!
    val sistema: String,
    val regiao: String,
    var isFavorito: Boolean = false,
    var ultimaVezJogado: Long = 0L,
    var tempoJogadoMinutos: Int = 0,
    var capaUrl: String? = null
) {
    // Atalho invisível para a interface continuar a usar o Uri normalmente
    val uri: Uri get() = Uri.parse(uriString)
}

data class RetroGameStat(val nome: String, val sistema: String, val conquistasDesbloqueadas: Int, val totalConquistas: Int)

data class RawgResponse(val results: List<RawgGame>?)
data class RawgGame(val name: String?, val background_image: String?)

// =====================================================================
// GESTOR DE BIBLIOTECA (CACHE E PASTAS SALVAS)
// =====================================================================
object BibliotecaManager {
    private val gson = Gson()

    fun salvarJogos(context: Context, jogos: List<Jogo>) {
        val prefs = context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE)
        val json = gson.toJson(jogos)
        prefs.edit().putString("biblioteca_cache", json).apply()
    }

    fun carregarJogos(context: Context): List<Jogo> {
        val prefs = context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE)
        val json = prefs.getString("biblioteca_cache", null) ?: return emptyList()
        val type = object : TypeToken<List<Jogo>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    fun adicionarPastaUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE)
        val pastasAtuais = prefs.getStringSet("pastas_roms_uris", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        pastasAtuais.add(uri.toString())
        prefs.edit().putStringSet("pastas_roms_uris", pastasAtuais).apply()
    }

    fun obterPastasUris(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE)
        val pastasAtuais = prefs.getStringSet("pastas_roms_uris", emptySet()) ?: emptySet()
        return pastasAtuais.map { Uri.parse(it) }
    }
}