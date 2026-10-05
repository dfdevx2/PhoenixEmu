package com.dfdx047.phoenixemu.ra

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Credenciais do RA. Mesmo arquivo de prefs da tela antiga, para nao deslogar ninguem. */
object RaCredenciais {
    private const val ARQ = "RetroAchievementsPrefs"
    fun usuario(c: Context): String = c.getSharedPreferences(ARQ, Context.MODE_PRIVATE).getString("username", "") ?: ""
    fun chave(c: Context): String = c.getSharedPreferences(ARQ, Context.MODE_PRIVATE).getString("api_key", "") ?: ""
    fun logado(c: Context): Boolean = c.getSharedPreferences(ARQ, Context.MODE_PRIVATE).getBoolean("isLogged", false)
    fun salvar(c: Context, usuario: String, chave: String) {
        c.getSharedPreferences(ARQ, Context.MODE_PRIVATE).edit()
            .putString("username", usuario).putString("api_key", chave).putBoolean("isLogged", true).apply()
    }
    fun sair(c: Context) {
        c.getSharedPreferences(ARQ, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

private data class IdEntrada(val id: Int, val em: Long)
private data class CacheLista(val itens: List<RaResumoJogo>, val em: Long)

/** Cache em disco por cima da RaApi. Se a rede falhar, devolve o cache velho em vez de erro. */
class RaRepositorio(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "ra").apply { mkdirs() }
    private val gson = Gson()
    private val mutex = Mutex()

    private companion object {
        const val TTL_PROGRESSO = 10 * 60_000L
        const val TTL_NEGATIVO = 24 * 3_600_000L
    }

    private fun seguro(s: String) = s.lowercase().replace(Regex("[^a-z0-9_]"), "_")

    private fun gravar(f: File, texto: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(texto)
        tmp.renameTo(f)
    }

    private fun lerIds(): MutableMap<String, IdEntrada> = runCatching {
        val f = File(dir, "ids.json")
        if (!f.exists()) return@runCatching mutableMapOf<String, IdEntrada>()
        val t = object : TypeToken<MutableMap<String, IdEntrada>>() {}.type
        gson.fromJson<MutableMap<String, IdEntrada>>(f.readText(), t) ?: mutableMapOf()
    }.getOrDefault(mutableMapOf())

    /** hash do RA -> id do jogo no RA. Positivos ficam para sempre; "sem suporte" expira em 24h. */
    suspend fun idDoJogo(hash: String): RaResultado<Int> = withContext(Dispatchers.IO) {
        val agora = System.currentTimeMillis()
        val cacheado = mutex.withLock { lerIds()[hash] }
        if (cacheado != null) {
            if (cacheado.id > 0) return@withContext RaResultado.Ok(cacheado.id, true)
            if (agora - cacheado.em < TTL_NEGATIVO) return@withContext RaResultado.Erro(RaErro.JOGO_SEM_SUPORTE)
        }
        when (val r = RaApi.idDoJogoPorHash(hash)) {
            is RaResultado.Erro -> r
            is RaResultado.Ok -> {
                mutex.withLock {
                    val m = lerIds()
                    m[hash] = IdEntrada(r.valor, agora)
                    gravar(File(dir, "ids.json"), gson.toJson(m))
                }
                if (r.valor > 0) RaResultado.Ok(r.valor) else RaResultado.Erro(RaErro.JOGO_SEM_SUPORTE)
            }
        }
    }

    suspend fun progresso(usuario: String, chave: String, idRa: Int, forcar: Boolean = false): RaResultado<RaJogoProgresso> =
        withContext(Dispatchers.IO) {
            val f = File(dir, "jogo_${seguro(usuario)}_$idRa.json")
            val velho = runCatching { gson.fromJson(f.readText(), RaJogoProgresso::class.java) }.getOrNull()
            val agora = System.currentTimeMillis()
            if (!forcar && velho != null && agora - velho.atualizadoEm < TTL_PROGRESSO) {
                return@withContext RaResultado.Ok(velho, true)
            }
            when (val r = RaApi.progressoDoJogo(usuario, chave, idRa)) {
                is RaResultado.Ok -> {
                    runCatching { gravar(f, gson.toJson(r.valor)) }
                    r
                }
                is RaResultado.Erro ->
                    if (velho != null && r.tipo in listOf(RaErro.SEM_REDE, RaErro.SERVIDOR, RaErro.LIMITE)) RaResultado.Ok(velho, true) else r
            }
        }

    suspend fun lista(usuario: String, chave: String, forcar: Boolean = false): RaResultado<List<RaResumoJogo>> =
        withContext(Dispatchers.IO) {
            val f = File(dir, "lista_${seguro(usuario)}.json")
            val velho = runCatching { gson.fromJson(f.readText(), CacheLista::class.java) }.getOrNull()
            val agora = System.currentTimeMillis()
            if (!forcar && velho != null && agora - velho.em < TTL_PROGRESSO) {
                return@withContext RaResultado.Ok(velho.itens, true)
            }
            when (val r = RaApi.listaDeProgresso(usuario, chave)) {
                is RaResultado.Ok -> {
                    runCatching { gravar(f, gson.toJson(CacheLista(r.valor, agora))) }
                    r
                }
                is RaResultado.Erro ->
                    if (velho != null && r.tipo in listOf(RaErro.SEM_REDE, RaErro.SERVIDOR, RaErro.LIMITE)) RaResultado.Ok(velho.itens, true) else r
            }
        }

    fun limparCache() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
