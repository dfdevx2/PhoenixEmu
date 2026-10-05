package com.dfdx047.phoenixemu.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dfdx047.phoenixemu.Acabamento
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.dfdx047.phoenixemu.FiltroBiblioteca
import com.dfdx047.phoenixemu.ModoVisual
import com.dfdx047.phoenixemu.Ordenacao
import com.dfdx047.phoenixemu.TemaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

// ---------------------------------------------------------------- chaves

private val K_TEMA = stringPreferencesKey("tema_app")
private val K_AMOLED = booleanPreferencesKey("amoled")
private val K_ACABAMENTO = stringPreferencesKey("acabamento")
private val K_WALL_DESFOQUE = floatPreferencesKey("wallpaper_desfoque")
private val K_WALL_OPACIDADE = floatPreferencesKey("wallpaper_opacidade")
private val K_BGM_ON = booleanPreferencesKey("bgm_enabled")
private val K_SFX_ON = booleanPreferencesKey("sfx_enabled")
private val K_BGM_VOL = floatPreferencesKey("bgm_volume")
private val K_SFX_VOL = floatPreferencesKey("sfx_volume")
private val K_WALLPAPER = stringPreferencesKey("wallpaper_uri")
private val K_REDUZIR_EFEITOS = booleanPreferencesKey("reduzir_efeitos")
private val K_MODO_VISUAL = stringPreferencesKey("modo_visual")
private val K_FILTRO = stringPreferencesKey("filtro_biblioteca")
private val K_ORDENACAO = stringPreferencesKey("ordenacao_biblioteca")
private val K_PROPORCAO = intPreferencesKey("proporcao_tela_idx")
private val K_FILTRO_VIDEO = intPreferencesKey("filtro_video_idx")
private val K_SISTEMA_IDX = intPreferencesKey("sistema_biblioteca_idx")
private val K_PASTAS = stringSetPreferencesKey("pastas_roms_uris")
private val K_PRIMEIRA_EXECUCAO = booleanPreferencesKey("primeira_execucao_concluida")
private val K_MAPEAMENTO = stringPreferencesKey("mapeamento_controle")
private val K_OVERLAY = stringPreferencesKey("overlay_controle")
private val K_ATALHOS = stringPreferencesKey("atalhos_controle")
private val K_AUTO_SALVAR = booleanPreferencesKey("auto_salvar")
private val K_AUTO_CARREGAR = booleanPreferencesKey("auto_carregar")
private val K_OMBROS_TROCAM_SECAO = booleanPreferencesKey("ombros_trocam_secao")
private val K_TOPO_FIXO_GRADE = booleanPreferencesKey("topo_fixo_grade")
private val K_SOMBRAS = booleanPreferencesKey("sombras")
private val K_SOM_CAPA_PASSA = booleanPreferencesKey("sfx_capa_passa")
private val K_SOM_TROCA_ABA = booleanPreferencesKey("sfx_troca_aba")
private val K_SOM_BOOT = booleanPreferencesKey("sfx_boot")
private val K_SOM_ENTRAR_JOGO = booleanPreferencesKey("sfx_game_boot")

/**
 * Chaves trazidas do SharedPreferences antigo, uma a uma.
 *
 * A lista e explicita de proposito. Sem ela, a migracao arrastaria tambem o
 * `biblioteca_cache` -- um JSON que pode ter megabytes -- para dentro do
 * DataStore, que carrega tudo na memoria. Esse cache tem outro destino: ele
 * vira linhas no Room, e quem cuida disso e o BibliotecaStore.
 */
private val CHAVES_LEGADAS = setOf(
    "tema_app", "bgm_enabled", "sfx_enabled", "bgm_volume", "sfx_volume",
    "wallpaper_uri", "reduzir_efeitos", "modo_visual", "filtro_biblioteca",
    "ordenacao_biblioteca", "proporcao_tela_idx", "filtro_video_idx",
    "pastas_roms_uris", "amoled", "acabamento",
    "wallpaper_desfoque", "wallpaper_opacidade",
    "primeira_execucao_concluida", "mapeamento_controle", "overlay_controle"
)

