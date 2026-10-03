package com.dfdx047.phoenixemu.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dfdx047.phoenixemu.Assinatura
import com.dfdx047.phoenixemu.FiltroBiblioteca
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.Ordenacao
import com.dfdx047.phoenixemu.Sistema
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface EstadoScan {
    data object Parado : EstadoScan

    data class EmAndamento(
        val etapa: Etapa,
        val progresso: Float,
        val bloqueante: Boolean
    ) : EstadoScan

    enum class Etapa { LENDO_PASTAS, IDENTIFICANDO }
}

data class ResumoSincronizacao(
    val adicionados: Int,
    val atualizados: Int,
    val ausentes: Int,
    val reencontrados: Int,
    val pastasComFalha: Int
) {
    val houveMudanca: Boolean
        get() = adicionados > 0 || atualizados > 0 || ausentes > 0 || reencontrados > 0

    companion object {
        val VAZIO = ResumoSincronizacao(0, 0, 0, 0, 0)
    }
}

/** Envelope do cache em JSON da Fase 1A. Existe so para a migracao. */
private data class BibliotecaSalva(val versao: Int = 1, val jogos: List<Jogo> = emptyList())

/**
 * Dono da biblioteca de jogos, agora sobre Room.
 *
 * O que a troca do miolo resolveu:
 *
 *  - **Marcar um favorito** era copiar a lista inteira em memoria e
 *    reserializar o JSON. Virou `UPDATE ... WHERE uri = ?`: uma linha.
 *  - **Filtro, busca e ordenacao** aconteciam em Kotlin, sobre a lista toda,
 *    a cada recomposicao. Desceram para o SQL, com indice.
 *  - **A varredura incremental** carregava a biblioteca inteira so para
 *    comparar tamanho e data. Agora le tres colunas.
 *
 * As assinaturas publicas ficaram como estavam de proposito: a UI e os
 * Workers nao souberam que o armazenamento mudou. As unicas mudancas sao
 * `jogosSemCapa`/`jogosSemHash`, que viraram suspensas, e o `gravar()`, que
 * deixou de existir porque nao ha mais nada para gravar em lote.
 */
class BibliotecaStore private constructor(context: Context) {

    private val app = context.applicationContext
    private val prefs = Preferencias.obter(app)
    private val dao = PhoenixDatabase.obter(app).jogos()
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()

    private val _estadoScan = MutableStateFlow<EstadoScan>(EstadoScan.Parado)
    val estadoScan: StateFlow<EstadoScan> = _estadoScan.asStateFlow()

    private val _carregado = MutableStateFlow(false)
    val carregado: StateFlow<Boolean> = _carregado.asStateFlow()

    private val _ultimoResumo = MutableStateFlow(ResumoSincronizacao.VAZIO)
    val ultimoResumo: StateFlow<ResumoSincronizacao> = _ultimoResumo.asStateFlow()

    // ------------------------------------------------------------ consulta

    private val _sistema = MutableStateFlow<Sistema?>(null)
    private val _termo = MutableStateFlow("")

    fun definirSistema(sistema: Sistema?) { _sistema.value = sistema }

    fun definirTermo(termo: String) { _termo.value = termo }

    private data class Consulta(
        val sistema: Sistema?,
        val termo: String,
        val filtro: FiltroBiblioteca,
        val ordenacao: Ordenacao
    )

