package com.dfdx047.phoenixemu

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

// =====================================================================
// ANALISADOR DE NOMES DE ROM
// =====================================================================
data class RomAnalisada(
    /** Nome bonito para a UI: sem nenhuma tag. */
    val nomeLimpo: String,
    /** Codigo curto de regiao para o badge do cartao ("USA", "USA/EUR"...). */
    val regiao: String,
    /** Nome no padrao das DATs, usado para montar a URL do Libretro. */
    val nomeLibretro: String
)

object RomParser {

    /**
     * A regex anterior era:
     *   \((USA|Japan|Europe|World|Asia|Brazil|En,Fr,De.*?)\)
     *
     * Dois problemas serios:
     *  1. Exigia uma unica regiao dentro dos parenteses, entao os nomes
     *     No-Intro mais comuns nao casavam: "(USA, Europe)",
     *     "(Europe, Australia)", "(Japan, USA)".
     *  2. "En,Fr,De" e lista de IDIOMAS, nao regiao. Jogo europeu
     *     multilingue era rotulado com o idioma no lugar da regiao.
     *
     * Agora percorremos cada grupo entre parenteses, quebramos por virgula
     * e so aceitamos termos que sejam realmente regioes. Lista de idiomas
     * nao casa com nada e e ignorada.
     */
    private val REGIOES: Map<String, String> = mapOf(
        // No-Intro
        "usa" to "USA",
        "europe" to "EUR",
        "japan" to "JPN",
        "world" to "WLD",
        "asia" to "ASI",
        "brazil" to "BRA",
        "korea" to "KOR",
        "china" to "CHN",
        "taiwan" to "TWN",
        "australia" to "AUS",
        "canada" to "CAN",
        "france" to "FRA",
        "germany" to "GER",
        "spain" to "ESP",
        "italy" to "ITA",
        "netherlands" to "NLD",
        "sweden" to "SWE",
        "russia" to "RUS",
        "hong kong" to "HK",
        "united kingdom" to "UK",
        "uk" to "UK",
        "scandinavia" to "SCN",
        "latin america" to "LTN",
        // GoodNES / GoodSNES antigos: (U), (E), (J), (W)
        "u" to "USA",
        "e" to "EUR",
        "j" to "JPN",
        "w" to "WLD",
        "b" to "BRA",
        "k" to "KOR",
        "a" to "AUS"
    )

    private val GRUPOS_PARENTESES = Regex("\\(([^()]*)\\)")
    private val TODAS_AS_TAGS = Regex("\\([^()]*\\)|\\[[^\\[\\]]*\\]")
    private val TAGS_COLCHETES = Regex("\\[[^\\[\\]]*\\]")
    private val ESPACOS_REPETIDOS = Regex("\\s{2,}")

    fun analisar(nomeArquivoBruto: String): RomAnalisada {
        val regiao = extrairRegiao(nomeArquivoBruto)

        val nomeLimpo = nomeArquivoBruto
            .replace(TODAS_AS_TAGS, " ")
            .replace(ESPACOS_REPETIDOS, " ")
            .trim()
            .trim('-', '_', '.', ' ')
            .ifBlank { nomeArquivoBruto.trim() }

        // Para o Libretro mantemos as tags entre parenteses (fazem parte do
        // nome na DAT) e removemos apenas as de colchete, que sao marcas de
        // dump ("[!]", "[b1]", "[T+Por]") e nunca aparecem no repositorio.
        val nomeLibretro = nomeArquivoBruto
            .replace(TAGS_COLCHETES, " ")
            .replace(ESPACOS_REPETIDOS, " ")
            .trim()

        return RomAnalisada(nomeLimpo, regiao, nomeLibretro)
    }

    private fun extrairRegiao(nome: String): String {
        for (grupo in GRUPOS_PARENTESES.findAll(nome)) {
            val termos = grupo.groupValues[1].split(',')
            val achadas = LinkedHashSet<String>()
            for (termo in termos) {
                REGIOES[termo.trim().lowercase()]?.let(achadas::add)
            }
            if (achadas.isNotEmpty()) {
                // No maximo dois codigos no badge; "USA/EUR/JPN" nao cabe.
                return achadas.take(2).joinToString("/")
            }
        }
        return Jogo.REGIAO_DESCONHECIDA
    }

    /**
     * Os arquivos no servidor de thumbnails do Libretro trocam por "_" os
     * caracteres que nao valem em nome de arquivo. Sem essa substituicao,
     * todo jogo com ":" ou "?" no titulo ("Sim City 2000: ...",
     * "Where in the World...?") dava 404 e caia no fallback sem necessidade.
     */
    private val PROIBIDOS = charArrayOf('&', '*', '/', ':', '`', '<', '>', '?', '\\', '|', '"')