private const val PREFS_LEGADAS = "EmulatorSettings"

private val Context.armazem: DataStore<Preferences> by preferencesDataStore(
    name = "ajustes",
    produceMigrations = { contexto ->
        listOf(
            SharedPreferencesMigration(
                context = contexto,
                sharedPreferencesName = PREFS_LEGADAS,
                keysToMigrate = CHAVES_LEGADAS
            )
        )
    }
)

/**
 * Preferencias do app, agora em DataStore.
 *
 * Por que trocar algo que funcionava: `SharedPreferences.apply()` parece
 * assincrono mas ainda enfileira um fsync que o sistema cobra no
 * `onPause`/`onStop` da Activity -- e a primeira leitura de `getX()` bloqueia
 * ate o XML inteiro ser carregado. Nenhum dos dois aparece em teste e os dois
 * aparecem em ANR de usuario. DataStore e transacional e sempre fora da main
 * thread.
 *
 * A API publica desta classe nao mudou: continua tudo StateFlow, e nem a UI
 * nem o AudioEngine precisaram de ajuste. A unica excecao e `pastas()`, que
 * virou suspensa -- ler do DataStore e assincrono por definicao.
 */
class Preferencias private constructor(context: Context) {

    private val app = context.applicationContext
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val fluxo: Flow<Preferences> = app.armazem.data
        // Arquivo corrompido ou disco cheio nao pode derrubar o app: cai nos
        // valores padrao e o usuario reconfigura.
        .catch { erro -> if (erro is IOException) emit(emptyPreferences()) else throw erro }

    /**
     * A leitura do DataStore e assincrona, entao por alguns quadros o tema
     * seria o padrao antes do salvo chegar. A MainActivity segura a splash
     * ate isto virar true, e o usuario nunca ve a troca.
     */
    private val _carregado = MutableStateFlow(false)
    val carregado: StateFlow<Boolean> = _carregado.asStateFlow()

    init {
        escopo.launch {
            fluxo.first()
            _carregado.value = true
        }
    }

    private fun <T> derivar(padrao: T, leitura: (Preferences) -> T): StateFlow<T> =
        fluxo.map(leitura)
            .distinctUntilChanged()
            .stateIn(escopo, SharingStarted.Eagerly, padrao)

    private fun editar(bloco: (MutablePreferences) -> Unit) {
        escopo.launch { app.armazem.edit(bloco) }
    }

    // ---------------------------------------------------------------- tema
    val tema: StateFlow<TemaApp> =
        derivar(TemaApp.DINAMICO) { TemaApp.deNome(it[K_TEMA]) }

    fun definirTema(valor: TemaApp) = editar { it[K_TEMA] = valor.name }

    /**
     * Preto absoluto sobre qualquer tema escuro.
     *
     * O padrao e "ligado se o usuario tinha o tema AMOLED salvo": quem
     * escolheu preto absoluto antes continua com preto absoluto depois da
     * reformulacao, sem precisar reconfigurar nada.
     */
    val amoled: StateFlow<Boolean> = derivar(false) { prefs ->
        prefs[K_AMOLED] ?: (prefs[K_TEMA] == TemaApp.LEGADO_AMOLED)
    }

    fun definirAmoled(valor: Boolean) = editar { it[K_AMOLED] = valor }

    val acabamento: StateFlow<Acabamento> =
        derivar(Acabamento.VIDRO) { Acabamento.deNome(it[K_ACABAMENTO]) }

    fun definirAcabamento(valor: Acabamento) = editar { it[K_ACABAMENTO] = valor.name }

    // --------------------------------------------------------------- audio
    val bgmAtivo: StateFlow<Boolean> = derivar(true) { it[K_BGM_ON] ?: true }
    val sfxAtivo: StateFlow<Boolean> = derivar(true) { it[K_SFX_ON] ?: true }
    // De fabrica a musica entra baixa e os efeitos no meio: abrir um app e
    // levar um susto de volume cheio nao e boa primeira impressao. Quem ja
    // tinha volume salvo mantem o que escolheu.
    val bgmVolume: StateFlow<Float> = derivar(BGM_PADRAO) { it[K_BGM_VOL] ?: BGM_PADRAO }
    val sfxVolume: StateFlow<Float> = derivar(SFX_PADRAO) { it[K_SFX_VOL] ?: SFX_PADRAO }

