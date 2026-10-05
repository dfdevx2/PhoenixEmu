package com.dfdx047.phoenixemu.emulator

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Lado Kotlin do RetroAchievements (rcheevos). O trabalho pesado esta em ra_bridge.cpp.
 *
 * - `iniciar` cria o cliente nativo (login por token + carga do jogo).
 * - A rede e feita aqui (HttpURLConnection), a pedido do nativo.
 * - Eventos chegam por `ouvinte`, em threads de fundo: quem escuta posta na UI.
 */
object RaNativo {
    init {
        System.loadLibrary("phoenix_emu")
    }

    interface Ouvinte {
        fun aoConquista(titulo: String, descricao: String, pontos: Int, badgeUrl: String)
        fun aoJogoCarregado(titulo: String, total: Int, desbloqueadas: Int, pontosTotal: Int, pontosGanhos: Int)
        /** 1 = login falhou, 2 = jogo nao carregou, 3 = jogo sem conquistas no RA. */
        fun aoErro(codigo: Int, mensagem: String)
    }

    @Volatile var ouvinte: Ouvinte? = null

    private val rede = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "ra-http").apply { isDaemon = true }
    }

    private external fun nativeRaIniciar(
        usuario: String, token: String, hash: String, consoleId: Int, hardcore: Boolean
    ): Boolean
    private external fun nativeRaParar()
    private external fun nativeRaResposta(id: Long, status: Int, corpo: ByteArray?)

    /** Chamar DEPOIS de o jogo estar carregado no nucleo. consoleId: NES = 7, SNES = 3. */
    fun iniciar(usuario: String, token: String, hash: String, consoleId: Int, hardcore: Boolean): Boolean =
        nativeRaIniciar(usuario, token, hash, consoleId, hardcore)

    /** Chamar DEPOIS de parar o laco do emulador. */
    fun parar() = nativeRaParar()

    private fun texto(b: ByteArray?): String = if (b == null) "" else String(b, Charsets.UTF_8)

    // ---- chamados pelo codigo nativo ----

    @JvmStatic
    fun pedidoHttp(id: Long, url: ByteArray, post: ByteArray?, tipo: ByteArray?) {
        rede.execute {
            var status = 0
            var corpo: ByteArray? = null
            var con: HttpURLConnection? = null
            try {
                val c = URL(texto(url)).openConnection() as HttpURLConnection
                con = c
                c.connectTimeout = 10_000
                c.readTimeout = 20_000
                c.setRequestProperty("User-Agent", "PhoenixEmu/1.0 (Android)")
                if (post != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty(
                        "Content-Type",
                        tipo?.let { texto(it) } ?: "application/x-www-form-urlencoded"
                    )
                    c.outputStream.use { it.write(post) }
                }
                status = c.responseCode
                val fluxo = if (status in 200..299) c.inputStream else c.errorStream
                corpo = fluxo?.use { it.readBytes() }
            } catch (_: Exception) {
                status = 0
                corpo = null
            } finally {
                con?.disconnect()
            }
            nativeRaResposta(id, status, corpo)
        }
    }

    @JvmStatic
    fun aoConquista(titulo: ByteArray, descricao: ByteArray, pontos: Int, badgeUrl: ByteArray) {
        ouvinte?.aoConquista(texto(titulo), texto(descricao), pontos, texto(badgeUrl))
    }

    @JvmStatic
    fun aoJogo(titulo: ByteArray, total: Int, desbloqueadas: Int, pontosTotal: Int, pontosGanhos: Int) {
        ouvinte?.aoJogoCarregado(texto(titulo), total, desbloqueadas, pontosTotal, pontosGanhos)
    }

    @JvmStatic
    fun aoErro(codigo: Int, mensagem: ByteArray) {
        ouvinte?.aoErro(codigo, texto(mensagem))
    }
}
