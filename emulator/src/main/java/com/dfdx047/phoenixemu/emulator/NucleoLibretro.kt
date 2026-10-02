package com.dfdx047.phoenixemu.emulator

import android.view.Surface

/**
 * Lado Kotlin da ponte JNI.
 *
 * ESTADO: esqueleto (Fase 4a). As assinaturas estao fechadas; os corpos
 * nativos estao em `src/main/cpp/jni_bridge.cpp`, com os TODOs marcados.
 *
 * Por que a ROM e passada como ByteArray: o lado nativo nao tem como abrir
 * uma URI do Storage Access Framework. O Kotlin le (e descompacta o zip, se
 * for o caso) e entrega os bytes. Se o nucleo declarar `need_fullpath`, o
 * arquivo e copiado para o cache e passamos o caminho.
 */
class NucleoLibretro {

    private external fun nativeDefinirPastas(sistema: String, saves: String)
    private external fun nativeInfo(): String
    private external fun nativeCarregar(caminhoDoSo: String): Boolean
    private external fun nativeCarregarJogo(rom: ByteArray?): Boolean
    private external fun nativeIniciarLaco(surface: Surface)
    private external fun nativePararLaco()
    private external fun nativeDefinirBotoes(porta: Int, mascara: Int)
    private external fun nativeDescarregar()
    private external fun nativeObterAspectRatio(): Float

    fun definirPastas(sistema: String, saves: String) = nativeDefinirPastas(sistema, saves)
    
    fun info(): String = nativeInfo()

    fun carregar(caminhoDoSo: String): Boolean = nativeCarregar(caminhoDoSo)

    fun carregarJogo(rom: ByteArray?): Boolean = nativeCarregarJogo(rom)

    fun iniciar(surface: Surface) = nativeIniciarLaco(surface)

    fun parar() = nativePararLaco()

    /** Chamado da thread da UI; o nucleo le uma vez por quadro. */
    fun definirBotoes(porta: Int, mascara: Int) = nativeDefinirBotoes(porta, mascara)

    fun descarregar() = nativeDescarregar()

    fun obterAspectRatio(): Float = nativeObterAspectRatio()

    /** Bits do RETRO_DEVICE_ID_JOYPAD, na ordem da API libretro. */
    object Botao {
        const val B = 1 shl 0
        const val Y = 1 shl 1
        const val SELECT = 1 shl 2
        const val START = 1 shl 3
        const val CIMA = 1 shl 4
        const val BAIXO = 1 shl 5
        const val ESQUERDA = 1 shl 6
        const val DIREITA = 1 shl 7
        const val A = 1 shl 8
        const val X = 1 shl 9
        const val L = 1 shl 10
        const val R = 1 shl 11
    }

    companion object {
        init {
            System.loadLibrary("phoenix_emu")
        }
    }
}