    fun definirBgmAtivo(valor: Boolean) = editar { it[K_BGM_ON] = valor }
    fun definirSfxAtivo(valor: Boolean) = editar { it[K_SFX_ON] = valor }
    fun definirBgmVolume(valor: Float) = editar { it[K_BGM_VOL] = valor.coerceIn(0f, 1f) }
    fun definirSfxVolume(valor: Float) = editar { it[K_SFX_VOL] = valor.coerceIn(0f, 1f) }

    // ----------------------------------------------------------- aparencia
    val wallpaperUri: StateFlow<String?> = derivar(null) { it[K_WALLPAPER] }

    fun definirWallpaper(uri: Uri?) = editar {
        if (uri == null) it.remove(K_WALLPAPER) else it[K_WALLPAPER] = uri.toString()
    }

    /** 0 = nitido, 1 = desfoque maximo (24dp). */
    val wallpaperDesfoque: StateFlow<Float> = derivar(0f) { it[K_WALL_DESFOQUE] ?: 0f }

    /** 1 = imagem cheia, 0 = so o fundo do tema. */
    val wallpaperOpacidade: StateFlow<Float> = derivar(1f) { it[K_WALL_OPACIDADE] ?: 1f }

    fun definirWallpaperDesfoque(valor: Float) =
        editar { it[K_WALL_DESFOQUE] = valor.coerceIn(0f, 1f) }

    fun definirWallpaperOpacidade(valor: Float) =
        editar { it[K_WALL_OPACIDADE] = valor.coerceIn(0f, 1f) }

    val reduzirEfeitos: StateFlow<Boolean> =
        derivar(false) { it[K_REDUZIR_EFEITOS] ?: false }

    fun definirReduzirEfeitos(valor: Boolean) = editar { it[K_REDUZIR_EFEITOS] = valor }

    // ------------------------------------------------------------ vitrine
    val modoVisual: StateFlow<ModoVisual> = derivar(ModoVisual.GRADE) { prefs ->
        runCatching { ModoVisual.valueOf(prefs[K_MODO_VISUAL] ?: "") }
            .getOrDefault(ModoVisual.GRADE)
    }

    fun definirModoVisual(valor: ModoVisual) = editar { it[K_MODO_VISUAL] = valor.name }

    val filtro: StateFlow<FiltroBiblioteca> =
        derivar(FiltroBiblioteca.TODOS) { FiltroBiblioteca.deNome(it[K_FILTRO]) }

    fun definirFiltro(valor: FiltroBiblioteca) = editar { it[K_FILTRO] = valor.name }

    val ordenacao: StateFlow<Ordenacao> =
        derivar(Ordenacao.NOME) { Ordenacao.deNome(it[K_ORDENACAO]) }

    fun definirOrdenacao(valor: Ordenacao) = editar { it[K_ORDENACAO] = valor.name }

    // --------------------------------------------------------------- video
    val proporcaoTela: StateFlow<Int> = derivar(0) { it[K_PROPORCAO] ?: 0 }
    val filtroVideo: StateFlow<Int> = derivar(0) { it[K_FILTRO_VIDEO] ?: 0 }

    fun definirProporcaoTela(indice: Int) = editar { it[K_PROPORCAO] = indice }
    fun definirFiltroVideo(indice: Int) = editar { it[K_FILTRO_VIDEO] = indice }

    val sistemaIdx: StateFlow<Int> = derivar(0) { it[K_SISTEMA_IDX] ?: 0 }

    fun definirSistemaIdx(indice: Int) = editar { it[K_SISTEMA_IDX] = indice }

    // ---------------------------------------------------------- pastas SAF
    val pastasFluxo: StateFlow<Set<String>> =
        derivar(emptySet()) { it[K_PASTAS] ?: emptySet() }

