package com.dfdx047.phoenixemu

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class AudioEngine(private val context: Context) {
    private var bgmPlayer: MediaPlayer? = null
    private var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var swipeSoundId: Int = 0

    // Conecta-se às configurações que guardamos na Tela de Configurações
    private val prefs = context.getSharedPreferences("EmulatorSettings", Context.MODE_PRIVATE)

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            // Carrega os sons (assumindo que tem os ficheiros na pasta res/raw)
            clickSoundId = soundPool.load(context, R.raw.sfx_click, 1)
            swipeSoundId = soundPool.load(context, R.raw.sfx_swipe, 1)
            bgmPlayer = MediaPlayer.create(context, R.raw.bgm_menu)
            bgmPlayer?.isLooping = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Aplica os volumes logo ao iniciar!
        atualizarVolumes()
    }

    // A MÁGICA ACONTECE AQUI: Lê as configurações e aplica na hora!
    fun atualizarVolumes() {
        val bgmEnabled = prefs.getBoolean("bgm_enabled", true)
        val sfxEnabled = prefs.getBoolean("sfx_enabled", true)

        val bgmVolume = if (bgmEnabled) prefs.getFloat("bgm_volume", 1.0f) else 0f
        bgmPlayer?.setVolume(bgmVolume, bgmVolume)

        // Se a música foi ativada e não está a tocar, inicia
        if (bgmEnabled && bgmPlayer?.isPlaying == false) {
            bgmPlayer?.start()
        }
        // Se a música foi desativada e está a tocar, pausa
        else if (!bgmEnabled && bgmPlayer?.isPlaying == true) {
            bgmPlayer?.pause()
        }
    }

    fun playClick() {
        if (prefs.getBoolean("sfx_enabled", true)) {
            val vol = prefs.getFloat("sfx_volume", 1.0f)
            soundPool.play(clickSoundId, vol, vol, 1, 0, 1f)
        }
    }

    fun playSwipe() {
        if (prefs.getBoolean("sfx_enabled", true)) {
            val vol = prefs.getFloat("sfx_volume", 1.0f)
            soundPool.play(swipeSoundId, vol, vol, 1, 0, 1f)
        }
    }

    fun playBgm() {
        if (prefs.getBoolean("bgm_enabled", true)) {
            bgmPlayer?.start()
        }
    }

    fun pauseBgm() {
        bgmPlayer?.pause()
    }

    fun release() {
        bgmPlayer?.release()
        soundPool.release()
    }
}