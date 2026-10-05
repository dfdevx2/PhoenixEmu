package com.dfdx047.phoenixemu.emulator

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class SkinOnline(
    val id: String,
    val nome: String,
    val autor: String,
    val versao: Int,
    val descricao: String,
    val zip: String
)

private data class SkinOnlineJson(
    val id: String? = null,
    val nome: String? = null,
    val autor: String? = null,
    val versao: Int? = null,
    val descricao: String? = null,
    val zip: String? = null
)

private data class IndiceJson(val skins: List<SkinOnlineJson>? = null)

sealed interface ResultadoLista {
    data class Ok(val skins: List<SkinOnline>) : ResultadoLista
    data object Erro : ResultadoLista
}

/**
 * Catalogo de skins da comunidade, hospedado num repositorio do GitHub.
 * So caminhos relativos e fixos dentro desse repositorio sao aceitos.
 */
object RepositorioDeSkins {
    const val URL_BASE = "https://raw.githubusercontent.com/dfdx047/phoenix-emu-skins/main/"
    private const val MAX_INDICE = 256 * 1024
    private const val MAX_ZIP = 6 * 1024 * 1024
    private const val MAX_ITENS = 100
    private val ID_OK = Regex("^[a-z0-9_]{3,40}$")
    private val ZIP_OK = Regex("^skins/[a-z0-9_]{3,40}\\.zip$")

    private fun baixar(caminho: String, limite: Int): ByteArray? {
        val c = URL(URL_BASE + caminho).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) return null
            c.inputStream.use { ins ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val r = ins.read(buf)
                    if (r < 0) break
                    total += r
                    if (total > limite) return null
                    out.write(buf, 0, r)
                }
                return out.toByteArray()
            }
        } catch (e: Exception) {
            return null
        } finally {
            c.disconnect()
        }
    }

    suspend fun listar(): ResultadoLista = withContext(Dispatchers.IO) {
        val bytes = baixar("index.json", MAX_INDICE) ?: return@withContext ResultadoLista.Erro
        val indice = runCatching { Gson().fromJson(String(bytes, Charsets.UTF_8), IndiceJson::class.java) }.getOrNull()
            ?: return@withContext ResultadoLista.Erro
        val lista = indice.skins.orEmpty().take(MAX_ITENS).mapNotNull { j ->
            val id = j.id ?: return@mapNotNull null
            val zip = j.zip ?: return@mapNotNull null
            if (!ID_OK.matches(id) || !ZIP_OK.matches(zip)) return@mapNotNull null
            SkinOnline(
                id = id,
                nome = (j.nome ?: id).trim().take(40).ifBlank { id },
                autor = (j.autor ?: "").trim().take(40),
                versao = j.versao ?: 1,
                descricao = (j.descricao ?: "").trim().take(140),
                zip = zip
            )
        }
        ResultadoLista.Ok(lista)
    }

    /** Baixa o zip (limitado) e passa pela mesma validacao do importador manual. */
    suspend fun baixarEInstalar(ctx: Context, skin: SkinOnline): ResultadoSkin = withContext(Dispatchers.IO) {
        val bytes = baixar(skin.zip, MAX_ZIP) ?: return@withContext ResultadoSkin.Erro("rede")
        val r = CarregadorDeSkins.instalarZip(ctx, ByteArrayInputStream(bytes))
        if (r is ResultadoSkin.Ok && r.skin.id != skin.id) {
            CarregadorDeSkins.remover(ctx, r.skin.id)
            return@withContext ResultadoSkin.Erro("manifesto_invalido", "id")
        }
        r
    }
}