    /**
     * A lista que a tela mostra, montada pelo banco.
     *
     * O debounce e condicional: apagar a busca ate ficar vazia volta a lista
     * completa na hora, enquanto digitar espera 180 ms. Sem isso, cada tecla
     * dispararia uma consulta -- e com o debounce fixo, limpar o campo ficaria
     * com um atraso perceptivel sem motivo.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val jogosVisiveis: StateFlow<List<Jogo>> =
        combine(
            _sistema,
            _termo.debounce { if (it.isEmpty()) 0L else 180L },
            prefs.filtro,
            prefs.ordenacao
        ) { sistema, termo, filtro, ordenacao ->
            Consulta(sistema, termo.trim(), filtro, ordenacao)
        }
            .distinctUntilChanged()
            .flatMapLatest(::consultar)
            .map { it.paraModelos() }
            .stateIn(escopo, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Todos os jogos, sem filtro de busca/sistema/favoritos. */
    val jogosTodos: StateFlow<List<Jogo>> = dao
        .observarTodos()
        .map { it.paraModelos() }
        .stateIn(escopo, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun consultar(c: Consulta): Flow<List<JogoEntity>> {
        val sistema = c.sistema?.name
        val favoritos = c.filtro == FiltroBiblioteca.FAVORITOS
        val recentes = c.filtro == FiltroBiblioteca.RECENTES
        return when (c.ordenacao) {
            Ordenacao.NOME -> dao.observarPorNome(sistema, favoritos, recentes, c.termo)
            Ordenacao.MAIS_JOGADOS -> dao.observarPorTempoJogado(sistema, favoritos, recentes, c.termo)
            Ordenacao.JOGADOS_RECENTE -> dao.observarPorUltimaVezJogado(sistema, favoritos, recentes, c.termo)
        }
    }

    // ----------------------------------------------------------- migracao

    /** Idempotente e seguro sob concorrencia: os Workers tambem chamam. */
    suspend fun carregar() {
        if (_carregado.value) return
        trava.withLock {
            if (_carregado.value) return
            withContext(Dispatchers.IO) { migrarSeNecessario() }
            _carregado.value = true
        }
    }

    private val arquivoJson: File get() = File(app.filesDir, "biblioteca.json")

    /**
     * Traz a biblioteca das fases anteriores para o banco, uma unica vez.
     *
     * Duas origens possiveis, nesta ordem: o `biblioteca.json` da Fase 1A e,
     * para quem pulou dela, o `biblioteca_cache` das SharedPreferences da
     * Fase 0.
     */
    private suspend fun migrarSeNecessario() {
        if (dao.contar() > 0) return

        val doArquivo = lerDoJson()
        val jogos = doArquivo.ifEmpty { lerDoCacheLegado() }
        if (jogos.isEmpty()) return

        dao.salvar(jogos.map { it.paraEntidade() })
        Log.i(TAG, "Migrados ${jogos.size} jogos para o Room")

        // Renomeado, nao apagado: se a migracao tiver saido torta, o dado
        // original ainda esta la para ser inspecionado.
        if (arquivoJson.exists()) {
            arquivoJson.renameTo(File(app.filesDir, "biblioteca.json.migrado"))
        }
        prefs.limparCacheBibliotecaLegado()
    }

    private fun lerDoJson(): List<Jogo> {
        if (!arquivoJson.exists()) return emptyList()
        return try {
            val salva = Gson().fromJson(arquivoJson.readText(), BibliotecaSalva::class.java)
            sanear(salva?.jogos.orEmpty())
        } catch (e: Exception) {
            Log.w(TAG, "biblioteca.json ilegivel", e)
            emptyList()
        }
    }

    private fun lerDoCacheLegado(): List<Jogo> {
        val json = prefs.cacheBibliotecaLegado() ?: return emptyList()
        return try {
            val tipo = object : TypeToken<List<Jogo>>() {}.type
            // O Gson nao executa os padroes do construtor Kotlin, entao campos
            // que nao existiam na Fase 0 chegam como false/0/null.
            sanear(Gson().fromJson<List<Jogo>>(json, tipo).orEmpty())
                .map { it.copy(capaPendente = it.capaUrl.isNullOrBlank()) }
        } catch (e: Exception) {
            Log.w(TAG, "Cache legado ilegivel", e)
            emptyList()
        }
    }

    /** O Gson ignora a nulidade do Kotlin; filtramos na porta de entrada. */
    private fun sanear(lista: List<Jogo?>): List<Jogo> =
        lista.filterNotNull()
            .filter { it.uriString.isNotBlank() && it.nome.isNotBlank() }
            .distinctBy { it.uriString }

    // ------------------------------------------------------------ mutacoes

    fun alternarFavorito(id: String) { escopo.launch { dao.alternarFavorito(id) } }

    fun marcarJogado(id: String, minutosJogados: Int = 0) {
        escopo.launch { dao.registrarSessao(id, System.currentTimeMillis(), minutosJogados) }
    }

    fun remover(id: String) {
        escopo.launch {
            dao.porUri(id)?.capaLocal?.takeIf { it.isNotBlank() }?.let { caminho ->
                runCatching { File(caminho).delete() }
            }
            dao.remover(id)
        }
    }

    fun adicionarPasta(uri: Uri) = prefs.adicionarPasta(uri)

    suspend fun porId(id: String): Jogo? = dao.porUri(id)?.paraModelo()

    // ------------------------------------------------- usado pelos Workers

    suspend fun jogosSemCapa(): List<Jogo> = dao.semCapa().paraModelos()

    suspend fun jogosSemHash(): List<Jogo> = dao.semHash().paraModelos()

    suspend fun definirCapa(id: String, caminhoLocal: String?, url: String?) {
        dao.definirCapa(
            uri = id,
            capaLocal = caminhoLocal,
            capaUrl = url,
            // URL resolvida mas download falhou: continua pendente para nova
            // tentativa. Sem URL, nao ha o que tentar.
            pendente = caminhoLocal.isNullOrBlank() && !url.isNullOrBlank()
        )
    }

    suspend fun marcarSemCapa(id: String) = dao.marcarSemCapa(id)

    suspend fun definirIdentidade(id: String, crc32: String, hashRa: String) =
        dao.definirIdentidade(id, crc32, hashRa)

    /**
     * Capa escolhida a mao pelo usuario.
     *
     * A imagem e COPIADA para o diretorio do app. Guardar so a URI do
     * seletor nao serviria: a permissao dela nao sobrevive a reinicializacao,
     * e a capa sumiria sozinha dias depois.
     */
    fun definirCapaManual(id: String, origem: Uri) {
        escopo.launch {
            runCatching {
                val destino = File(CapaWorker.pastaDeCapas(app), CapaWorker.nomeDeArquivo(id))
                val temporario = File(destino.parentFile, destino.name + ".tmp")
                app.contentResolver.openInputStream(origem)?.use { entrada ->
                    temporario.outputStream().use { saida -> entrada.copyTo(saida, 64 * 1024) }
                } ?: return@runCatching
                if (destino.exists()) destino.delete()
                temporario.renameTo(destino)
                dao.definirCapa(id, destino.absolutePath, null, pendente = false)
            }.onFailure { Log.w(TAG, "Falha ao copiar a capa escolhida", it) }
        }
    }

    // ------------------------------------------------------- sincronizacao

    suspend fun sincronizar(bloqueante: Boolean = false): ResumoSincronizacao {
        carregar()

        val pastas = prefs.pastas()
        if (pastas.isEmpty()) {
            _ultimoResumo.value = ResumoSincronizacao.VAZIO
            return ResumoSincronizacao.VAZIO
        }

        return try {
            _estadoScan.value =
                EstadoScan.EmAndamento(EstadoScan.Etapa.LENDO_PASTAS, 0f, bloqueante)

            // Tres colunas, nao a tabela inteira.
            val conhecidos: Map<String, Assinatura> = dao.assinaturas()
                .associate { it.uri to Assinatura(it.tamanhoBytes, it.modificadoEm) }
            val ausentesConhecidos = dao.urisAusentes().toSet()

            val diff = RomScanner.comparar(app, pastas, conhecidos, ausentesConhecidos)

            if (!diff.temTrabalho) {
                ResumoSincronizacao(0, 0, 0, 0, diff.pastasComFalha.size)
                    .also { _ultimoResumo.value = it }
            } else {
                val paraIdentificar = diff.novos + diff.alterados
                val identificados = if (paraIdentificar.isEmpty()) {
                    emptyList()
                } else {
                    _estadoScan.value =
                        EstadoScan.EmAndamento(EstadoScan.Etapa.IDENTIFICANDO, 0f, bloqueante)
                    RomScanner.identificar(app, paraIdentificar) { p ->
                        _estadoScan.value =
                            EstadoScan.EmAndamento(EstadoScan.Etapa.IDENTIFICANDO, p, bloqueante)
                    }
                }

                aplicar(identificados, diff)

                ResumoSincronizacao(
                    adicionados = diff.novos.size,
                    atualizados = diff.alterados.size,
                    ausentes = diff.idsAusentes.size,
                    reencontrados = diff.idsReencontrados.size,
                    pastasComFalha = diff.pastasComFalha.size
                ).also { _ultimoResumo.value = it }
            }
        } finally {
            _estadoScan.value = EstadoScan.Parado
            Trabalhos.enfileirarCapas(app)
        }
    }

    /**
     * Funde a varredura no banco.
     *
     * Arquivo novo entra inteiro. Arquivo ALTERADO passa por um UPDATE que
     * toca so os campos do arquivo: favorito, tempo de jogo e ultima vez
     * jogado continuam do usuario, e o hash e zerado porque o conteudo mudou.
     */
    private suspend fun aplicar(identificados: List<Jogo>, diff: RomScanner.Diferenca) {
        val idsAlterados = diff.alterados.mapTo(HashSet()) { it.uri.toString() }

        val novos = identificados.filter { it.uriString !in idsAlterados }
        if (novos.isNotEmpty()) {
            dao.inserirIgnorando(novos.map { it.paraEntidade() })
        }

        for (jogo in identificados.filter { it.uriString in idsAlterados }) {
            dao.atualizarArquivoAlterado(
                uri = jogo.uriString,
                nome = jogo.nome,
                nomeOrdenacao = jogo.nome.lowercase(),
                nomeArquivo = jogo.nomeArquivoOriginal,
                extensao = jogo.extensao,
                sistema = jogo.sistema.name,
                regiao = jogo.regiao,
                tamanho = jogo.tamanhoBytes,
                modificado = jogo.modificadoEm
            )
        }

        // SQLite tem teto de variaveis por statement (999 por padrao). Um
        // cartao SD removido com 3.000 jogos estouraria isso num IN (...).
        diff.idsAusentes.chunked(LOTE_SQL).forEach { dao.marcarAusentes(it) }
        diff.idsReencontrados.chunked(LOTE_SQL).forEach { dao.marcarPresentes(it) }
    }

    /** O usuario acabou de escolher uma pasta: varre so ela, mostrando progresso. */
    suspend fun importarPasta(treeUri: Uri) {
        carregar()
        try {
            _estadoScan.value =
                EstadoScan.EmAndamento(EstadoScan.Etapa.LENDO_PASTAS, 0f, bloqueante = true)

            val conhecidos = dao.assinaturas().mapTo(HashSet()) { it.uri }
            val resultado = RomScanner.listar(app, treeUri)
            val novos = resultado.arquivos.filter { it.uri.toString() !in conhecidos }

            if (novos.isNotEmpty()) {
                _estadoScan.value =
                    EstadoScan.EmAndamento(EstadoScan.Etapa.IDENTIFICANDO, 0f, bloqueante = true)
                val identificados = RomScanner.identificar(app, novos) { p ->
                    _estadoScan.value =
                        EstadoScan.EmAndamento(EstadoScan.Etapa.IDENTIFICANDO, p, bloqueante = true)
                }
                if (identificados.isNotEmpty()) {
                    dao.inserirIgnorando(identificados.map { it.paraEntidade() })
                }
            }

            _ultimoResumo.value = ResumoSincronizacao(
                adicionados = novos.size,
                atualizados = 0,
                ausentes = 0,
                reencontrados = 0,
                pastasComFalha = if (resultado.ok) 0 else 1
            )
        } finally {
            _estadoScan.value = EstadoScan.Parado
            Trabalhos.enfileirarCapas(app)
        }
    }

    // --------------------------------------- disparos a partir da UI

    fun sincronizarAsync(bloqueante: Boolean = false) {
        escopo.launch { runCatching { sincronizar(bloqueante) } }
    }

    fun importarPastaAsync(treeUri: Uri) {
        escopo.launch { runCatching { importarPasta(treeUri) } }
    }

    companion object {
        private const val TAG = "BibliotecaStore"
        private const val LOTE_SQL = 500

        @Volatile
        private var instancia: BibliotecaStore? = null

        fun obter(context: Context): BibliotecaStore =
            instancia ?: synchronized(this) {
                instancia ?: BibliotecaStore(context).also { instancia = it }
            }
    }
}
