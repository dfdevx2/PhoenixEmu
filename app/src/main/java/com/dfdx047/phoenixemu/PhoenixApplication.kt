package com.dfdx047.phoenixemu

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dfdx047.phoenixemu.data.BibliotecaStore
import com.dfdx047.phoenixemu.data.Preferencias
import java.io.File

/**
 * Antes nao existia classe Application, e isso custava caro:
 *
 *  - O ImageLoader do Coil era construido DENTRO de PhoenixApp, ou seja, um
 *    novo a cada recomposicao em que houvesse wallpaper. Cada instancia cria
 *    caches proprios: vazamento de memoria e engasgo ao mesmo tempo.
 *    Implementando ImageLoaderFactory, `context.imageLoader` devolve sempre
 *    o mesmo, com cache de memoria e de disco de verdade.
 *
 *  - O AudioEngine era da Activity, entao a musica reiniciava a cada
 *    rotacao. Aqui ele e observado pelo ciclo de vida do PROCESSO.
 */
class PhoenixApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        val nome = obterNomeDoProcesso()
        if (ehProcessoDoEmulador()) {
            Log.i("PhoenixProc", "processo=$nome emu=true (sem singletons)")
            return
        } else {
            Log.i("PhoenixProc", "processo=$nome emu=false")
        }

        if (BuildConfig.DEBUG) ligarStrictMode()

        // Instancia os singletons cedo, fora de qualquer composicao.
        Preferencias.obter(this)
        BibliotecaStore.obter(this)
        AudioEngine.obter(this).observar(ProcessLifecycleOwner.get())
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                // GIF e WebP animado para os wallpapers.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                // As capas sao pequenas; 20% da heap sobra e evita releitura
                // de disco durante o scroll.
                MemoryCache.Builder(this).maxSizePercent(0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("capas"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // o servidor do Libretro nao manda cache headers uteis
            .build()

    private fun obterNomeDoProcesso(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            try {
                File("/proc/self/cmdline").readText().trim('\u0000')
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun ehProcessoDoEmulador(): Boolean {
        return obterNomeDoProcesso().endsWith(":emu")
    }

    /**
     * Em debug, o app RECLAMA em vez de deixar passar: I/O na main thread,
     * cursor nao fechado, URI de arquivo exposta. Era exatamente assim que
     * o ANR do seletor de pastas passava despercebido.
     */
    private fun ligarStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }
}
