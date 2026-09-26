package com.dfdx047.phoenixemu.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.dfdx047.phoenixemu.Assinatura
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.RomParser
import com.dfdx047.phoenixemu.Sistema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.zip.ZipInputStream

/**
 * Varredura de pastas SAF.
 *
 * Fase 0 trocou DocumentFile por DocumentsContract e tornou a varredura
 * recursiva. Fase 1A acrescenta a parte que faltava: ela agora e
 * INCREMENTAL. Cada arquivo carrega uma assinatura (tamanho + data de
 * modificacao); se a assinatura bate com a que esta na biblioteca, o arquivo
 * e ignorado sem ser aberto. Reabrir o app com 5.000 ROMs passa a custar uma
 * query por pasta, e nada mais.
 */
object RomScanner {

    private const val TAG = "RomScanner"
    private const val PROFUNDIDADE_MAXIMA = 12

    /** Extensoes que vale a pena abrir. `fig`/`swc` sao formatos antigos de SNES. */
    val EXTENSOES_ACEITAS = setOf("nes", "smc", "sfc", "fig", "swc", "zip")

    private val COLUNAS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    data class ArquivoRom(
        val uri: Uri,
        val nome: String,
        val tamanho: Long,
        val modificado: Long
    ) {
        val extensao: String get() = nome.substringAfterLast('.', "").lowercase()
        val nomeSemExtensao: String get() = nome.substringBeforeLast('.')
        val assinatura: Assinatura get() = Assinatura(tamanho, modificado)
    }

    /**
     * `ok = false` quando a pasta nao pode ser lida (permissao revogada, SD
     * card removido). Essa distincao e critica: sem ela, uma pasta
     * inacessivel faria a biblioteca inteira parecer ter desaparecido.
     */
    data class VarreduraDePasta(
        val treeUri: Uri,
        val ok: Boolean,
        val arquivos: List<ArquivoRom>
    )

    data class Diferenca(
        val novos: List<ArquivoRom>,
        val alterados: List<ArquivoRom>,
        val idsAusentes: List<String>,
        val idsReencontrados: List<String>,
        val totalVistos: Int,
        val pastasComFalha: List<Uri>
    ) {
        val temTrabalho: Boolean
            get() = novos.isNotEmpty() || alterados.isNotEmpty() ||
                idsAusentes.isNotEmpty() || idsReencontrados.isNotEmpty()
    }

    // ------------------------------------------------------------ listagem

    /** Lista recursivamente os candidatos a ROM dentro de uma arvore SAF. */
    suspend fun listar(context: Context, treeUri: Uri): VarreduraDePasta =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val encontrados = ArrayList<ArquivoRom>()
            val visitados = HashSet<String>()

