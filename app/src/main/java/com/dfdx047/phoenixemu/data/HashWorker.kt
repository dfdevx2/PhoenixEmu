package com.dfdx047.phoenixemu.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Calcula CRC32 e o MD5 no formato RetroAchievements de cada ROM.
 *
 * Disparado pelo usuario, nas Configuracoes. Deliberadamente NAO automatico:
 * hashear exige ler cada byte de cada ROM atraves do SAF, e numa biblioteca
 * grande isso e questao de minutos a dezenas de minutos e de bateria de
 * verdade. Como o hash serve ao RetroAchievements (Fase 5), ele pode esperar.
 *
 * E retomavel: o criterio de "o que falta" e a propria biblioteca, entao
 * cancelar no meio e voltar depois nao perde nada do que ja foi calculado.
 */
class HashWorker(
    contexto: Context,
    parametros: WorkerParameters
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val store = BibliotecaStore.obter(applicationContext)
        store.carregar()

        val pendentes = store.jogosSemHash()
        if (pendentes.isEmpty()) return Result.success()

        setProgress(workDataOf(Trabalhos.CHAVE_FEITOS to 0, Trabalhos.CHAVE_TOTAL to pendentes.size))

        var feitos = 0
        for (jogo in pendentes) {
            if (isStopped) break

            val identidade = RomHasher.calcular(
                applicationContext,
                jogo.uri,
                jogo.extensao,
                jogo.sistema
            )
            if (identidade != null) {
                store.definirIdentidade(jogo.id, identidade.crc32, identidade.hashRa)
            }

            feitos++
            // Sequencial de proposito: hashear e limitado por I/O do
            // provedor SAF, e paralelizar leitura de arquivos grandes no
            // mesmo armazenamento costuma piorar em vez de ajudar.
            if (feitos % 10 == 0 || feitos == pendentes.size) {
                setProgress(
                    workDataOf(
                        Trabalhos.CHAVE_FEITOS to feitos,
                        Trabalhos.CHAVE_TOTAL to pendentes.size
                    )
                )
                store.gravar()
            }
        }

        store.gravar()
        return Result.success()
    }
}
