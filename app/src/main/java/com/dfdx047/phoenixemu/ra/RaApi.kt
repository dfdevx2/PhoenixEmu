package com.dfdx047.phoenixemu.ra

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class RaErro { SEM_REDE, CHAVE_INVALIDA, NAO_ENCONTRADO, JOGO_SEM_SUPORTE, LIMITE, SERVIDOR, RESPOSTA_INVALIDA }

sealed class RaResultado<out T> {
    data class Ok<T>(val valor: T, val doCache: Boolean = false) : RaResultado<T>()
    data class Erro(val tipo: RaErro, val detalhe: String? = null) : RaResultado<Nothing>()
}

object RaConsole {
    const val SNES = 3
    const val NES = 7
}

data class RaPerfil(val usuario: String, val pontos: Int, val pontosSoftcore: Int, val avatarUrl: String?)

data class RaConquista(
    val id: Int,
    val titulo: String,
    val descricao: String,
    val pontos: Int,
    val badge: String,
    val ordem: Int,
    val desbloqueada: Boolean,
    val hardcore: Boolean,
    val dataDesbloqueio: String?,
    val tipo: String?,
    val raridadePct: Float
) {
    val urlIcone: String get() = RaApi.urlBadge(badge, !desbloqueada)
}

data class RaJogoProgresso(
    val idRa: Int,
    val titulo: String,
    val consoleId: Int,
    val consoleNome: String,
    val iconeUrl: String?,
    val totalConquistas: Int,
    val desbloqueadas: Int,
    val desbloqueadasHardcore: Int,
    val pontosTotal: Int,
    val pontosGanhos: Int,
    val tempoMin: Int,
    val conquistas: List<RaConquista>,
    val atualizadoEm: Long
) {
    val fracao: Float get() = if (totalConquistas > 0) desbloqueadas.toFloat() / totalConquistas else 0f
}

data class RaResumoJogo(
    val idRa: Int,
    val titulo: String,
    val iconeUrl: String?,
    val consoleId: Int,
    val consoleNome: String,
    val maximo: Int,
    val ganhas: Int,
    val ganhasHardcore: Int,
    val ultimaData: String?,
    val premio: String?
) {
    val fracao: Float get() = if (maximo > 0) ganhas.toFloat() / maximo else 0f
}

private fun JsonElement.objOuNull(): JsonObject? = if (isJsonObject) asJsonObject else null
private fun JsonObject.str(k: String): String? =
    get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
private fun JsonObject.int(k: String, def: Int = 0): Int = str(k)?.toDoubleOrNull()?.toInt() ?: def

object RaApi {
    private const val BASE = "https://retroachievements.org"
    private const val MIDIA = "https://media.retroachievements.org"
    private const val UA = "PhoenixEmu/1.0 (Android)"

    fun urlImagem(caminho: String?): String? {
        if (caminho.isNullOrBlank()) return null
        if (caminho.startsWith("http")) return caminho
        return MIDIA + (if (caminho.startsWith("/")) caminho else "/$caminho")
    }

