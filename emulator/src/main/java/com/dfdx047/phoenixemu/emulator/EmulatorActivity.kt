package com.dfdx047.phoenixemu.emulator

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Host da emulacao.
 *
 * ESTADO: esqueleto (Fase 4a). Ela ja nasce com as tres decisoes que sao
 * caras de mudar depois:
 *
 *  1. **Processo separado** (`android:process=":emu"` no manifesto). Nucleos
 *     libretro tem estado global e `dlclose` nao o limpa de forma confiavel.
 *     Um processo novo por sessao garante inicio limpo, e um crash do nucleo
 *     nao derruba a biblioteca.
 *  2. **SurfaceView, nao TextureView.** TextureView passa por uma composicao
 *     extra e acrescenta latencia.
 *  3. **O overlay de toque e Compose POR CIMA da SurfaceView**, nunca
 *     dentro dela.
 */
class EmulatorActivity : ComponentActivity() {

    private val nucleo = NucleoLibretro()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { contexto ->
                        SurfaceView(contexto).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    nucleo.iniciar(holder.surface)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    largura: Int,
                                    altura: Int
                                ) = Unit

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    nucleo.parar()
                                }
                            })
                        }
                    }
                )

                // TODO (Fase 4c): overlay de toque em Compose, aqui por cima.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nucleo.parar()
        nucleo.descarregar()
    }
}