    /** Suspensa agora: ler do DataStore e assincrono. */
    suspend fun pastas(): List<Uri> =
        (fluxo.first()[K_PASTAS] ?: emptySet()).map(Uri::parse)

    fun adicionarPasta(uri: Uri) = editar {
        it[K_PASTAS] = (it[K_PASTAS] ?: emptySet()) + uri.toString()
    }

    fun removerPasta(uri: Uri) = editar {
        it[K_PASTAS] = (it[K_PASTAS] ?: emptySet()) - uri.toString()
    }

    // ------------------------------------------------- primeira execucao

    val primeiraExecucaoConcluida: StateFlow<Boolean> =
        derivar(false) { it[K_PRIMEIRA_EXECUCAO] ?: false }

    fun concluirPrimeiraExecucao() = editar { it[K_PRIMEIRA_EXECUCAO] = true }

    // ------------------------------------------------------------ controles
    //
    // Mapa e overlay sao guardados como JSON num campo de texto. Poderiam ser
    // uma chave por botao, mas ai cada leitura viraria doze leituras, e
    // restaurar o padrao viraria doze remocoes.

    val mapeamento: StateFlow<Map<BotaoVirtual, Int>> =
        derivar(BotaoVirtual.padrao()) { prefs ->
            lerMapeamento(prefs[K_MAPEAMENTO])
        }

    fun definirTecla(botao: BotaoVirtual, codigo: Int) = editar { prefs ->
        val atual = lerMapeamento(prefs[K_MAPEAMENTO]).toMutableMap()
        val outroBotao = atual.entries.firstOrNull { it.value == codigo && it.key != botao }?.key
        if (outroBotao != null) {
            val teclaAntiga = atual[botao] ?: botao.padrao
            atual[outroBotao] = teclaAntiga
        }
        atual[botao] = codigo
        prefs[K_MAPEAMENTO] = gson.toJson(atual.mapKeys { it.key.name })
    }

    fun restaurarMapeamento() = editar { it.remove(K_MAPEAMENTO) }

    private fun lerMapeamento(json: String?): Map<BotaoVirtual, Int> {
        val padrao = BotaoVirtual.padrao()
        if (json.isNullOrBlank()) return padrao
        return runCatching {
            val tipo = object : TypeToken<Map<String, Int>>() {}.type
            val cru: Map<String, Int> = gson.fromJson(json, tipo) ?: return padrao
            // Comeca do padrao e sobrescreve: botao novo que eu acrescente
            // depois nao fica sem tecla para quem ja tinha um mapa salvo.
            padrao + cru.mapNotNull { (nome, codigo) ->
                BotaoVirtual.entries.firstOrNull { it.name == nome }?.let { it to codigo }
            }.toMap()
        }.getOrDefault(padrao)
    }

    val overlay: StateFlow<OverlayConfigNova> = derivar(OverlayConfigNova()) { prefs ->
        val json = prefs[K_OVERLAY]
        if (json.isNullOrBlank()) OverlayConfigNova()
        else {
            val configNova = runCatching { gson.fromJson(json, OverlayConfigNova::class.java) }.getOrNull()
            if (configNova?.controles != null && configNova.controles.isNotEmpty()) {
                configNova
            } else {
                // Try old format
                val configAntiga = runCatching { gson.fromJson(json, ConfigDoOverlay::class.java) }.getOrNull()
                if (configAntiga != null) OverlayConfigNova.converterAntigo(configAntiga) else OverlayConfigNova()
            }
        }
    }

    val atalhos: StateFlow<ConfigDeAtalhos> = derivar(ConfigDeAtalhos.padrao()) { prefs ->
        val json = prefs[K_ATALHOS]
        if (json.isNullOrBlank()) ConfigDeAtalhos.padrao()
        else runCatching {
            val cru = gson.fromJson(json, ConfigDeAtalhos::class.java) ?: return@runCatching ConfigDeAtalhos.padrao()
            val padrao = ConfigDeAtalhos.padrao()
            
            val validas = cru.acoes.filterKeys { k -> AcaoAtalho.entries.any { it.name == k } }
            cru.copy(acoes = padrao.acoes + validas)
        }.getOrDefault(ConfigDeAtalhos.padrao())
    }