    fun urlBadge(badge: String, bloqueada: Boolean): String =
        "$MIDIA/Badge/$badge${if (bloqueada) "_lock" else ""}.png"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String): RaResultado<JsonElement> = withContext(Dispatchers.IO) {
        var con: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            con = c
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code == 401 || code == 403) return@withContext RaResultado.Erro(RaErro.CHAVE_INVALIDA)
            if (code == 404) return@withContext RaResultado.Erro(RaErro.NAO_ENCONTRADO)
            if (code == 429) return@withContext RaResultado.Erro(RaErro.LIMITE)
            if (code !in 200..299) return@withContext RaResultado.Erro(RaErro.SERVIDOR, "HTTP $code")
            val texto = c.inputStream.bufferedReader().use { it.readText() }
            RaResultado.Ok(JsonParser.parseString(texto))
        } catch (e: IOException) {
            RaResultado.Erro(RaErro.SEM_REDE, e.message)
        } catch (e: Exception) {
            RaResultado.Erro(RaErro.RESPOSTA_INVALIDA, e.message)
        } finally {
            con?.disconnect()
        }
    }

    /** Valida usuario + chave da Web API. */
    suspend fun validarConta(usuario: String, chave: String): RaResultado<RaPerfil> {
        val r = get("$BASE/API/API_GetUserProfile.php?u=${enc(usuario)}&y=${enc(chave)}")
        return when (r) {
            is RaResultado.Erro -> r
            is RaResultado.Ok -> {
                val o = r.valor.objOuNull() ?: return RaResultado.Erro(RaErro.RESPOSTA_INVALIDA)
                val nome = o.str("User") ?: return RaResultado.Erro(RaErro.NAO_ENCONTRADO)
                RaResultado.Ok(
                    RaPerfil(
                        usuario = nome,
                        pontos = o.int("TotalPoints"),
                        pontosSoftcore = o.int("TotalSoftcorePoints"),
                        avatarUrl = urlImagem(o.str("UserPic"))
                    )
                )
            }
        }
    }

    /** hash (MD5 do RA) -> id do jogo. Endpoint publico, sem chave. Ok(0) = jogo sem suporte. */
    suspend fun idDoJogoPorHash(hash: String): RaResultado<Int> {
        val r = get("$BASE/dorequest.php?r=gameid&m=${enc(hash)}")
        return when (r) {
            is RaResultado.Erro -> r
            is RaResultado.Ok -> {
                val o = r.valor.objOuNull() ?: return RaResultado.Erro(RaErro.RESPOSTA_INVALIDA)
                RaResultado.Ok(o.int("GameID"))
            }
        }
    }

    suspend fun progressoDoJogo(usuario: String, chave: String, idRa: Int): RaResultado<RaJogoProgresso> {
        val r = get("$BASE/API/API_GetGameInfoAndUserProgress.php?u=${enc(usuario)}&y=${enc(chave)}&g=$idRa")
        return when (r) {
            is RaResultado.Erro -> r
            is RaResultado.Ok -> {
                val o = r.valor.objOuNull() ?: return RaResultado.Erro(RaErro.RESPOSTA_INVALIDA)
                if (o.str("Title") == null) return RaResultado.Erro(RaErro.NAO_ENCONTRADO)
                val jogadores = o.int("NumDistinctPlayers").coerceAtLeast(1)
                val lista = mutableListOf<RaConquista>()
                val ach = o.get("Achievements")
                if (ach != null && ach.isJsonObject) {
                    for ((_, v) in ach.asJsonObject.entrySet()) {
                        val a = v.objOuNull() ?: continue
                        val data = a.str("DateEarned")
                        val dataHc = a.str("DateEarnedHardcore")
                        val raros = a.int("NumAwarded")
                        lista.add(
                            RaConquista(
                                id = a.int("ID"),
                                titulo = a.str("Title") ?: "",
                                descricao = a.str("Description") ?: "",
                                pontos = a.int("Points"),
                                badge = a.str("BadgeName") ?: "00000",
                                ordem = a.int("DisplayOrder", a.int("ID")),
                                desbloqueada = data != null || dataHc != null,
                                hardcore = dataHc != null,
                                dataDesbloqueio = dataHc ?: data,
                                tipo = a.str("type"),
                                raridadePct = raros * 100f / jogadores
                            )
                        )
                    }
                }
                lista.sortBy { it.ordem }
                RaResultado.Ok(
                    RaJogoProgresso(
                        idRa = idRa,
                        titulo = o.str("Title") ?: "",
                        consoleId = o.int("ConsoleID"),
                        consoleNome = o.str("ConsoleName") ?: "",
                        iconeUrl = urlImagem(o.str("ImageIcon")),
                        totalConquistas = lista.size,
                        desbloqueadas = lista.count { it.desbloqueada },
                        desbloqueadasHardcore = lista.count { it.hardcore },
                        pontosTotal = lista.sumOf { it.pontos },
                        pontosGanhos = lista.filter { it.desbloqueada }.sumOf { it.pontos },
                        tempoMin = o.int("UserTotalPlaytime"),
                        conquistas = lista,
                        atualizadoEm = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /** Todos os jogos com progresso do usuario (ate ~1500, paginado de 500 em 500). */
    suspend fun listaDeProgresso(usuario: String, chave: String): RaResultado<List<RaResumoJogo>> {
        val todos = mutableListOf<RaResumoJogo>()
        var offset = 0
        repeat(3) {
            val r = get("$BASE/API/API_GetUserCompletionProgress.php?u=${enc(usuario)}&y=${enc(chave)}&c=500&o=$offset")
            when (r) {
                is RaResultado.Erro -> return r
                is RaResultado.Ok -> {
                    val o = r.valor.objOuNull() ?: return RaResultado.Erro(RaErro.RESPOSTA_INVALIDA)
                    val total = o.int("Total")
                    val itens = o.get("Results")
                    if (itens != null && itens.isJsonArray) {
                        for (e in itens.asJsonArray) {
                            val j = e.objOuNull() ?: continue
                            todos.add(
                                RaResumoJogo(
                                    idRa = j.int("GameID"),
                                    titulo = j.str("Title") ?: "",
                                    iconeUrl = urlImagem(j.str("ImageIcon")),
                                    consoleId = j.int("ConsoleID"),
                                    consoleNome = j.str("ConsoleName") ?: "",
                                    maximo = j.int("MaxPossible"),
                                    ganhas = j.int("NumAwarded"),
                                    ganhasHardcore = j.int("NumAwardedHardcore"),
                                    ultimaData = j.str("MostRecentAwardedDate"),
                                    premio = j.str("HighestAwardKind")
                                )
                            )
                        }
                    }
                    offset += 500
                    if (offset >= total) return RaResultado.Ok(todos.toList())
                }
            }
        }
        return RaResultado.Ok(todos.toList())
    }
}
