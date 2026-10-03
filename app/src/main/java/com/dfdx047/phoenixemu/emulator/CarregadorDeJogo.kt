package com.dfdx047.phoenixemu.emulator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

internal class CarregadorDeJogo(
    private val context: Context,
    private val nucleo: NucleoLibretro
) {
    internal class Resultado(
        val erro: String?,
        val info: String,
        val aspectRatio: Float,
        val srmPath: String?,
        val jogoReal: Boolean,
        val autoloadAplicado: Boolean
    )

    fun carregar(
        nomeDoNucleo: String, romUri: String?,
        nomeSave: String, autoCarregar: Boolean
    ): Resultado {
        val libraryPath = "${context.applicationInfo.nativeLibraryDir}/$nomeDoNucleo"
        val file = File(libraryPath)

        var infoMessage = ""
        var loadError: String? = null
        var srmPath: String? = null
        var isJogoReal = false
        var aspectRatio = 4f / 3f
        var autoloadAplicado = false

        if (!file.exists()) {
            loadError = context.getString(com.dfdx047.phoenixemu.R.string.jogo_erro_nucleo_nao_encontrado, libraryPath)
        } else {
            val systemDir = File(context.filesDir, "system").apply { mkdirs() }
            val savesDir = File(context.filesDir, "saves").apply { mkdirs() }
            srmPath = File(savesDir, "$nomeSave.srm").absolutePath

            try {
                nucleo.definirPastas(systemDir.absolutePath, savesDir.absolutePath)
                if (!nucleo.carregar(libraryPath)) {
                    loadError = context.getString(com.dfdx047.phoenixemu.R.string.jogo_erro_falha_carregar_nucleo)
                } else {
                    var romBytes: ByteArray? = null
                    var romPath: String? = null
                    
                    if (romUri != null) {
                        val uri = Uri.parse(romUri)
                        val resolver = context.contentResolver
                        
                        var romDisplayName = "rom.bin"
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                romDisplayName = cursor.getString(0) ?: "rom.bin"
                            }
                        }
                        
                        var effectiveRomName = romDisplayName
                        
                        var inputStream = resolver.openInputStream(uri)
                        
                        if (inputStream != null) {
                            if (romDisplayName.lowercase().endsWith(".zip")) {
                                val zis = ZipInputStream(inputStream)
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    val name = entry.name.lowercase()
                                    if (name.endsWith(".nes") || name.endsWith(".sfc") || name.endsWith(".smc")) {
                                        romBytes = zis.readBytes()
                                        effectiveRomName = entry.name.substringAfterLast('/')
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                                zis.close()
                            } else {
                                romBytes = inputStream.readBytes()
                                inputStream.close()
                            }
                        }
                        
                        val needFullpath = nucleo.precisaDeFullPath()
                        romPath = File(context.cacheDir, effectiveRomName).absolutePath
                        
                        if (needFullpath && romBytes != null) {
                            File(romPath).writeBytes(romBytes!!)
                        }
                        
                        Log.i("PhoenixLibretro", "Núcleo: $nomeDoNucleo, ROM: $effectiveRomName, need_fullpath: $needFullpath, bytes: ${romBytes?.size ?: 0}")
                    }
                    
                    if (romUri != null && romBytes == null) {
                        loadError = context.getString(com.dfdx047.phoenixemu.R.string.jogo_erro_falha_ler_rom)
                    } else if (!nucleo.carregarJogo(romBytes, romPath)) {
                        loadError = context.getString(com.dfdx047.phoenixemu.R.string.jogo_erro_falha_carregar_jogo)
                    } else {
                        if (romUri != null) {
                            isJogoReal = true
                        }
                        infoMessage = nucleo.info()
                        aspectRatio = nucleo.obterAspectRatio()
                        
                        srmPath?.let {
                            nucleo.carregarSram(it)
                            nucleo.definirCaminhoSram(it)
                        }

                        if (isJogoReal && autoCarregar) {
                            val autoFile = File(savesDir, "$nomeSave.auto")
                            if (autoFile.exists() && autoFile.length() > 0) {
                                try {
                                    val bytes = autoFile.readBytes()
                                    if (nucleo.carregarEstado(bytes)) {
                                        autoloadAplicado = true
                                        Log.i("PhoenixLibretro", "autoload: ok")
                                    } else {
                                        Log.w("PhoenixLibretro", "autoload: falhou")
                                    }
                                } catch (e: Exception) {
                                    Log.w("PhoenixLibretro", "autoload: falhou")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                loadError = context.getString(com.dfdx047.phoenixemu.R.string.jogo_erro_generico, e.message)
            }
        }

        return Resultado(
            erro = loadError,
            info = infoMessage,
            aspectRatio = aspectRatio,
            srmPath = srmPath,
            jogoReal = isJogoReal,
            autoloadAplicado = autoloadAplicado
        )
    }
}