    private fun sanitizarParaLibretro(nome: String): String {
        val sb = StringBuilder(nome.length)
        for (c in nome) sb.append(if (c in PROIBIDOS) '_' else c)
        return sb.toString()
    }

    private fun pastaDoSistema(sistema: Sistema): String = when (sistema) {
        Sistema.NES -> "Nintendo - Nintendo Entertainment System"
        Sistema.SNES -> "Nintendo - Super Nintendo Entertainment System"
    }

    fun urlCapaLibretro(nomeLibretro: String, sistema: Sistema): String {
        val pasta = Uri.encode(pastaDoSistema(sistema))
        val arquivo = Uri.encode(sanitizarParaLibretro(nomeLibretro))
        return "https://thumbnails.libretro.com/$pasta/Named_Boxarts/$arquivo.png"
    }
}

// =====================================================================
// API DA RAWG (fallback)
// =====================================================================
interface RawgApi {
    @GET("games")
    suspend fun searchGames(
        @Query("search") query: String,
        @Query("platforms") platforms: String,
        @Query("page_size") pageSize: Int = 5,
        @Query("key") apiKey: String
    ): RawgResponse
}

// =====================================================================
// SCRAPER HIBRIDO (LIBRETRO + RAWG)
// =====================================================================
object RetroScraper {

    private const val TAG = "RetroScraper"

    /**
     * Cliente unico. A versao anterior abria um HttpURLConnection por ROM e
     * nunca chamava disconnect(), entao as conexoes ficavam presas. Com um
     * OkHttpClient compartilhado ha pool de conexoes e reaproveitamento de
     * TLS, o que sozinho corta boa parte do tempo de varredura.
     */
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val rawgDisponivel: Boolean
        get() = BuildConfig.RAWG_API_KEY.isNotBlank()

