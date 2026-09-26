package com.dfdx047.phoenixemu.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Registro central dos trabalhos em segundo plano.
 *
 * Por que WorkManager e nao apenas corrotinas: capa e hash precisam
 * sobreviver ao app ser fechado, respeitar rede e bateria, e retomar sozinhos
 * depois de um kill do sistema. Na Fase 0 as capas eram buscadas dentro do
 * escopo do BibliotecaStore: funcionava, mas sair do app no meio de 3.000
 * capas significava comecar de novo na proxima abertura.
 */
object Trabalhos {

    const val CAPAS = "phoenix_capas"
    const val HASHES = "phoenix_hashes"

    const val CHAVE_FEITOS = "feitos"
    const val CHAVE_TOTAL = "total"

    data class Progresso(val feitos: Int, val total: Int, val rodando: Boolean) {
        val fracao: Float get() = if (total > 0) feitos / total.toFloat() else 0f
    }

    /** Enfileira a busca de capas. KEEP: nao empilha se ja houver uma em andamento. */
    fun enfileirarCapas(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<CapaWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CAPAS, ExistingWorkPolicy.KEEP, pedido)
    }

    /**
     * Enfileira o calculo de hashes. Disparado pelo usuario, nao
     * automaticamente: ler cada byte de cada ROM atraves do SAF e caro, e o
     * hash so serve ao RetroAchievements, que ainda nao existe.
     */
    fun enfileirarHashes(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<HashWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(HASHES, ExistingWorkPolicy.KEEP, pedido)
    }

    fun cancelar(context: Context, nome: String) {
        WorkManager.getInstance(context).cancelUniqueWork(nome)
    }

    /** null quando nao ha nada em andamento. */
    fun progresso(context: Context, nome: String): Flow<Progresso?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(nome)
            .map { infos ->
                val ativo = infos.firstOrNull { !it.state.isFinished } ?: return@map null
                val feitos = ativo.progress.getInt(CHAVE_FEITOS, 0)
                val total = ativo.progress.getInt(CHAVE_TOTAL, 0)
                Progresso(
                    feitos = feitos,
                    total = total,
                    rodando = ativo.state == WorkInfo.State.RUNNING
                )
            }
}