            val raiz = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "URI de arvore invalida: $treeUri", e)
                return@withContext VarreduraDePasta(treeUri, ok = false, arquivos = emptyList())
            }

            // Fila de (documentId, profundidade). Sem recursao de verdade: uma
            // estrutura de pastas patologica nao estoura a pilha.
            val fila = ArrayDeque<Pair<String, Int>>()
            fila += raiz to 0
            var falhou = false

            while (fila.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (docId, profundidade) = fila.removeFirst()
                if (!visitados.add(docId)) continue

                val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val cursor: Cursor? = try {
                    resolver.query(filhos, COLUNAS, null, null, null)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permissao perdida em $treeUri", e)
                    falhou = true
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao consultar $docId", e)
                    falhou = true
                    null
                }

                if (cursor == null) {
                    // Se foi a raiz que falhou, a arvore esta perdida e paramos.
                    // Uma subpasta ilegivel apenas marca a arvore como suspeita:
                    // seguimos listando, mas nao vamos declarar nada ausente.
                    if (docId == raiz) break
                    continue
                }

                cursor.use { c ->
                    val iId = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val iNome = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val iMime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val iTam = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val iMod = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (c.moveToNext()) {
                        val filhoId = c.getString(iId) ?: continue
                        val nome = c.getString(iNome) ?: continue
                        val mime = c.getString(iMime)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (profundidade < PROFUNDIDADE_MAXIMA) {
                                fila += filhoId to (profundidade + 1)
                            }
                            continue
                        }

                        val ext = nome.substringAfterLast('.', "").lowercase()
                        if (ext !in EXTENSOES_ACEITAS) continue

                        encontrados += ArquivoRom(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, filhoId),
                            nome = nome,
                            tamanho = if (iTam >= 0 && !c.isNull(iTam)) c.getLong(iTam) else 0L,
                            modificado = if (iMod >= 0 && !c.isNull(iMod)) c.getLong(iMod) else 0L
                        )
                    }
                }
            }

            VarreduraDePasta(treeUri, ok = !falhou, arquivos = encontrados)
        }

    // --------------------------------------------------------- comparacao

    /**
     * Compara o disco com o que a biblioteca ja conhece.
     *
     * A parte delicada e a ausencia. Um jogo so pode ser marcado como ausente
     * se a PASTA dele foi lida com sucesso: se o cartao SD nao montou, a query
     * falha e marcar tudo como ausente apagaria a biblioteca visualmente. Por
     * isso a ausencia e calculada apenas dentro das arvores que responderam.
     */
    suspend fun comparar(
        context: Context,
        pastas: List<Uri>,
        conhecidos: Map<String, Assinatura>,
        ausentesConhecidos: Set<String>
    ): Diferenca = withContext(Dispatchers.IO) {
        val novos = ArrayList<ArquivoRom>()
        val alterados = ArrayList<ArquivoRom>()
        val vistos = HashSet<String>()
        val arvoresOk = ArrayList<Uri>()
        val pastasComFalha = ArrayList<Uri>()

        for (pasta in pastas) {
            currentCoroutineContext().ensureActive()
            val resultado = listar(context, pasta)
            if (!resultado.ok) {
                pastasComFalha += pasta
                continue
            }
            arvoresOk += pasta

            for (arquivo in resultado.arquivos) {
                val id = arquivo.uri.toString()
                if (!vistos.add(id)) continue // pastas aninhadas registradas duas vezes

                val anterior = conhecidos[id]
                when {
                    anterior == null -> novos += arquivo
                    anterior != arquivo.assinatura -> alterados += arquivo
                }
            }
        }

        // Prefixo das URIs de cada arvore lida com sucesso.
        val prefixosOk = arvoresOk.map { "$it/document/" }
        fun dentroDeArvoreLida(id: String) = prefixosOk.any { id.startsWith(it) }

        val idsAusentes = conhecidos.keys.filter { id ->
            id !in vistos && id !in ausentesConhecidos && dentroDeArvoreLida(id)
        }
        val idsReencontrados = ausentesConhecidos.filter { it in vistos }

        Diferenca(
            novos = novos,
            alterados = alterados,
            idsAusentes = idsAusentes,
            idsReencontrados = idsReencontrados,
            totalVistos = vistos.size,
            pastasComFalha = pastasComFalha
        )
    }

    // ------------------------------------------------------ identificacao

    /**
     * Identifica o sistema. Para .zip, inspeciona os nomes das entradas sem
     * extrair. Para .nes, confere tambem o cabecalho iNES, entao um arquivo
     * renomeado errado nao entra na biblioteca como NES.
     */
    private fun identificarSistema(
        context: Context,
        uri: Uri,
        extensao: String
    ): Sistema? = when (extensao) {
        "nes" -> Sistema.NES
        "smc", "sfc", "fig", "swc" -> Sistema.SNES
        "zip" -> identificarDentroDoZip(context, uri)
        else -> null
    }

    private fun identificarDentroDoZip(context: Context, uri: Uri): Sistema? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                ZipInputStream(entrada).use { zis ->
                    var sistema: Sistema? = null
                    var entry = zis.nextEntry
                    var lidas = 0
                    while (entry != null && sistema == null && lidas < 500) {
                        if (!entry.isDirectory) {
                            sistema = when (entry.name.substringAfterLast('.', "").lowercase()) {
                                "nes" -> Sistema.NES
                                "smc", "sfc", "fig", "swc" -> Sistema.SNES
                                else -> null
                            }
                        }
                        lidas++
                        if (sistema == null) entry = zis.nextEntry
                    }
                    sistema
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Zip ilegivel: $uri", e)
            null
        }
    }

    /**
     * Transforma arquivos em Jogos.
     *
     * Nao toca na rede e NAO calcula hash. As capas ficam a cargo do
     * CapaWorker e os hashes do HashWorker: os dois sao caros por motivos
     * diferentes (rede e leitura integral da ROM) e nenhum dos dois pode
     * ficar entre o usuario e a lista de jogos.
     */
    suspend fun identificar(
        context: Context,
        arquivos: List<ArquivoRom>,
        onProgresso: (Float) -> Unit
    ): List<Jogo> = withContext(Dispatchers.IO) {
        if (arquivos.isEmpty()) {
            onProgresso(1f)
            return@withContext emptyList()
        }

        val jogos = ArrayList<Jogo>(arquivos.size)
        val total = arquivos.size
        var ultimoReporte = -1

        arquivos.forEachIndexed { indice, arquivo ->
            currentCoroutineContext().ensureActive()

            val sistema = identificarSistema(context, arquivo.uri, arquivo.extensao)
            if (sistema != null) {
                val analise = RomParser.analisar(arquivo.nomeSemExtensao)
                jogos += Jogo(
                    nome = analise.nomeLimpo,
                    nomeArquivoOriginal = arquivo.nome,
                    extensao = arquivo.extensao,
                    uriString = arquivo.uri.toString(),
                    sistema = sistema,
                    regiao = analise.regiao,
                    capaPendente = true,
                    tamanhoBytes = arquivo.tamanho,
                    modificadoEm = arquivo.modificado
                )
            }

            // Reporta a cada ~1%, nao a cada arquivo: com 5000 ROMs, 5000
            // saltos de estado custam mais que a propria varredura.
            val pct = ((indice + 1) * 100) / total
            if (pct > ultimoReporte || indice == total - 1) {
                ultimoReporte = pct
                onProgresso((indice + 1) / total.toFloat())
            }
        }

        jogos
    }
}