    fun definirOverlay(config: OverlayConfigNova) = editar {
        it[K_OVERLAY] = gson.toJson(config)
    }

    fun restaurarOverlay() = editar { it.remove(K_OVERLAY) }
    
    fun definirHotkey(codigo: Int) = editar { prefs ->
        val atual = atalhos.value
        val nova = atual.copy(hotkey = codigo)
        prefs[K_ATALHOS] = gson.toJson(nova)
    }

    fun definirAtalho(acao: AcaoAtalho, codigo: Int) = editar { prefs ->
        val atual = atalhos.value
        val acoes = atual.acoes.toMutableMap()
        val atalhoAntigo = acoes[acao.name] ?: AtalhoDaAcao()
        
        acoes[acao.name] = atalhoAntigo.copy(tecla = codigo)
        var nova = atual.copy(acoes = acoes)
        
        val comboNovo = nova.combo(acao)
        if (comboNovo.isNotEmpty()) {
            AcaoAtalho.entries.forEach { outra ->
                if (outra != acao && nova.combo(outra) == comboNovo) {
                    acoes[outra.name] = (acoes[outra.name] ?: AtalhoDaAcao()).copy(tecla = 0)
                }
            }
        }
        nova = atual.copy(acoes = acoes)
        prefs[K_ATALHOS] = gson.toJson(nova)
    }

    fun definirUsarHotkey(acao: AcaoAtalho, usar: Boolean) = editar { prefs ->
        val atual = atalhos.value
        val acoes = atual.acoes.toMutableMap()
        val atalhoAntigo = acoes[acao.name] ?: AtalhoDaAcao()
        
        acoes[acao.name] = atalhoAntigo.copy(usarHotkey = usar)
        var nova = atual.copy(acoes = acoes)
        
        val comboNovo = nova.combo(acao)
        if (comboNovo.isNotEmpty()) {
            AcaoAtalho.entries.forEach { outra ->
                if (outra != acao && nova.combo(outra) == comboNovo) {
                    acoes[outra.name] = (acoes[outra.name] ?: AtalhoDaAcao()).copy(tecla = 0)
                }
            }
        }
        nova = atual.copy(acoes = acoes)
        prefs[K_ATALHOS] = gson.toJson(nova)
    }

    fun restaurarAtalhos() = editar { it.remove(K_ATALHOS) }

    // ------------------------------------------------------------- autosave

    val autoSalvar: StateFlow<Boolean> = derivar(true) { it[K_AUTO_SALVAR] ?: true }
    val autoCarregar: StateFlow<Boolean> = derivar(true) { it[K_AUTO_CARREGAR] ?: true }

    fun definirAutoSalvar(valor: Boolean) = editar { it[K_AUTO_SALVAR] = valor }
    fun definirAutoCarregar(valor: Boolean) = editar { it[K_AUTO_CARREGAR] = valor }

    // ------------------------------------------------------------- navegacao

    val ombrosTrocamSecao: StateFlow<Boolean> = derivar(true) { it[K_OMBROS_TROCAM_SECAO] ?: true }

    fun definirOmbrosTrocamSecao(valor: Boolean) = editar { it[K_OMBROS_TROCAM_SECAO] = valor }

    // ----------------------------------------------------- ocultar nav ao descer

    private val K_OCULTAR_NAV_DESCER = booleanPreferencesKey("ocultar_nav_descer")

    val ocultarNavAoDescer: StateFlow<Boolean> = derivar(true) { it[K_OCULTAR_NAV_DESCER] ?: true }

    fun definirOcultarNavAoDescer(valor: Boolean) = editar { it[K_OCULTAR_NAV_DESCER] = valor }

    // ------------------------------------------------------- grade topo fixo

    val topoFixoNaGrade: StateFlow<Boolean> = derivar(true) { it[K_TOPO_FIXO_GRADE] ?: true }

    fun definirTopoFixoNaGrade(valor: Boolean) = editar { it[K_TOPO_FIXO_GRADE] = valor }

