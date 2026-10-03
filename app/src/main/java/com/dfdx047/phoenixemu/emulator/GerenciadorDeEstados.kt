package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ResultadoDoEstado { OK, VAZIO, FALHA }

internal class GerenciadorDeEstados(
    private val nucleo: NucleoLibretro,
    private val armazem: ArmazemDeEstados,
    private val escopo: CoroutineScope,
    private val capturarMiniatura: (onDone: () -> Unit) -> Unit,
    private val miniaturaAtual: () -> Bitmap?,
    private val pausado: () -> Boolean
) {
    private var pendingJob: Job? = null

    fun salvar(slot: Int, capturarAntes: Boolean, aoConcluir: (ResultadoDoEstado) -> Unit) {
        if (pausado()) {
            val bytes = nucleo.salvarEstado()
            if (bytes == null || bytes.isEmpty()) {
                Log.i("PhoenixEstado", "salvar slot=$slot pausado=true via=sync resultado=FALHA")
                aoConcluir(ResultadoDoEstado.FALHA)
                return
            }
            escopo.launch(Dispatchers.IO) {
                try {
                    armazem.gravarBytes(slot, bytes)
                    val currentBmp = miniaturaAtual()
                    if (currentBmp != null) {
                        armazem.gravarMiniatura(slot, currentBmp)
                    }
                    withContext(Dispatchers.Main) {
                        Log.i("PhoenixEstado", "salvar slot=$slot pausado=true via=sync resultado=OK")
                        aoConcluir(ResultadoDoEstado.OK)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.i("PhoenixEstado", "salvar slot=$slot pausado=true via=sync resultado=FALHA")
                        aoConcluir(ResultadoDoEstado.FALHA)
                    }
                }
            }
        } else {
            if (capturarAntes) {
                capturarMiniatura {
                    executarSalvar(slot, aoConcluir)
                }
            } else {
                executarSalvar(slot, aoConcluir)
            }
        }
    }

    private fun executarSalvar(slot: Int, aoConcluir: (ResultadoDoEstado) -> Unit) {
        val fileState = armazem.arquivoDoEstado(slot)
        if (fileState != null && nucleo.pedirEstado(1, fileState.absolutePath)) {
            pendingJob?.cancel()
            pendingJob = escopo.launch {
                var result = 0
                for (i in 0 until 40) { // 40 * 50ms = 2s
                    delay(50)
                    result = nucleo.resultadoEstado()
                    if (result != 0) break
                }
                if (result == 1) {
                    val currentBmp = miniaturaAtual()
                    if (currentBmp != null) {
                        withContext(Dispatchers.IO) {
                            armazem.gravarMiniatura(slot, currentBmp)
                        }
                    }
                    Log.i("PhoenixEstado", "salvar slot=$slot pausado=false via=async resultado=OK")
                    aoConcluir(ResultadoDoEstado.OK)
                } else {
                    Log.i("PhoenixEstado", "salvar slot=$slot pausado=false via=async resultado=FALHA")
                    aoConcluir(ResultadoDoEstado.FALHA)
                }
            }
        } else {
            Log.i("PhoenixEstado", "salvar slot=$slot pausado=false via=async resultado=FALHA")
            aoConcluir(ResultadoDoEstado.FALHA)
        }
    }

    fun carregar(slot: Int, aoConcluir: (ResultadoDoEstado) -> Unit) {
        if (!armazem.temEstado(slot)) {
            Log.i("PhoenixEstado", "carregar slot=$slot pausado=${pausado()} via=sync resultado=VAZIO")
            aoConcluir(ResultadoDoEstado.VAZIO)
            return
        }
        if (pausado()) {
            escopo.launch(Dispatchers.IO) {
                val bytes = armazem.lerBytes(slot)
                withContext(Dispatchers.Main) {
                    if (bytes == null || bytes.isEmpty()) {
                        Log.i("PhoenixEstado", "carregar slot=$slot pausado=true via=sync resultado=FALHA")
                        aoConcluir(ResultadoDoEstado.FALHA)
                    } else {
                        val ok = nucleo.carregarEstado(bytes)
                        if (ok) {
                            Log.i("PhoenixEstado", "carregar slot=$slot pausado=true via=sync resultado=OK")
                            aoConcluir(ResultadoDoEstado.OK)
                        } else {
                            Log.i("PhoenixEstado", "carregar slot=$slot pausado=true via=sync resultado=FALHA")
                            aoConcluir(ResultadoDoEstado.FALHA)
                        }
                    }
                }
            }
        } else {
            val fileState = armazem.arquivoDoEstado(slot)
            if (fileState != null && nucleo.pedirEstado(2, fileState.absolutePath)) {
                pendingJob?.cancel()
                pendingJob = escopo.launch {
                    var result = 0
                    for (i in 0 until 40) {
                        delay(50)
                        result = nucleo.resultadoEstado()
                        if (result != 0) break
                    }
                    if (result == 2) {
                        Log.i("PhoenixEstado", "carregar slot=$slot pausado=false via=async resultado=OK")
                        aoConcluir(ResultadoDoEstado.OK)
                    } else {
                        Log.i("PhoenixEstado", "carregar slot=$slot pausado=false via=async resultado=FALHA")
                        aoConcluir(ResultadoDoEstado.FALHA)
                    }
                }
            } else {
                Log.i("PhoenixEstado", "carregar slot=$slot pausado=false via=async resultado=FALHA")
                aoConcluir(ResultadoDoEstado.FALHA)
            }
        }
    }

    fun cancelarPendentes() {
        pendingJob?.cancel()
        pendingJob = null
    }
}
