package com.dfdx047.phoenixemu.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.dfdx047.phoenixemu.FiltroBiblioteca
import com.dfdx047.phoenixemu.ModoVisual
import com.dfdx047.phoenixemu.Ordenacao
import com.dfdx047.phoenixemu.TemaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unica porta de entrada das preferencias do app.
 *
 * Por que existe:
 *  - O tema estava em `remember { mutableStateOf(DINAMICO) }` dentro da
 *    Activity, ou seja, voltava ao padrao a cada rotacao e a cada reinicio.
 *  - O wallpaper era lido com `prefs.getString(...)` direto no composable,
 *    entao trocar o fundo nas Configuracoes nao atualizava a Biblioteca.
 *  - O AudioEngine relia as prefs em cada clique.
 *
 * Tudo aqui e exposto como StateFlow, entao a UI e o audio reagem na hora.
 * As chaves sao exatamente as antigas: nada do que o usuario ja configurou
 * se perde ao atualizar.
 *
 * Na Fase 1 isto vira DataStore com SharedPreferencesMigration; a interface
 * publica (os flows) foi desenhada para nao mudar quando isso acontecer.
 */
class Preferencias private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- tema
    private val _tema = MutableStateFlow(TemaApp.deNome(prefs.getString(K_TEMA, null)))
    val tema: StateFlow<TemaApp> = _tema.asStateFlow()

    fun definirTema(valor: TemaApp) {
        prefs.edit { putString(K_TEMA, valor.name) }
        _tema.value = valor
    }

    // --------------------------------------------------------------- audio
    private val _bgmAtivo = MutableStateFlow(prefs.getBoolean(K_BGM_ON, true))
    val bgmAtivo: StateFlow<Boolean> = _bgmAtivo.asStateFlow()

    private val _sfxAtivo = MutableStateFlow(prefs.getBoolean(K_SFX_ON, true))
    val sfxAtivo: StateFlow<Boolean> = _sfxAtivo.asStateFlow()

    private val _bgmVolume = MutableStateFlow(prefs.getFloat(K_BGM_VOL, 1f))
    val bgmVolume: StateFlow<Float> = _bgmVolume.asStateFlow()

    private val _sfxVolume = MutableStateFlow(prefs.getFloat(K_SFX_VOL, 1f))
    val sfxVolume: StateFlow<Float> = _sfxVolume.asStateFlow()

    fun definirBgmAtivo(valor: Boolean) {
        prefs.edit { putBoolean(K_BGM_ON, valor) }
        _bgmAtivo.value = valor
    }

    fun definirSfxAtivo(valor: Boolean) {
        prefs.edit { putBoolean(K_SFX_ON, valor) }
        _sfxAtivo.value = valor
    }

    fun definirBgmVolume(valor: Float) {
        val v = valor.coerceIn(0f, 1f)
        prefs.edit { putFloat(K_BGM_VOL, v) }
        _bgmVolume.value = v
    }

    fun definirSfxVolume(valor: Float) {
        val v = valor.coerceIn(0f, 1f)
        prefs.edit { putFloat(K_SFX_VOL, v) }
        _sfxVolume.value = v
    }

    // ----------------------------------------------------------- aparencia
    private val _wallpaperUri = MutableStateFlow(prefs.getString(K_WALLPAPER, null))
    val wallpaperUri: StateFlow<String?> = _wallpaperUri.asStateFlow()

    fun definirWallpaper(uri: Uri?) {
        prefs.edit {
            if (uri == null) remove(K_WALLPAPER) else putString(K_WALLPAPER, uri.toString())
        }
        _wallpaperUri.value = uri?.toString()
    }

    /**
     * Fase 3 usa isto para escolher entre blur ao vivo (RenderEffect) e
     * scrim translucido. Ja fica salvo agora para nao precisar de migracao.
     */
    private val _reduzirEfeitos = MutableStateFlow(prefs.getBoolean(K_REDUZIR_EFEITOS, false))
    val reduzirEfeitos: StateFlow<Boolean> = _reduzirEfeitos.asStateFlow()

    fun definirReduzirEfeitos(valor: Boolean) {
        prefs.edit { putBoolean(K_REDUZIR_EFEITOS, valor) }
        _reduzirEfeitos.value = valor
    }

    // ------------------------------------------------------------ vitrine
    private val _modoVisual = MutableStateFlow(
        runCatching { ModoVisual.valueOf(prefs.getString(K_MODO_VISUAL, null) ?: "") }
            .getOrDefault(ModoVisual.GRADE)
    )
    val modoVisual: StateFlow<ModoVisual> = _modoVisual.asStateFlow()

    fun definirModoVisual(valor: ModoVisual) {
        prefs.edit { putString(K_MODO_VISUAL, valor.name) }
        _modoVisual.value = valor
    }

    private val _filtro = MutableStateFlow(FiltroBiblioteca.deNome(prefs.getString(K_FILTRO, null)))
    val filtro: StateFlow<FiltroBiblioteca> = _filtro.asStateFlow()

    fun definirFiltro(valor: FiltroBiblioteca) {
        prefs.edit { putString(K_FILTRO, valor.name) }
        _filtro.value = valor
    }

    private val _ordenacao = MutableStateFlow(Ordenacao.deNome(prefs.getString(K_ORDENACAO, null)))
    val ordenacao: StateFlow<Ordenacao> = _ordenacao.asStateFlow()

    fun definirOrdenacao(valor: Ordenacao) {
        prefs.edit { putString(K_ORDENACAO, valor.name) }
        _ordenacao.value = valor
    }

    // --------------------------------------------------------------- video
    private val _proporcaoTela = MutableStateFlow(prefs.getInt(K_PROPORCAO, 0))
    val proporcaoTela: StateFlow<Int> = _proporcaoTela.asStateFlow()

    fun definirProporcaoTela(indice: Int) {
        prefs.edit { putInt(K_PROPORCAO, indice) }
        _proporcaoTela.value = indice
    }

    private val _filtroVideo = MutableStateFlow(prefs.getInt(K_FILTRO_VIDEO, 0))
    val filtroVideo: StateFlow<Int> = _filtroVideo.asStateFlow()

    fun definirFiltroVideo(indice: Int) {
        prefs.edit { putInt(K_FILTRO_VIDEO, indice) }
        _filtroVideo.value = indice
    }

    // --------------------------------------------------------- pastas SAF
    fun pastas(): List<Uri> =
        (prefs.getStringSet(K_PASTAS, emptySet()) ?: emptySet()).map(Uri::parse)

    fun adicionarPasta(uri: Uri) {
        val atuais = (prefs.getStringSet(K_PASTAS, emptySet()) ?: emptySet()).toMutableSet()
        atuais += uri.toString()
        prefs.edit { putStringSet(K_PASTAS, atuais) }
    }

    fun removerPasta(uri: Uri) {
        val atuais = (prefs.getStringSet(K_PASTAS, emptySet()) ?: emptySet()).toMutableSet()
        atuais -= uri.toString()
        prefs.edit { putStringSet(K_PASTAS, atuais) }
    }

    // ------------------------------------------- cache antigo da biblioteca
    /** Lido uma unica vez pelo BibliotecaStore para migrar o JSON para arquivo. */
    fun cacheBibliotecaLegado(): String? = prefs.getString(K_CACHE_LEGADO, null)

    fun limparCacheBibliotecaLegado() {
        prefs.edit { remove(K_CACHE_LEGADO) }
    }

    companion object {
        private const val ARQUIVO = "EmulatorSettings"

        // Chaves preservadas da versao anterior
        private const val K_BGM_ON = "bgm_enabled"
        private const val K_SFX_ON = "sfx_enabled"
        private const val K_BGM_VOL = "bgm_volume"
        private const val K_SFX_VOL = "sfx_volume"
        private const val K_WALLPAPER = "wallpaper_uri"
        private const val K_PASTAS = "pastas_roms_uris"
        private const val K_CACHE_LEGADO = "biblioteca_cache"

        // Chaves novas da Fase 0
        private const val K_TEMA = "tema_app"
        private const val K_MODO_VISUAL = "modo_visual"
        private const val K_FILTRO = "filtro_biblioteca"
        private const val K_ORDENACAO = "ordenacao_biblioteca"
        private const val K_REDUZIR_EFEITOS = "reduzir_efeitos"
        private const val K_PROPORCAO = "proporcao_tela_idx"
        private const val K_FILTRO_VIDEO = "filtro_video_idx"

        @Volatile
        private var instancia: Preferencias? = null

        fun obter(context: Context): Preferencias =
            instancia ?: synchronized(this) {
                instancia ?: Preferencias(context).also { instancia = it }
            }
    }
}