    // ------------------------------------------------------------- sombras

    val sombras: StateFlow<Boolean> = derivar(false) { it[K_SOMBRAS] ?: false }

    fun definirSombras(valor: Boolean) = editar { it[K_SOMBRAS] = valor }

    // --------------------------------------------------------- efeitos individuais

    val somCapaPassa: StateFlow<Boolean> = derivar(true) { it[K_SOM_CAPA_PASSA] ?: true }
    fun definirSomCapaPassa(valor: Boolean) = editar { it[K_SOM_CAPA_PASSA] = valor }

    val somTrocaAba: StateFlow<Boolean> = derivar(true) { it[K_SOM_TROCA_ABA] ?: true }
    fun definirSomTrocaAba(valor: Boolean) = editar { it[K_SOM_TROCA_ABA] = valor }

    val somBoot: StateFlow<Boolean> = derivar(true) { it[K_SOM_BOOT] ?: true }
    fun definirSomBoot(valor: Boolean) = editar { it[K_SOM_BOOT] = valor }

    val somEntrarJogo: StateFlow<Boolean> = derivar(true) { it[K_SOM_ENTRAR_JOGO] ?: true }
    fun definirSomEntrarJogo(valor: Boolean) = editar { it[K_SOM_ENTRAR_JOGO] = valor }

    // ------------------------------------------- cache antigo da biblioteca

    /**
     * Lido direto do SharedPreferences, nao do DataStore: esta chave foi
     * deixada fora da migracao de proposito (veja CHAVES_LEGADAS). Serve
     * apenas para quem vem da Fase 0 sem ter passado pela 1A.
     */
    fun cacheBibliotecaLegado(): String? =
        app.getSharedPreferences(PREFS_LEGADAS, Context.MODE_PRIVATE)
            .getString("biblioteca_cache", null)

    fun limparCacheBibliotecaLegado() {
        app.getSharedPreferences(PREFS_LEGADAS, Context.MODE_PRIVATE)
            .edit()
            .remove("biblioteca_cache")
            .apply()
    }

    // ----------------------------------------------------------- ajustes de jogo
    suspend fun getAjusteString(chaveGlobal: String, chaveOverride: String?, padrao: String): Pair<String, Boolean> {
        val prefs = fluxo.first()
        if (chaveOverride != null) {
            val vOverride = prefs[stringPreferencesKey(chaveOverride)]
            if (vOverride != null) return vOverride to true
        }
        val vGlobal = prefs[stringPreferencesKey(chaveGlobal)]
        if (vGlobal != null) return vGlobal to false
        return padrao to false
    }

    suspend fun getAjusteInt(chaveGlobal: String, chaveOverride: String?, padrao: Int): Pair<Int, Boolean> {
        val prefs = fluxo.first()
        if (chaveOverride != null) {
            val vOverride = prefs[intPreferencesKey(chaveOverride)]
            if (vOverride != null) return vOverride to true
        }
        val vGlobal = prefs[intPreferencesKey(chaveGlobal)]
        if (vGlobal != null) return vGlobal to false
        return padrao to false
    }

    suspend fun getAjusteFloat(chaveGlobal: String, chaveOverride: String?, padrao: Float): Pair<Float, Boolean> {
        val prefs = fluxo.first()
        if (chaveOverride != null) {
            val vOverride = prefs[floatPreferencesKey(chaveOverride)]
            if (vOverride != null) return vOverride to true
        }
        val vGlobal = prefs[floatPreferencesKey(chaveGlobal)]
        if (vGlobal != null) return vGlobal to false
        return padrao to false
    }

    suspend fun getAjusteBoolean(chaveGlobal: String, chaveOverride: String?, padrao: Boolean): Pair<Boolean, Boolean> {
        val prefs = fluxo.first()
        if (chaveOverride != null) {
            val vOverride = prefs[booleanPreferencesKey(chaveOverride)]
            if (vOverride != null) return vOverride to true
        }
        val vGlobal = prefs[booleanPreferencesKey(chaveGlobal)]
        if (vGlobal != null) return vGlobal to false
        return padrao to false
    }