    private val rawg: RawgApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.rawg.io/api/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RawgApi::class.java)
    }

    /**
     * Tenta o Libretro (boxart de verdade) e, so se falhar, a RAWG.
     *
     * Aviso sobre a RAWG: o campo `background_image` e arte promocional ou
     * captura de tela, nao capa de caixa. Misturado com os boxarts do
     * Libretro, o resultado e uma grade visualmente inconsistente. Fica
     * como rede de seguranca para mods e bootlegs obscuros; na Fase 1 o
     * ideal e trocar por uma fonte de boxart (ScreenScraper ou
     * libretro-thumbnails alternativos).
     */
    suspend fun buscarCapa(nomeBruto: String, sistema: Sistema): String? {
        val analise = RomParser.analisar(nomeBruto)

        // Candidato 1: nome completo da DAT, com as tags de regiao.
        // Candidato 2: nome limpo, para arquivos renomeados pelo usuario.
        val candidatos = linkedSetOf(
            RomParser.urlCapaLibretro(analise.nomeLibretro, sistema),
            RomParser.urlCapaLibretro(analise.nomeLimpo, sistema)
        )

        urlPeloIndice(analise.nomeLimpo, sistema)?.let { return it }

        for (url in candidatos) {
            if (existe(url)) return url
        }

        if (!rawgDisponivel) return null
        return buscarNaRawg(analise.nomeLimpo, sistema)
    }

    private val indices = java.util.concurrent.ConcurrentHashMap<Sistema, Map<String, List<String>>>()
    private val falhasDeIndice = java.util.concurrent.ConcurrentHashMap<Sistema, Long>()
    private val travaIndice = kotlinx.coroutines.sync.Mutex()

    private fun chaveDeNome(nome: String): String {
        var t = nome.lowercase()
        t = t.replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\[[^\\]]*\\]"), " ")
        t = t.replace(Regex(",\\s*the\\s*$"), "").replace(Regex("^\\s*the\\s+"), "")
        t = t.replace(Regex("\\band\\b"), " ")
        t = t.replace(Regex("\\b(viii|vii|iii|ii|iv|vi|ix|v)\\b")) { m ->
            when (m.value) {
                "ii" -> "2"; "iii" -> "3"; "iv" -> "4"; "v" -> "5"
                "vi" -> "6"; "vii" -> "7"; "viii" -> "8"; "ix" -> "9"
                else -> m.value
            }
        }
        return t.replace(Regex("[^a-z0-9]"), "")
    }

    private fun pastaLibretro(sistema: Sistema): String =
        RomParser.urlCapaLibretro("x", sistema).substringBeforeLast("/") + "/"

    private suspend fun indiceDe(sistema: Sistema): Map<String, List<String>> {
        indices[sistema]?.let { return it }
        travaIndice.lock()
        try {
            indices[sistema]?.let { return it }
            val agora = System.currentTimeMillis()
            val falhou = falhasDeIndice[sistema]
            if (falhou != null && agora - falhou < 30_000) return emptyMap()
            val mapa = HashMap<String, MutableList<String>>()
            try {
                val cliente = http.newBuilder()
                    .callTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val html = withContext(Dispatchers.IO) {
                    cliente.newCall(Request.Builder().url(pastaLibretro(sistema)).build())
                        .execute().use { r -> if (r.isSuccessful) (r.body?.string() ?: "") else "" }
                }
                Regex("href=\"([^\"]+?)\\.png\"").findAll(html).forEach { m ->
                    val arq = Uri.decode(m.groupValues[1])
                    mapa.getOrPut(chaveDeNome(arq)) { mutableListOf() }.add(arq)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Indice do Libretro falhou", e)
            }
            if (mapa.isNotEmpty()) indices[sistema] = mapa else falhasDeIndice[sistema] = agora
            Log.w(TAG, "Indice do Libretro $sistema: ${mapa.size} jogos")
            return mapa
        } finally {
            travaIndice.unlock()
        }
    }

    private fun distanciaDeEdicao(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val custo = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + custo)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun achaAproximado(ind: Map<String, List<String>>, k: String): List<String>? {
        if (k.length < 6) return null
        val max = maxOf(2, k.length / 8)
        val digitos = k.filter { it.isDigit() }
        var melhor: String? = null
        var menor = max + 1
        for (c in ind.keys) {
            if (kotlin.math.abs(c.length - k.length) > max) continue
            if (c.filter { it.isDigit() } != digitos) continue
            val d = distanciaDeEdicao(k, c)
            if (d < menor) { menor = d; melhor = c }
        }
        return melhor?.let { ind[it] }
    }

    private suspend fun urlPeloIndice(nome: String, sistema: Sistema): String? {
        val indice = indiceDe(sistema)
        val chave = chaveDeNome(nome)
        val lista = indice[chave] ?: achaAproximado(indice, chave) ?: return null
        val ruins = Regex("\\((beta|proto|demo|sample|unl|pirate|alt|rev)")
        fun nota(a: String): Int {
            val l = a.lowercase()
            var n = when {
                "(usa" in l -> 0
                "(world" in l -> 1
                "(europe" in l -> 2
                else -> 3
            }
            if (ruins.containsMatchIn(l)) n += 10
            return n
        }
        val melhor = lista.minByOrNull { nota(it) } ?: return null
        return pastaLibretro(sistema) + Uri.encode(melhor) + ".png"
    }

    private suspend fun existe(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pedido = Request.Builder().url(url).head().build()
            // `use` fecha o corpo da resposta, o que devolve a conexao ao pool.
            http.newCall(pedido).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Baixa a capa para um arquivo local.
     *
     * A lista de jogos passa a ler a capa do disco do proprio app, entao ela
     * nunca depende da rede nem do cache do Coil (que e limitado e
     * descartavel). Escrita atomica: temporario + rename, para que uma queda
     * de conexao nao deixe um PNG truncado no lugar da capa.
     */
    suspend fun baixarCapa(url: String, destino: File): Boolean = withContext(Dispatchers.IO) {
        val temporario = File(destino.parentFile, destino.name + ".tmp")
        try {
            destino.parentFile?.mkdirs()
            val pedido = Request.Builder().url(url).build()
            http.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) return@withContext false
                val corpo = resposta.body ?: return@withContext false
                corpo.byteStream().use { entrada ->
                    temporario.outputStream().use { saida -> entrada.copyTo(saida, 64 * 1024) }
                }
            }
            if (temporario.length() == 0L) {
                temporario.delete()
                return@withContext false
            }
            if (destino.exists()) destino.delete()
            temporario.renameTo(destino)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar capa: $url", e)
            temporario.delete()
            false
        }
    }

    private suspend fun buscarNaRawg(nomeLimpo: String, sistema: Sistema): String? {
        val plataforma = when (sistema) {
            Sistema.NES -> "49"
            Sistema.SNES -> "79"
        }
        return try {
            val resposta = rawg.searchGames(
                query = nomeLimpo,
                platforms = plataforma,
                apiKey = BuildConfig.RAWG_API_KEY
            )
            val comImagem = resposta.results?.filter { !it.background_image.isNullOrBlank() }
            if (comImagem.isNullOrEmpty()) return null

            val buscado = nomeLimpo.lowercase()
            val melhor = comImagem.firstOrNull {
                val daApi = it.name?.lowercase() ?: ""
                daApi == buscado || daApi.contains(buscado) || buscado.contains(daApi)
            }
            melhor?.background_image
        } catch (e: Exception) {
            Log.w(TAG, "RAWG falhou para \"$nomeLimpo\"", e)
            null
        }
    }
}
