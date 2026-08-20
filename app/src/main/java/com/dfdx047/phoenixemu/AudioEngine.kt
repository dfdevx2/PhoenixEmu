package com.dfdx047.phoenixemu

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class AudioEngine(private val context: Context) {

    // O Motor de Música Ambiente (Loop Contínuo)
    private var mediaPlayer: MediaPlayer? = null

    // O Motor de Efeitos Sonoros (Cliques Rápidos)
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var swipeSoundId: Int = 0

    init {
        // Inicializa o BGM e diz ao sistema que é um arquivo de repetição
        mediaPlayer = MediaPlayer.create(context, R.raw.bgm_menu)
        mediaPlayer?.isLooping = true
        // Deixamos a música com 30% do volume máximo para não ofuscar os efeitos sonoros
        mediaPlayer?.setVolume(0.3f, 0.3f)

        // Configura o SoundPool (Moderno e otimizado para áudios de interface/jogos)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4) // Define que até 4 sons diferentes podem tocar exatamente no mesmo segundo
            .setAudioAttributes(audioAttributes)
            .build()

        // Pré-carrega os SFX de forma bruta diretamente na memória RAM
        clickSoundId = soundPool?.load(context, R.raw.sfx_click, 1) ?: 0
        swipeSoundId = soundPool?.load(context, R.raw.sfx_swipe, 1) ?: 0
    }

    // --- FUNÇÕES QUE CHAMAREMOS LÁ DA INTERFACE ---

    fun playBgm() {
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    fun pauseBgm() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    fun playClick() {
        soundPool?.play(clickSoundId, 1f, 1f, 0, 0, 1f)
    }

    fun playSwipe() {
        // Volume 60% para o swipe, para que deslizar o dedo não se torne um ruído agressivo
        soundPool?.play(swipeSoundId, 0.6f, 0.6f, 0, 0, 1f)
    }

    // Libera a memória RAM e a placa de som quando o app for completamente fechado
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        soundPool?.release()
        soundPool = null
    }
}