    fun setAjusteString(chave: String, valor: String) = editar { it[stringPreferencesKey(chave)] = valor }
    fun setAjusteInt(chave: String, valor: Int) = editar { it[intPreferencesKey(chave)] = valor }
    fun setAjusteFloat(chave: String, valor: Float) = editar { it[floatPreferencesKey(chave)] = valor }
    fun setAjusteBoolean(chave: String, valor: Boolean) = editar { it[booleanPreferencesKey(chave)] = valor }

    fun limparOverrides(chaves: List<String>) = editar { prefs ->
        chaves.forEach { 
            prefs.remove(stringPreferencesKey(it))
            prefs.remove(intPreferencesKey(it))
            prefs.remove(floatPreferencesKey(it))
            prefs.remove(booleanPreferencesKey(it))
        }
    }

    suspend fun apagarTodosOsAjustesPorJogo() {
        val prefs = app.armazem.data.first()
        val chaves = prefs.asMap().keys.filter { it.name.startsWith("aj_jogo_") }
        val count = chaves.size
        if (count > 0) {
            editar { p ->
                for (k in chaves) {
                    @Suppress("UNCHECKED_CAST")
                    p.remove(k as Preferences.Key<Any>)
                }
            }
        }
        Log.d("PhoenixAjustes", "apagarTodosOsAjustesPorJogo: removidas $count chaves aj_jogo_*")
    }

    val globalEscalaImagem: StateFlow<String> = derivar("AJUSTAR") { it[stringPreferencesKey("aj_global_escala")] ?: "AJUSTAR" }
    val globalProporcaoImagem: StateFlow<String> = derivar("AUTOMATICA") { it[stringPreferencesKey("aj_global_proporcao")] ?: "AUTOMATICA" }
    val globalMostrarFps: StateFlow<Boolean> = derivar(false) { it[booleanPreferencesKey("aj_global_mostrarFps")] ?: false }
    val globalVolume: StateFlow<Float> = derivar(1.0f) { it[floatPreferencesKey("aj_global_volume")] ?: 1.0f }
    val globalMudo: StateFlow<Boolean> = derivar(false) { it[booleanPreferencesKey("aj_global_mudo")] ?: false }
    val globalVelocidadeFF: StateFlow<Int> = derivar(2) { it[intPreferencesKey("aj_global_velocidadeFF")] ?: 2 }

    val globalMenuEstilo: StateFlow<String> = derivar("VIDRO") { it[stringPreferencesKey("aj_global_menu_estilo")] ?: "VIDRO" }
    val globalMenuDesfoque: StateFlow<Float> = derivar(20f) { it[floatPreferencesKey("aj_global_menu_desfoque")] ?: 20f }
    val globalMenuOpacidade: StateFlow<Float> = derivar(1.0f) { it[floatPreferencesKey("aj_global_menu_opacidade")] ?: 1.0f }
    val globalMenuLado: StateFlow<String> = derivar("ESQUERDA") { it[stringPreferencesKey("aj_global_menu_lado")] ?: "ESQUERDA" }
    val globalMenuTema: StateFlow<Boolean> = derivar(true) { it[booleanPreferencesKey("aj_global_menu_tema")] ?: true }
    
    val globalMenuAlca: StateFlow<Boolean> = derivar(true) { it[booleanPreferencesKey("aj_global_menu_alca")] ?: true }
    val globalMenuVoltar: StateFlow<Boolean> = derivar(true) { it[booleanPreferencesKey("aj_global_menu_voltar")] ?: true }
    val globalMenuGesto: StateFlow<Boolean> = derivar(true) { it[booleanPreferencesKey("aj_global_menu_gesto")] ?: true }

    companion object {
        const val BGM_PADRAO = 0.25f
        const val SFX_PADRAO = 0.50f

        @Volatile
        private var instancia: Preferencias? = null

        fun obter(context: Context): Preferencias =
            instancia ?: synchronized(this) {
                instancia ?: Preferencias(context).also { instancia = it }
            }
    }
}
