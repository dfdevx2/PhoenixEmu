package com.dfdx047.phoenixemu.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dfdx047.phoenixemu.Assinatura
import com.dfdx047.phoenixemu.Jogo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Envelope versionado do cache em disco: permite evoluir o formato sem perder tudo. */
data class BibliotecaSalva(
    val versao: Int = BibliotecaStore.VERSAO_FORMATO,
    val jogos: List<Jogo> = emptyList()
)

sealed interface EstadoScan {
    data object Parado : EstadoScan

    /**
     * `bloqueante` separa os dois casos que antes eram tratados igual:
     *  - o usuario acabou de escolher uma pasta e esta esperando (dialogo);
     *  - o app abriu e esta se sincronizando sozinho (barra fina, sem estorvo).
     */
    data class EmAndamento(
        val etapa: Etapa,
        val progresso: Float,
        val bloqueante: Boolean
    ) : EstadoScan

    enum class Etapa { LENDO_PASTAS, IDENTIFICANDO }
}

/** Resultado de uma sincronizacao, para a UI avisar o usuario. */
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

/**
 * Dono da biblioteca de jogos.
 *
 * Fase 1A muda o miolo da varredura:
 *
 *  - `sincronizar()` e incremental. Compara a assinatura (tamanho + data) de
 *    cada arquivo com a que esta na biblioteca e so reidentifica o que mudou.
 *    Reabrir o app com 5.000 ROMs custa uma query por pasta.
 *  - Arquivo que desapareceu e marcado `ausente`, nao apagado. Cartao SD
 *    desmontado nao destroi a biblioteca, e favoritos e tempo de jogo
 *    sobrevivem a pasta voltar.
 *  - A busca de capas saiu daqui e foi para o CapaWorker; o hash, para o
 *    HashWorker. Este objeto voltou a fazer so uma coisa: ser a verdade
 *    sobre quais jogos existem.
 *
 * Na Fase 1B o corpo destes metodos passa a falar com um banco; as
 * assinaturas publicas foram desenhadas para nao mudar quando isso acontecer.
 */
class BibliotecaStore private constructor(context: Context) {

    private val app = context.applicationContext
    private val prefs = Preferencias.obter(app)
    private val gson = Gson()
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()
    private val arquivo: File get() = File(app.filesDir, NOME_ARQUIVO)

    private val _jogos = MutableStateFlow<List<Jogo>>(emptyList())
    val jogos: StateFlow<List<Jogo>> = _jogos.asStateFlow()

    private val _estadoScan = MutableStateFlow<EstadoScan>(EstadoScan.Parado)
    val estadoScan: StateFlow<EstadoScan> = _estadoScan.asStateFlow()

    private val _carregado = MutableStateFlow(false)
    val carregado: StateFlow<Boolean> = _carregado.asStateFlow()

    private val _ultimoResumo = MutableStateFlow(ResumoSincronizacao.VAZIO)
    val ultimoResumo: StateFlow<ResumoSincronizacao> = _ultimoResumo.asStateFlow()

    private var jobPersistencia: Job? = null

    // ------------------------------------------------------------ leitura

    /**
     * Idempotente e seguro para chamar de varios lugares ao mesmo tempo: os
     * Workers tambem chamam, e eles rodam fora da Activity.
     */
    suspend fun carregar() {
        if (_carregado.value) return
        trava.withLock {
            if (_carregado.value) return
            val lidos = withContext(Dispatchers.IO) {
                lerDoArquivo() ?: migrarDasPreferencias()
            }
            _jogos.value = lidos.sortedBy { it.nome.lowercase() }
            _carregado.value = true
        }
    }

    private fun lerDoArquivo(): List<Jogo>? {
        if (!arquivo.exists()) return null
        return try {
            val salva = gson.fromJson(arquivo.readText(), BibliotecaSalva::class.java)
            salva?.jogos?.let(::sanear)
        } catch (e: Exception) {
            Log.w(TAG, "Cache em disco corrompido; recomecando vazio", e)
            null
        }
    }

    private fun migrarDasPreferencias(): List<Jogo> {
        val json = prefs.cacheBibliotecaLegado() ?: return emptyList()
        val lista = try {
            val tipo = object : TypeToken<List<Jogo>>() {}.type
            gson.fromJson<List<Jogo>>(json, tipo)
        } catch (e: Exception) {
            Log.w(TAG, "Cache legado ilegivel", e)
            null
        } ?: return emptyList()

        // O Gson nao executa os valores padrao do construtor Kotlin, entao os
        // campos novos vem como false/0/null. Aqui damos sentido a eles.
        val migrados = sanear(lista).map { it.copy(capaPendente = it.capaUrl.isNullOrBlank()) }
        Log.i(TAG, "Migrados ${migrados.size} jogos das SharedPreferences para $NOME_ARQUIVO")
        gravarAgora(migrados)
        prefs.limparCacheBibliotecaLegado()
        return migrados
    }

