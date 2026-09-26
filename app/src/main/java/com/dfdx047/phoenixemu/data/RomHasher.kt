package com.dfdx047.phoenixemu.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dfdx047.phoenixemu.Sistema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/**
 * Identidade binaria da ROM.
 *
 * Duas coisas diferentes, calculadas na mesma passada:
 *
 *  - **CRC32 do arquivo inteiro**: e o que as DATs No-Intro publicam. Serve
 *    para casar a ROM com o nome canonico dela mais tarde e melhorar o
 *    acerto das capas.
 *  - **MD5 no formato RetroAchievements**: o RA nao hasheia o arquivo cru.
 *    No NES ele ignora o cabecalho iNES de 16 bytes; no SNES ele ignora o
 *    cabecalho de copiador de 512 bytes, quando presente. Errar isso nao da
 *    erro visivel: da "jogo nao reconhecido" no Phase 5 e horas perdidas
 *    procurando o motivo.
 *
 * Por que isto NAO roda na varredura: hashear exige ler cada byte de cada
 * ROM. Uma biblioteca de 5.000 SNES com media de 2 MB significa 10 GB lidos
 * atraves do SAF, que e a camada mais lenta disponivel. O hash existe para o
 * RetroAchievements, entao ele pode chegar tarde, em segundo plano, ou nem
 * chegar.
 */
object RomHasher {

    private const val TAG = "RomHasher"
    private const val BUFFER = 64 * 1024

    /** Cabecalho de copiador do SNES. Presente quando tamanho % 1024 == 512. */
    private const val CABECALHO_COPIADOR_SNES = 512

    /** Cabecalho iNES. Presente quando os 4 primeiros bytes sao "NES\u001A". */
    private const val CABECALHO_INES = 16

    private val MAGICO_INES = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) // N E S 0x1A

    data class Identidade(
        val crc32: String,
        val hashRa: String,
        val bytesLidos: Long
    )

    suspend fun calcular(context: Context, uri: Uri, extensao: String, sistema: Sistema): Identidade? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { bruto ->
                    if (extensao == "zip") {
                        comZip(bruto, sistema)
                    } else {
                        umaPassada(bruto, sistema)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Nao foi possivel hashear $uri", e)
                null
            }
        }

    /** O RA hasheia a ROM de DENTRO do zip, nao o zip. */
    private fun comZip(entrada: InputStream, sistema: Sistema): Identidade? {
        ZipInputStream(entrada).use { zis ->
            var entry = zis.nextEntry
            var lidas = 0
            while (entry != null && lidas < 500) {
                if (!entry.isDirectory) {
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (ext in RomScanner.EXTENSOES_ACEITAS && ext != "zip") {
                        // Nao fechamos o zis aqui: umaPassada le so o stream da entrada.
                        return umaPassada(zis, sistema, fecharEntrada = false)
                    }
                }
                lidas++
                entry = zis.nextEntry
            }
        }
        return null
    }

    /**
     * Uma unica leitura do stream alimentando tres digestores.
     *
     * O truque do SNES: saber se ha cabecalho de copiador exige o tamanho
     * total, que nem sempre esta disponivel antes de ler (entradas de zip
     * costumam reportar -1). Em vez de exigir o tamanho, mantemos dois MD5 em
     * paralelo -- um do arquivo inteiro e um ignorando os primeiros 512 bytes
     * -- e escolhemos no final, quando o tamanho real ja e conhecido. MD5 e
     * barato; reler a ROM nao e.
     */
    private fun umaPassada(
        entrada: InputStream,
        sistema: Sistema,
        fecharEntrada: Boolean = true
    ): Identidade? {
        val crc = CRC32()
        val md5Inteiro = MessageDigest.getInstance("MD5")
        val md5SemCabecalho = MessageDigest.getInstance("MD5")

        val pulo = when (sistema) {
            Sistema.NES -> CABECALHO_INES
            Sistema.SNES -> CABECALHO_COPIADOR_SNES
        }

        val buffer = ByteArray(BUFFER)
        var total = 0L
        // So precisamos dos 4 primeiros bytes: o NES se identifica pelo magico
        // e o SNES pelo tamanho total.
        val inicio = ByteArray(MAGICO_INES.size)
        var inicioPreenchido = 0

        try {
            while (true) {
                val lidos = entrada.read(buffer)
                if (lidos <= 0) break

                crc.update(buffer, 0, lidos)
                md5Inteiro.update(buffer, 0, lidos)

                // Guarda os primeiros bytes para inspecionar o cabecalho depois.
                if (inicioPreenchido < inicio.size) {
                    val copiar = minOf(inicio.size - inicioPreenchido, lidos)
                    System.arraycopy(buffer, 0, inicio, inicioPreenchido, copiar)
                    inicioPreenchido += copiar
                }

                // Alimenta o segundo MD5 apenas com o que vem depois do cabecalho.
                val jaLidoAntes = total
                val inicioUtil = (pulo - jaLidoAntes).coerceIn(0L, lidos.toLong()).toInt()
                if (inicioUtil < lidos) {
                    md5SemCabecalho.update(buffer, inicioUtil, lidos - inicioUtil)
                }

                total += lidos
            }
        } finally {
            if (fecharEntrada) entrada.close()
        }

        if (total == 0L) return null

        val temCabecalho = when (sistema) {
            // NES: cabecalho iNES e detectado pelo magico, deterministico.
            Sistema.NES -> inicioPreenchido >= MAGICO_INES.size &&
                inicio.contentEquals(MAGICO_INES)
            // SNES: cabecalho de copiador existe quando sobram 512 bytes.
            Sistema.SNES -> total % 1024L == CABECALHO_COPIADOR_SNES.toLong()
        }

        val escolhido = if (temCabecalho && total > pulo) md5SemCabecalho else md5Inteiro

        return Identidade(
            crc32 = String.format("%08x", crc.value),
            hashRa = escolhido.digest().toHex(),
            bytesLidos = total
        )
    }

    private fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        val digitos = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            hex[i * 2] = digitos[v ushr 4]
            hex[i * 2 + 1] = digitos[v and 0x0F]
        }
        return String(hex)
    }
}
