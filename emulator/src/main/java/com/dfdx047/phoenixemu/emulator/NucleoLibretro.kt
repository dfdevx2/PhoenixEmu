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
    private external fun nativeCarregarJogo(rom: ByteArray?, caminho: String?): Boolean
    private external fun nativeIniciarLaco(surface: Surface)
    private external fun nativePararLaco()
    private external fun nativeDefinirBotoes(porta: Int, mascara: Int)
    private external fun nativeDescarregar()
    private external fun nativeObterAspectRatio(): Float
    private external fun nativeReiniciar()
    private external fun nativePrecisaDeFullPath(): Boolean
    private external fun nativeCarregarSram(caminho: String)
    private external fun nativeSalvarSram(caminho: String)
    private external fun nativeDefinirCaminhoSram(caminho: String)
    private external fun nativePedirSalvarSram()
    private external fun nativeTamanhoEstado(): Int
    private external fun nativeSalvarEstado(): ByteArray?
    private external fun nativeCarregarEstado(dados: ByteArray): Boolean
    private external fun nativeDefinirAvancoRapido(velocidade: Int)
    private external fun nativeObterStats(): FloatArray
    private external fun nativeDefinirRewind(ativo: Boolean)
    private external fun nativeObterRewindSegundos(): Float
    private external fun nativePedirEstado(tipo: Int, caminho: String): Boolean
    private external fun nativeResultadoEstado(): Int
    private external fun nativeDefinirPausa(pausado: Boolean)
    private external fun nativeDefinirVolume(volume: Float)

    fun definirPastas(sistema: String, saves: String) = nativeDefinirPastas(sistema, saves)
    
    fun info(): String = nativeInfo()

    fun carregar(caminhoDoSo: String): Boolean = nativeCarregar(caminhoDoSo)

    fun carregarJogo(rom: ByteArray?, caminho: String? = null): Boolean = nativeCarregarJogo(rom, caminho)

    fun iniciar(surface: Surface) = nativeIniciarLaco(surface)

    fun parar() = nativePararLaco()

    /** Chamado da thread da UI; o nucleo le uma vez por quadro. */
    fun definirBotoes(porta: Int, mascara: Int) = nativeDefinirBotoes(porta, mascara)

    fun descarregar() = nativeDescarregar()

    fun obterAspectRatio(): Float = nativeObterAspectRatio()

    fun reiniciar() = nativeReiniciar()
    
    fun precisaDeFullPath(): Boolean = nativePrecisaDeFullPath()
    
    fun carregarSram(caminho: String) = nativeCarregarSram(caminho)
    
    fun salvarSram(caminho: String) = nativeSalvarSram(caminho)
    
    fun definirCaminhoSram(caminho: String) = nativeDefinirCaminhoSram(caminho)
    
    fun pedirSalvarSram() = nativePedirSalvarSram()

    /** Retorna o tamanho do save-state em bytes (0 se indisponivel). */
    fun tamanhoEstado(): Int = nativeTamanhoEstado()

    /** Salva o estado atual e retorna os bytes. Null se falhar ou laço rodando. */
    fun salvarEstado(): ByteArray? = nativeSalvarEstado()

    /** Carrega um estado a partir de dados. Retorna true se ok. */
    fun carregarEstado(dados: ByteArray): Boolean = nativeCarregarEstado(dados)
    
    fun definirAvancoRapido(velocidade: Int) = nativeDefinirAvancoRapido(velocidade)
    
    fun obterStats(): FloatArray = nativeObterStats()

    fun definirRewind(ativo: Boolean) = nativeDefinirRewind(ativo)
    fun obterRewindSegundos(): Float = nativeObterRewindSegundos()
    
    fun pedirEstado(tipo: Int, caminho: String): Boolean = nativePedirEstado(tipo, caminho)
    fun resultadoEstado(): Int = nativeResultadoEstado()
    fun definirPausa(pausado: Boolean) = nativeDefinirPausa(pausado)
    fun definirVolume(volume: Float) = nativeDefinirVolume(volume)

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