    /**
     * O Gson ignora a nulidade do Kotlin: um JSON adulterado ou truncado
     * produz objetos com campos null onde o tipo diz que nao pode ser null, e
     * o crash aparece longe daqui. Filtramos na porta de entrada.
     */
    private fun sanear(lista: List<Jogo?>): List<Jogo> =
        lista.filterNotNull()
            .filter { it.uriString.isNotBlank() && it.nome.isNotBlank() }
            .distinctBy { it.uriString }

    // ------------------------------------------------------------ escrita

    private fun agendarPersistencia() {
        jobPersistencia?.cancel()
        jobPersistencia = escopo.launch {
            delay(400) // agrupa rajadas (marcar varios favoritos, importar em lote)
            gravarAgora(_jogos.value)
        }
    }

    /** Gravacao imediata. Os Workers chamam isto ao fim de cada lote. */
    fun gravar() = gravarAgora(_jogos.value)

    private fun gravarAgora(lista: List<Jogo>) {
        try {
            val temporario = File(app.filesDir, "$NOME_ARQUIVO.tmp")
            temporario.writeText(gson.toJson(BibliotecaSalva(jogos = lista)))
            if (!temporario.renameTo(arquivo)) {
                // renameTo falha em alguns sistemas de arquivos se o destino existe
                arquivo.delete()
                temporario.renameTo(arquivo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gravar a biblioteca", e)
        }
    }

    // ------------------------------------------------------------ mutacoes

    private fun atualizar(id: String, transformar: (Jogo) -> Jogo) {
        val atual = _jogos.value
        val indice = atual.indexOfFirst { it.id == id }
        if (indice < 0) return
        _jogos.value = atual.toMutableList().also { it[indice] = transformar(it[indice]) }
        agendarPersistencia()
    }

    fun alternarFavorito(id: String) = atualizar(id) { it.copy(isFavorito = !it.isFavorito) }

    fun marcarJogado(id: String, minutosJogados: Int = 0) = atualizar(id) {
        it.copy(
            ultimaVezJogado = System.currentTimeMillis(),
            tempoJogadoMinutos = it.tempoJogadoMinutos + minutosJogados
        )
    }

    fun remover(id: String) {
        _jogos.value.firstOrNull { it.id == id }?.capaLocal?.let { caminho ->
            runCatching { File(caminho).delete() }
        }
        _jogos.value = _jogos.value.filterNot { it.id == id }
        agendarPersistencia()
    }

    fun adicionarPasta(uri: Uri) = prefs.adicionarPasta(uri)

    // ------------------------------------------------- usado pelos Workers

    fun jogosSemCapa(): List<Jogo> = _jogos.value.filter {
        it.capaPendente && it.capaLocal.isNullOrBlank() && !it.ausente
    }

    fun jogosSemHash(): List<Jogo> = _jogos.value.filter {
        it.hashRa.isNullOrBlank() && !it.ausente
    }

    fun definirCapa(id: String, caminhoLocal: String?, url: String?) = atualizar(id) {
        it.copy(
            capaLocal = caminhoLocal,
            capaUrl = url,
            // Se a URL existe mas o download falhou, segue pendente para
            // tentar de novo depois. Sem URL, nao ha o que tentar.
            capaPendente = caminhoLocal.isNullOrBlank() && !url.isNullOrBlank()
        )
    }

    fun marcarSemCapa(id: String) = atualizar(id) {
        it.copy(capaPendente = false, capaUrl = null, capaLocal = null)
    }

    fun definirIdentidade(id: String, crc32: String, hashRa: String) = atualizar(id) {
        it.copy(crc32 = crc32, hashRa = hashRa)
    }

    // ------------------------------------------------------- sincronizacao

    /**
     * Varredura incremental de todas as pastas registradas.
     *
     * @param bloqueante true quando o usuario esta olhando e esperando.
     */
    suspend fun sincronizar(bloqueante: Boolean = false): ResumoSincronizacao {
        carregar()

        val pastas = prefs.pastas()
        if (pastas.isEmpty()) {
            _ultimoResumo.value = ResumoSincronizacao.VAZIO
            return ResumoSincronizacao.VAZIO
        }

        return try {
            _estadoScan.value = EstadoScan.EmAndamento(
                EstadoScan.Etapa.LENDO_PASTAS, 0f, bloqueante
            )

            val conhecidos: Map<String, Assinatura> =
                _jogos.value.associate { it.uriString to it.assinatura }
            val ausentesConhecidos: Set<String> =
                _jogos.value.filter { it.ausente }.mapTo(HashSet()) { it.uriString }

            val diff = RomScanner.comparar(app, pastas, conhecidos, ausentesConhecidos)

            if (!diff.temTrabalho) {
                val resumo = ResumoSincronizacao(0, 0, 0, 0, diff.pastasComFalha.size)
                _ultimoResumo.value = resumo
                return resumo
            }

            val paraIdentificar = diff.novos + diff.alterados
            val identificados = if (paraIdentificar.isEmpty()) {
                emptyList()
            } else {
                _estadoScan.value = EstadoScan.EmAndamento(
                    EstadoScan.Etapa.IDENTIFICANDO, 0f, bloqueante
                )
                RomScanner.identificar(app, paraIdentificar) { p ->
                    _estadoScan.value = EstadoScan.EmAndamento(
                        EstadoScan.Etapa.IDENTIFICANDO, p, bloqueante
                    )
                }
            }

            aplicar(identificados, diff)

            val resumo = ResumoSincronizacao(
                adicionados = diff.novos.size,
                atualizados = diff.alterados.size,
                ausentes = diff.idsAusentes.size,
                reencontrados = diff.idsReencontrados.size,
                pastasComFalha = diff.pastasComFalha.size
            )
            _ultimoResumo.value = resumo
            resumo
        } finally {
            _estadoScan.value = EstadoScan.Parado
            // Sempre: pode haver capa pendente de uma execucao anterior.
            Trabalhos.enfileirarCapas(app)
        }
    }

    /**
     * Funde o resultado da varredura na biblioteca.
     *
     * O cuidado aqui e nao destruir o que e do USUARIO. Um arquivo alterado
     * ganha nome, sistema e assinatura novos, mas mantem favorito, tempo de
     * jogo e ultima vez jogado. O hash, ao contrario, e descartado: o
     * conteudo mudou, entao o hash antigo esta errado.
     */
    private fun aplicar(identificados: List<Jogo>, diff: RomScanner.Diferenca) {
        val porId = identificados.associateBy { it.uriString }
        val idsAlterados = diff.alterados.mapTo(HashSet()) { it.uri.toString() }
        val idsAusentes = diff.idsAusentes.toHashSet()
        val idsReencontrados = diff.idsReencontrados.toHashSet()

        val atualizada = ArrayList<Jogo>(_jogos.value.size + porId.size)

        for (antigo in _jogos.value) {
            val id = antigo.uriString
            when {
                id in idsAlterados -> {
                    val novo = porId[id]
                    if (novo == null) {
                        // Reidentificacao falhou (arquivo virou ilegivel).
                        atualizada += antigo.copy(ausente = true)
                    } else {
                        atualizada += novo.copy(
                            isFavorito = antigo.isFavorito,
                            ultimaVezJogado = antigo.ultimaVezJogado,
                            tempoJogadoMinutos = antigo.tempoJogadoMinutos,
                            capaUrl = antigo.capaUrl,
                            capaLocal = antigo.capaLocal,
                            capaPendente = antigo.capaLocal.isNullOrBlank(),
                            hashRa = null,
                            crc32 = null,
                            ausente = false
                        )
                    }
                }
                id in idsAusentes -> atualizada += antigo.copy(ausente = true)
                id in idsReencontrados -> atualizada += antigo.copy(ausente = false)
                else -> atualizada += antigo
            }
        }

        // Os genuinamente novos (nao estavam na lista antiga).
        val jaPresentes = _jogos.value.mapTo(HashSet()) { it.uriString }
        for (novo in identificados) {
            if (novo.uriString !in jaPresentes) atualizada += novo
        }

        _jogos.value = atualizada.sortedBy { it.nome.lowercase() }
        gravarAgora(_jogos.value)
    }

    /** O usuario acabou de escolher uma pasta: varre so ela e mostra progresso. */
    suspend fun importarPasta(treeUri: Uri) {
        carregar()
        try {
            _estadoScan.value = EstadoScan.EmAndamento(
                EstadoScan.Etapa.LENDO_PASTAS, 0f, bloqueante = true
            )
            val conhecidos = _jogos.value.mapTo(HashSet()) { it.uriString }
            val resultado = RomScanner.listar(app, treeUri)
            val novos = resultado.arquivos.filter { it.uri.toString() !in conhecidos }

            if (novos.isNotEmpty()) {
                _estadoScan.value = EstadoScan.EmAndamento(
                    EstadoScan.Etapa.IDENTIFICANDO, 0f, bloqueante = true
                )
                val identificados = RomScanner.identificar(app, novos) { p ->
                    _estadoScan.value = EstadoScan.EmAndamento(
                        EstadoScan.Etapa.IDENTIFICANDO, p, bloqueante = true
                    )
                }
                val jaPresentes = _jogos.value.mapTo(HashSet()) { it.uriString }
                _jogos.value = (_jogos.value + identificados.filter { it.uriString !in jaPresentes })
                    .sortedBy { it.nome.lowercase() }
                gravarAgora(_jogos.value)
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
    //
    // A UI nao deve segurar uma varredura no escopo de uma composicao: sair
    // da tela cancelaria a importacao no meio. Estes rodam no escopo do
    // proprio store, que vive enquanto o processo vive.

    fun sincronizarAsync(bloqueante: Boolean = false) {
        escopo.launch { runCatching { sincronizar(bloqueante) } }
    }

    fun importarPastaAsync(treeUri: Uri) {
        escopo.launch { runCatching { importarPasta(treeUri) } }
    }

    companion object {
        private const val TAG = "BibliotecaStore"
        private const val NOME_ARQUIVO = "biblioteca.json"

        const val VERSAO_FORMATO = 1

        @Volatile
        private var instancia: BibliotecaStore? = null

        fun obter(context: Context): BibliotecaStore =
            instancia ?: synchronized(this) {
                instancia ?: BibliotecaStore(context).also { instancia = it }
            }
    }
}
