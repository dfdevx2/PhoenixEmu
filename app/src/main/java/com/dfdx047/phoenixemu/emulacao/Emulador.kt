package com.dfdx047.phoenixemu.emulacao

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.Sistema
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.Idioma
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.emulator.EmulatorActivity
import com.dfdx047.phoenixemu.emulator.AjustesDeJogo
import com.dfdx047.phoenixemu.emulator.EscalaImagem
import com.dfdx047.phoenixemu.emulator.FiltroImagem
import com.dfdx047.phoenixemu.emulator.ProporcaoImagem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * O unico ponto do app que abre o emulador.
 *
 * O modulo `:emulator` roda em outro processo e nao enxerga nada do app --
 * nem as preferencias, nem o `BotaoVirtual`. Tudo o que ele precisa vai na
 * Intent, montado aqui. Se um dia faltar alguma informacao la dentro, e aqui
 * que ela entra.
 */
object Emulador {

    /**
     * Nomes dos nucleos dentro do APK.
     *
     * Precisam comecar com "lib" e terminar em ".so": o Android so extrai da
     * pasta lib/ do APK os arquivos com esse formato. Um nucleo baixado como
     * `mesen_libretro_android.so` tem de ser RENOMEADO antes de ir para
     * `emulator/src/main/jniLibs/arm64-v8a/`, senao ele e ignorado sem aviso.
     */
    const val NUCLEO_FALSO = "libnucleo_falso.so"
    const val NUCLEO_NES = "libmesen.so"
    const val NUCLEO_SNES = "libsnes9x.so"

    fun nucleoPara(sistema: Sistema): String = when (sistema) {
        Sistema.NES -> NUCLEO_NES
        Sistema.SNES -> NUCLEO_SNES
    }

    /** Ferramenta da Fase 4: roda o nucleo falso, sem ROM. */
    fun abrirNucleoFalso(context: Context, prefs: Preferencias) =
        abrir(context, prefs, NUCLEO_FALSO, rom = null, nomeSave = "nucleo_falso")

