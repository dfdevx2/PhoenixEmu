package com.dfdx047.phoenixemu

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dfdx047.phoenixemu.data.Preferencias
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * SFX e BGM da interface.
 *
 * O comportamento sonoro e exatamente o de antes (mesmos arquivos em
 * res/raw, mesmo SoundPool, mesmo MediaPlayer em loop). O que mudou e onde
 * ele vive e como e controlado:
 *
 *  - Era criado no onCreate da Activity e liberado no onDestroy, entao
 *    girar a tela destruia e recriava tudo: a musica reiniciava do zero.
 *    Agora e um singleton de processo, observado pelo ProcessLifecycleOwner:
 *    pausa quando o APP vai para segundo plano, nao quando a Activity morre.
 *
 *  - Os volumes vinham de prefs.getFloat() lido a cada clique. Agora chegam
 *    por Flow e ficam em campos @Volatile: tocar um SFX nao faz I/O.
 *
 *  - Ganhou foco de audio: a BGM cede lugar quando o usuario abre o
 *    Spotify, atende uma chamada, etc.
 *
 *  - Ganhou `definirEmulacaoAtiva`, que a Fase 4 usa para calar a BGM
 *    enquanto um jogo esta rodando.
 */
class AudioEngine private constructor(context: Context) : DefaultLifecycleObserver {

    private val app = context.applicationContext
    private val prefs = Preferencias.obter(app)
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val atributos: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(atributos)
        .build()

    private var bgmPlayer: MediaPlayer? = null
    private var idClique = 0
    private var idSwipe = 0
    private val carregados = HashSet<Int>()

    // ---- SFX novos (TAREFA A1) ----
    private var idCapaPassa = 0
    private var idTrocaAba = 0
    private var idBoot = 0
    private var idGameBoot = 0

    /** Instante da última execução de CAPA_PASSA (throttle 60 ms). */
    private var ultimoCapaPassa = 0L

    // Cache dos ajustes: lidos dos Flows, nunca das prefs em tempo de clique.
    @Volatile private var sfxAtivo = true
    @Volatile private var sfxVolume = 1f
    @Volatile private var bgmAtivo = true
    @Volatile private var bgmVolume = 1f

    private var appVisivel = false
    private var emulacaoAtiva = false
    private var temFoco = false

