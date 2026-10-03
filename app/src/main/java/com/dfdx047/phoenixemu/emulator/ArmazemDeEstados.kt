package com.dfdx047.phoenixemu.emulator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class ArmazemDeEstados(
    private val savesDir: File?,
    private val nomeSave: String,
    private val textoVazio: String = "vazio"
) {

    fun arquivoDoEstado(slot: Int): File? {
        if (savesDir == null) return null
        return File(savesDir, "$nomeSave.state$slot")
    }

    fun arquivoDaMiniatura(slot: Int): File? {
        if (savesDir == null) return null
        return File(savesDir, "$nomeSave.state$slot.png")
    }

    fun listarSlots(): List<SlotData> {
        val list = mutableListOf<SlotData>()
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        for (slot in 1..4) {
            if (savesDir != null) {
                val stateFile = File(savesDir, "$nomeSave.state$slot")
                val pngFile = File(savesDir, "$nomeSave.state$slot.png")

                if (stateFile.exists() && stateFile.length() > 0) {
                    val dateStr = dateFormat.format(Date(stateFile.lastModified()))
                    val bmp = if (pngFile.exists()) {
                        try {
                            BitmapFactory.decodeFile(pngFile.absolutePath)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    list.add(SlotData(slot, true, dateStr, bmp))
                } else {
                    list.add(SlotData(slot, false, textoVazio, null))
                }
            } else {
                list.add(SlotData(slot, false, textoVazio, null))
            }
        }
        return list
    }

    fun temEstado(slot: Int): Boolean {
        val fileState = arquivoDoEstado(slot) ?: return false
        return fileState.exists() && fileState.length() > 0L
    }

    fun gravarBytes(slot: Int, bytes: ByteArray) {
        val savesDir = this.savesDir ?: return
        try {
            val stateFile = File(savesDir, "$nomeSave.state$slot")
            val tmpStateFile = File(savesDir, "$nomeSave.state$slot.tmp")
            tmpStateFile.writeBytes(bytes)
            if (!tmpStateFile.renameTo(stateFile)) {
                tmpStateFile.copyTo(stateFile, overwrite = true)
                tmpStateFile.delete()
            }
        } catch (e: Exception) {
            // fallback if anything goes wrong
        }
    }

    fun gravarMiniatura(slot: Int, bmp: Bitmap) {
        val savesDir = this.savesDir ?: return
        try {
            val pngFile = File(savesDir, "$nomeSave.state$slot.png")
            val tmpPngFile = File(savesDir, "$nomeSave.state$slot.png.tmp")
            FileOutputStream(tmpPngFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmpPngFile.renameTo(pngFile)) {
                tmpPngFile.copyTo(pngFile, overwrite = true)
                tmpPngFile.delete()
            }
        } catch (e: Exception) {}
    }

    fun lerBytes(slot: Int): ByteArray? {
        val stateFile = arquivoDoEstado(slot) ?: return null
        return if (stateFile.exists()) {
            try { stateFile.readBytes() } catch (e: Exception) { null }
        } else null
    }
}
