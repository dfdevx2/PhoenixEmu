package com.dfdx047.phoenixemu

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.HttpURLConnection
import java.net.URL

// =====================================================================
// ANALISADOR SINTÁTICO DE ROMS (ROM PARSER)
// =====================================================================
data class RomAnalisada(val nomeLimpo: String, val regiao: String, val nomeLibretro: String)

object RomParser {
    fun analisar(nomeArquivoBruto: String): RomAnalisada {
        val regexRegiao = Regex("\\((USA|Japan|Europe|World|Asia|Brazil|En,Fr,De.*?)\\)", RegexOption.IGNORE_CASE)
        val matchRegiao = regexRegiao.find(nomeArquivoBruto)
        val regiaoEncontrada = matchRegiao?.groupValues?.get(1) ?: "Desconhecida"

        val nomeLimpo = nomeArquivoBruto.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
        val nomeLibretro = nomeArquivoBruto.replace(Regex("\\[.*?\\]"), "").trim()

        return RomAnalisada(nomeLimpo, regiaoEncontrada, nomeLibretro)
    }

    fun gerarCapaRetroArch(nomeLibretro: String, sistema: String): String {
        val baseUrl = "https://thumbnails.libretro.com/"
        val systemPath = if (sistema == "NES") "Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/" else "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/"
        val nomeCodificado = Uri.encode(nomeLibretro)
        return "$baseUrl$systemPath$nomeCodificado.png"
    }
}

// =====================================================================
// INTERFACE DA API DO RAWG
// =====================================================================
interface RawgApi {
    @GET("games")
    suspend fun searchGames(
        @Query("search") query: String,
        @Query("platforms") platforms: String,
        @Query("key") apiKey: String = "COLOQUE_SUA_API_KEY_AQUI"
    ): RawgResponse
}

// =====================================================================
// MOTOR DE PESQUISA HÍBRIDO (RETROARCH + RAWG)
// =====================================================================
object RetroScraper {
    private val api = Retrofit.Builder().baseUrl("https://api.rawg.io/api/").addConverterFactory(GsonConverterFactory.create()).build().create(RawgApi::class.java)

    // A BALA DE PRATA: Tenta o RetroArch primeiro, se falhar usa o RAWG!
    suspend fun buscarCapaDefinitiva(nomeBruto: String, sistema: String): String? {
        val romAnalisada = RomParser.analisar(nomeBruto)
        val urlRetroArch = RomParser.gerarCapaRetroArch(romAnalisada.nomeLibretro, sistema)

        // Testa a conexão para ver se a imagem realmente existe no servidor gringo
        if (verificarUrlExiste(urlRetroArch)) {
            return urlRetroArch
        }

        // Se a imagem não existe, chama o nosso plano B (RAWG)
        return buscarCapaFallback(romAnalisada.nomeLimpo, sistema)
    }

    private suspend fun verificarUrlExiste(urlString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun buscarCapaFallback(nomeLimpo: String, sistema: String): String? {
        val platformId = if (sistema == "NES") "49" else "79"
        return try {
            val response = api.searchGames(query = nomeLimpo, platforms = platformId)
            val resultados = response.results?.filter { it.background_image != null }
            if (resultados.isNullOrEmpty()) return null

            val nomeBuscado = nomeLimpo.lowercase()
            val resultadoIdeal = resultados.firstOrNull {
                val nomeDaApi = it.name?.lowercase() ?: ""
                nomeDaApi.contains(nomeBuscado) || nomeBuscado.contains(nomeDaApi)
            }
            resultadoIdeal?.background_image
        } catch (e: Exception) { null }
    }
}