    private val ouvinteDeFoco = AudioManager.OnAudioFocusChangeListener { mudanca ->
        when (mudanca) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                temFoco = true
                aplicarEstadoBgm()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                temFoco = false
                bgmPlayer?.takeIf { it.isPlaying }?.pause()
            }
        }
    }

    private val pedidoDeFoco: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(ouvinteDeFoco)
            .build()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) carregados += sampleId
        }
        try {
            idClique = soundPool.load(app, R.raw.sfx_click, 1)
            idSwipe = soundPool.load(app, R.raw.sfx_swipe, 1)
            // ajuste de ouvido — volumes relativos ao sfxVolume geral
            idCapaPassa = soundPool.load(app, R.raw.sfx_capa_passa, 1)
            idTrocaAba  = soundPool.load(app, R.raw.sfx_troca_aba, 1)
            idBoot      = soundPool.load(app, R.raw.sfx_boot, 1)
            idGameBoot  = soundPool.load(app, R.raw.sfx_game_boot, 1)
            bgmPlayer = MediaPlayer.create(app, R.raw.bgm_menu)?.apply { isLooping = true }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar os audios de res/raw", e)
        }

        // Um unico coletor para os quatro ajustes.
        escopo.launch {
            combine(
                prefs.sfxAtivo,
                prefs.sfxVolume,
                prefs.bgmAtivo,
                prefs.bgmVolume
            ) { sfxOn, sfxVol, bgmOn, bgmVol ->
                Ajustes(sfxOn, sfxVol, bgmOn, bgmVol)
            }.collect { a ->
                sfxAtivo = a.sfxAtivo
                sfxVolume = a.sfxVolume
                bgmAtivo = a.bgmAtivo
                bgmVolume = a.bgmVolume
                bgmPlayer?.setVolume(a.bgmVolume, a.bgmVolume)
                aplicarEstadoBgm()
            }
        }
    }

    private data class Ajustes(
        val sfxAtivo: Boolean,
        val sfxVolume: Float,
        val bgmAtivo: Boolean,
        val bgmVolume: Float
    )

    // ========================================================= SFX novos (TAREFA A1)
    // ajuste de ouvido — volumes relativos ao sfxVolume geral do motor
    private val VOLUME_RELATIVO = mapOf(
        Som.CAPA_PASSA to 0.20f,
        Som.TROCA_ABA  to 0.30f,
        Som.BOOT       to 0.35f,
        Som.GAME_BOOT  to 0.40f
    )

    /** Tipo seguro para os novos efeitos sonoros. */
    sealed class Som {
        object CAPA_PASSA : Som()
        object TROCA_ABA  : Som()
        object BOOT       : Som()
        object GAME_BOOT  : Som()
        val cooldownMs: Long
            get() = when (this) {
                CAPA_PASSA -> 60L
                else       -> 0L
            }
    }

    /** Mapa SoundPoolId → Som, preenchido apos carregamento dos recursos. */
    private val somPorId = mutableMapOf<Int, Som>()

    /** Mapeia um Som para seu id do SoundPool (apos carregamento). */
    private fun AudioEngine.idDoSom(som: Som): Int = when (som) {
        Som.CAPA_PASSA -> idCapaPassa
        Som.TROCA_ABA  -> idTrocaAba
        Som.BOOT       -> idBoot
        Som.GAME_BOOT  -> idGameBoot
    }

    /** Mapeia um Som para a preferencia individual correspondente (valor atual). */
    private fun AudioEngine.ehAtivoIndividual(som: Som): Boolean = when (som) {
        Som.CAPA_PASSA  -> prefs.somCapaPassa.value
        Som.TROCA_ABA   -> prefs.somTrocaAba.value
        Som.BOOT        -> prefs.somBoot.value
        Som.GAME_BOOT   -> prefs.somEntrarJogo.value
    }

    /** Preenche o mapa inverso (id → Som) apos os loads terminarem. */
    private fun reconstruirMapaSom() {
        somPorId.clear()
        for (s in arrayOf(Som.CAPA_PASSA, Som.TROCA_ABA, Som.BOOT, Som.GAME_BOOT)) {
            val id = idDoSom(s)
            if (id != 0) somPorId[id] = s
        }
    }

    /** Toca um efeito novo, respeitando as mesmas condições de silêncio que sfx_click/swipe. */
    fun tocar(som: Som) {
        val id = idDoSom(som)
        if (!sfxAtivo || !ehAtivoIndividual(som) || id == 0 || id !in carregados) return
        // Throttle para CAPA_PASSA
        if (som.cooldownMs > 0L) {
            val agora = SystemClock.uptimeMillis()
            if (agora - ultimoCapaPassa < som.cooldownMs) return
            ultimoCapaPassa = agora
        }
        val volumeRelativo = VOLUME_RELATIVO[som] ?: 1f
        soundPool.play(id, sfxVolume * volumeRelativo, sfxVolume * volumeRelativo, 1, 0, 1f)
    }

    // ------------------------------------------------------ ciclo de vida

    /** Chamado uma vez pela Application com o ProcessLifecycleOwner. */
    fun observar(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        appVisivel = true
        aplicarEstadoBgm()
    }

    override fun onStop(owner: LifecycleOwner) {
        appVisivel = false
        aplicarEstadoBgm()
    }

    /** A Fase 4 chama isto ao abrir e fechar o emulador. */
    fun definirEmulacaoAtiva(ativa: Boolean) {
        emulacaoAtiva = ativa
        aplicarEstadoBgm()
    }

    // ------------------------------------------------------------- tocar

    fun playClick() = tocar(idClique)

    fun playSwipe() = tocar(idSwipe)

    private fun tocar(id: Int) {
        if (!sfxAtivo || id == 0 || id !in carregados) return
        soundPool.play(id, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    private fun aplicarEstadoBgm() {
        val player = bgmPlayer ?: return
        val deveTocar = bgmAtivo && appVisivel && !emulacaoAtiva

        if (deveTocar) {
            if (!temFoco) {
                temFoco = audioManager.requestAudioFocus(pedidoDeFoco) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
            if (temFoco && !player.isPlaying) {
                runCatching { player.start() }
                    .onFailure { Log.w(TAG, "Nao foi possivel iniciar a BGM", it) }
            }
        } else {
            if (player.isPlaying) runCatching { player.pause() }
            if (temFoco) {
                audioManager.abandonAudioFocusRequest(pedidoDeFoco)
                temFoco = false
            }
        }
    }

    /**
     * So deve ser chamado se o processo estiver realmente terminando.
     * Sendo singleton de processo, o normal e nunca chamar.
     */
    fun release() {
        runCatching { bgmPlayer?.release() }
        bgmPlayer = null
        soundPool.release()
        if (temFoco) {
            audioManager.abandonAudioFocusRequest(pedidoDeFoco)
            temFoco = false
        }
    }

    companion object {
        private const val TAG = "AudioEngine"

        @Volatile
        private var instancia: AudioEngine? = null

        fun obter(context: Context): AudioEngine =
            instancia ?: synchronized(this) {
                instancia ?: AudioEngine(context).also { instancia = it }
            }
    }
}

/**
 * Acaba com o repasse manual de `audioEngine` por parametro em toda a
 * arvore de composicao. Fornecido uma vez na MainActivity.
 */
val LocalAudio = staticCompositionLocalOf<AudioEngine> {
    error("LocalAudio nao foi fornecido. Envolva a UI em CompositionLocalProvider(LocalAudio provides ...).")
}