    fun abrirJogo(context: Context, prefs: Preferencias, jogo: Jogo) =
        abrir(
            context, prefs,
            nucleo = nucleoPara(jogo.sistema),
            rom = jogo.uriString,
            nomeSave = nomeDeSave(jogo),
            titulo = jogo.nome,
            capa = jogo.capaLocal,
            tempoJogadoMs = jogo.tempoJogadoMinutos.toLong() * 60 * 1000,
            plataforma = when (jogo.sistema) {
                Sistema.NES -> "NES"
                Sistema.SNES -> "SNES"
            },
            raHash = jogo.hashRa?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        com.dfdx047.phoenixemu.data.RomHasher.calcular(context, jogo.uri, jogo.extensao, jogo.sistema)?.hashRa
                    }
                }.getOrNull()
                ?: "",
            raConsole = when (jogo.sistema) {
                Sistema.NES -> 7
                Sistema.SNES -> 3
            }
        )

    private fun abrir(
        context: Context,
        prefs: Preferencias,
        nucleo: String,
        rom: String?,
        nomeSave: String,
        titulo: String = "",
        capa: String? = null,
        tempoJogadoMs: Long = 0,
        plataforma: String = "",
        raHash: String = "",
        raConsole: Int = 0,
    ) {
        val idDoJogo = rom?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) } ?: ""

        val ajustes = runBlocking {
            val (escalaStr, eO) = prefs.getAjusteString("aj_global_${AjustesDeJogo.CHAVE_ESCALA}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_ESCALA}" }, EscalaImagem.AJUSTAR.name)
            val (proporcaoStr, pO) = prefs.getAjusteString("aj_global_${AjustesDeJogo.CHAVE_PROPORCAO}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_PROPORCAO}" }, ProporcaoImagem.AUTOMATICA.name)
            val (filtroStr, filO) = prefs.getAjusteString("aj_global_${AjustesDeJogo.CHAVE_FILTRO}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_FILTRO}" }, FiltroImagem.NITIDO.name)
            val (mostrarFps, fO) = prefs.getAjusteBoolean("aj_global_${AjustesDeJogo.CHAVE_MOSTRAR_FPS}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_MOSTRAR_FPS}" }, false)
            val (volume, vO) = prefs.getAjusteFloat("aj_global_${AjustesDeJogo.CHAVE_VOLUME}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_VOLUME}" }, 1.0f)
            val (mudo, mO) = prefs.getAjusteBoolean("aj_global_${AjustesDeJogo.CHAVE_MUDO}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_MUDO}" }, false)
            val (velocidadeFF, ffO) = prefs.getAjusteInt("aj_global_${AjustesDeJogo.CHAVE_VELOCIDADE_FF}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_VELOCIDADE_FF}" }, 2)

            val overrides = mutableSetOf<String>()
            if (eO) overrides.add(AjustesDeJogo.CHAVE_ESCALA)
            if (pO) overrides.add(AjustesDeJogo.CHAVE_PROPORCAO)
            if (filO) overrides.add(AjustesDeJogo.CHAVE_FILTRO)
            if (fO) overrides.add(AjustesDeJogo.CHAVE_MOSTRAR_FPS)
            if (vO) overrides.add(AjustesDeJogo.CHAVE_VOLUME)
            if (mO) overrides.add(AjustesDeJogo.CHAVE_MUDO)
            if (ffO) overrides.add(AjustesDeJogo.CHAVE_VELOCIDADE_FF)

            val (overlayJsonStr, overlayOverride) = prefs.getAjusteString("dummy", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_overlay_controle" }, "")
            if (overlayOverride && overlayJsonStr.isNotBlank()) overrides.add("overlay_controle")

            AjustesDeJogo(
                escala = runCatching { EscalaImagem.valueOf(escalaStr) }.getOrDefault(EscalaImagem.AJUSTAR),
                proporcao = runCatching { ProporcaoImagem.valueOf(proporcaoStr) }.getOrDefault(ProporcaoImagem.AUTOMATICA),
                filtro = runCatching { FiltroImagem.valueOf(filtroStr) }.getOrDefault(FiltroImagem.NITIDO),
                mostrarFps = mostrarFps,
                volume = volume,
                mudo = mudo,
                velocidadeFF = velocidadeFF,
                overrides = overrides,
                idDoJogo = idDoJogo
            )
        }
        
        val overlayJsonStr = runBlocking { 
            val (str, override) = prefs.getAjusteString("dummy", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_overlay_controle" }, "")
            val overlayConfigEfetivo = if (override && str.isNotBlank()) {
                Gson().fromJson(str, com.dfdx047.phoenixemu.data.OverlayConfigNova::class.java)
            } else {
                prefs.overlay.first()
            }
            val overlayFinal = if (overlayConfigEfetivo.controles.isEmpty()) {
                overlayConfigEfetivo.copy(controles = if (plataforma == "SNES") com.dfdx047.phoenixemu.data.OverlayConfigNova.layoutSnes() else com.dfdx047.phoenixemu.data.OverlayConfigNova.layoutNes())
            } else {
                overlayConfigEfetivo
            }
            Gson().toJson(overlayFinal)
        }

        val darkTheme = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val schemeBase = com.dfdx047.phoenixemu.ui.theme.esquemaDeAmostraSemComposable(prefs.tema.value, context, darkTheme)
        
        val corPrimaria = schemeBase.primary.toArgb()
        val corSuperficie = schemeBase.surface.toArgb()
        val corTexto = schemeBase.onSurface.toArgb()

        val atalhos = prefs.atalhos.value
        val atalhosJson = JSONObject()
        AcaoAtalho.entries.forEach { acao ->
            val combo = atalhos.combo(acao)
            if (combo.isNotEmpty()) {
                val array = JSONArray()
                combo.forEach { array.put(it) }
                atalhosJson.put(acao.name, array)
            } else {
                atalhosJson.put(acao.name, JSONArray())
            }
        }

        val intent = Intent(context, EmulatorActivity::class.java)
            .putExtra(EmulatorActivity.EXTRA_NUCLEO, nucleo)
            .putExtra(EmulatorActivity.EXTRA_NOME_SAVE, nomeSave)
            .putExtra(EmulatorActivity.EXTRA_MAPEAMENTO, mapeamento(prefs))
            .putExtra(EmulatorActivity.EXTRA_OVERLAY, overlayJsonStr)
            .putExtra(EmulatorActivity.EXTRA_ATALHOS, atalhosJson.toString())
            .putExtra(EmulatorActivity.EXTRA_ATALHOS_CFG, Gson().toJson(prefs.atalhos.value))
            .putExtra(EmulatorActivity.EXTRA_AUTOSALVAR, prefs.autoSalvar.value)
            .putExtra(EmulatorActivity.EXTRA_AUTOCARREGAR, prefs.autoCarregar.value)
            .putExtra(EmulatorActivity.EXTRA_TEMA, prefs.tema.value.name)
            .putExtra(EmulatorActivity.EXTRA_ACABAMENTO, prefs.acabamento.value.name)
            .putExtra(EmulatorActivity.EXTRA_REDUZIR_EFEITOS, prefs.reduzirEfeitos.value)
            .putExtra(EmulatorActivity.EXTRA_AMOLED, prefs.amoled.value)
            .putExtra(EmulatorActivity.EXTRA_SOMBRAS, prefs.sombras.value)
            .putExtra(EmulatorActivity.EXTRA_IDIOMA, Idioma.atual(context))
            .putExtra(EmulatorActivity.EXTRA_TITULO, titulo)
            .putExtra(EmulatorActivity.EXTRA_PLATAFORMA, plataforma)
            .putExtra("phoenix.ajustes", Gson().toJson(ajustes))

        if (rom != null) intent.putExtra(EmulatorActivity.EXTRA_ROM, rom)
        if (!capa.isNullOrBlank()) intent.putExtra(EmulatorActivity.EXTRA_CAPA, capa)
        intent.putExtra(EmulatorActivity.EXTRA_TEMPO_JOGADO_MS, tempoJogadoMs)
        intent.putExtra("phoenix.cor_primaria", corPrimaria)
        intent.putExtra("phoenix.cor_superficie", corSuperficie)
        intent.putExtra("phoenix.cor_texto", corTexto)
        intent.putExtra("phoenix.menu_estilo", runBlocking { prefs.globalMenuEstilo.first() })
        intent.putExtra("phoenix.menu_desfoque", runBlocking { prefs.globalMenuDesfoque.first() })
        intent.putExtra("phoenix.menu_opacidade", runBlocking { prefs.globalMenuOpacidade.first() })
        intent.putExtra("phoenix.menu_lado", runBlocking { prefs.globalMenuLado.first() })
        intent.putExtra("phoenix.menu_tema", runBlocking { prefs.globalMenuTema.first() })
        intent.putExtra("phoenix.menu_alca", runBlocking { prefs.globalMenuAlca.first() })
        intent.putExtra("phoenix.menu_voltar", runBlocking { prefs.globalMenuVoltar.first() })
        intent.putExtra("phoenix.menu_gesto", runBlocking { prefs.globalMenuGesto.first() })
        intent.putExtra("phoenix.conq_avisos", runBlocking { prefs.conqAvisos.first() })
        intent.putExtra("phoenix.conq_som", runBlocking { prefs.conqSom.first() })
        intent.putExtra("phoenix.conq_vibrar", runBlocking { prefs.conqVibrar.first() })
        intent.putExtra("phoenix.conq_posicao", runBlocking { prefs.conqPosicao.first() })
        intent.putExtra("phoenix.conq_duracao", runBlocking { prefs.conqDuracao.first() })
        intent.putExtra("phoenix.conq_volume", runBlocking { prefs.conqVolume.first() })
        intent.putExtra("phoenix.ra_usuario", com.dfdx047.phoenixemu.ra.RaCredenciais.usuario(context) ?: "")
        intent.putExtra("phoenix.ra_token", com.dfdx047.phoenixemu.ra.RaCredenciais.token(context) ?: "")
        intent.putExtra("phoenix.ra_hash", raHash)
        intent.putExtra("phoenix.ra_console", raConsole)
        runCatching { com.dfdx047.phoenixemu.ra.RaRepositorio(context).limparCache() }
        context.startActivity(intent)
    }

    /**
     * O mapa de teclas, na ordem que a Activity espera.
     *
     * A ordem e procurada POR NOME, nao assumida: se alguem reordenar a enum
     * BotaoVirtual ou a lista la do modulo, `valueOf` falha na hora com o
     * nome do botao, em vez de o A virar o B em silencio.
     */
    private fun mapeamento(prefs: Preferencias): IntArray {
        val mapa = prefs.mapeamento.value
        return EmulatorActivity.ORDEM_DO_MAPEAMENTO
            .map { nome -> BotaoVirtual.valueOf(nome) }
            .map { botao -> mapa[botao] ?: botao.padrao }
            .toIntArray()
    }

    /**
     * Nome-base dos saves: o nome do arquivo sem extensao, mais o CRC32 quando
     * existir. So o nome colidiria com duas ROMs iguais em pastas diferentes.
     */
    private fun nomeDeSave(jogo: Jogo): String {
        val base = jogo.nomeArquivoOriginal.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return if (jogo.crc32.isNullOrBlank()) base else "$base.${jogo.crc32}"
    }
}
