package com.dfdx047.phoenixemu.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.RetroScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/**
 * Resolve e baixa as capas que faltam.
 *
 * Duas mudancas em relacao a Fase 0:
 *
 *  1. Roda no WorkManager, entao sobrevive ao app ser fechado e retoma de
 *     onde parou (o proprio criterio de "o que falta" e o estado da
 *     biblioteca, nao uma posicao guardada).
 *  2. A capa e GRAVADA EM DISCO no diretorio do app. A lista passa a ler
 *     arquivo local; nem a rede nem o cache do Coil, que e limitado e
 *     descartavel, entram no caminho do scroll.
 */
class CapaWorker(
    contexto: Context,
    parametros: WorkerParameters
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val store = BibliotecaStore.obter(applicationContext)
        store.carregar()

        val pendentes = store.jogosSemCapa()
        Log.w(TAG, "pendentes=${pendentes.size}")
        if (pendentes.isEmpty()) return Result.success()

        val pasta = pastaDeCapas(applicationContext)
        val limite = Semaphore(CONCORRENCIA)
        var feitos = 0

        setProgress(workDataOf(Trabalhos.CHAVE_FEITOS to 0, Trabalhos.CHAVE_TOTAL to pendentes.size))

        for (lote in pendentes.chunked(LOTE)) {
            if (isStopped) break

            coroutineScope {
                lote.map { jogo ->
                    async(Dispatchers.IO) { limite.withPermit { tratar(store, jogo, pasta) } }
                }.awaitAll()
            }

            feitos += lote.size
            setProgress(
                workDataOf(
                    Trabalhos.CHAVE_FEITOS to feitos,
                    Trabalhos.CHAVE_TOTAL to pendentes.size
                )
            )
        }

        return Result.success()
    }

    private suspend fun tratar(store: BibliotecaStore, jogo: Jogo, pasta: File) {
        val url = jogo.capaUrl?.takeIf { it.isNotBlank() }
            ?: runCatching {
                RetroScraper.buscarCapa(
                    jogo.nomeArquivoOriginal.substringBeforeLast('.'),
                    jogo.sistema
                )
            }.getOrNull()

        if (url == null) {
            Log.w(TAG, "sem capa: ${jogo.nomeArquivoOriginal}")
            store.marcarSemCapa(jogo.id)
            return
        }

        val destino = File(pasta, nomeDeArquivo(jogo.id))
        val baixou = RetroScraper.baixarCapa(url, destino)
        if (baixou) {
            store.definirCapa(jogo.id, destino.absolutePath, url)
        } else {
            // Guarda a URL: numa proxima tentativa nao precisamos resolver de novo.
            Log.w(TAG, "Capa resolvida mas nao baixada: ${jogo.nome}")
            store.definirCapa(jogo.id, null, url)
        }
    }

    companion object {
        private const val TAG = "CapaWorker"
        private const val CONCORRENCIA = 6
        private const val LOTE = 24

        fun pastaDeCapas(context: Context): File =
            File(context.filesDir, "capas").apply { mkdirs() }

        /**
         * A URI do documento nao serve como nome de arquivo (tem barras e
         * dois-pontos) e pode passar do limite de tamanho. SHA-1 dela da um
         * nome curto, estavel e unico.
         */
        fun nomeDeArquivo(id: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) sb.append("%02x".format(b.toInt() and 0xFF))
            return "$sb.img"
        }
    }
}
