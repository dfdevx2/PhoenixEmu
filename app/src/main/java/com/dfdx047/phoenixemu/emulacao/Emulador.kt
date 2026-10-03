package com.dfdx047.phoenixemu.emulacao

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.Sistema
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.emulator.EmulatorActivity
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
    const val NUCLEO_SNES = "libbsnes.so"

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
            nomeSave = nomeDeSave(jogo)
        )

    private fun abrir(
        context: Context,
        prefs: Preferencias,
        nucleo: String,
        rom: String?,
        nomeSave: String
    ) {
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
            // Mesmo JSON que as Preferencias ja gravam: o modulo le com
            // org.json e nao precisa conhecer a classe ConfigDoOverlay.
            .putExtra(EmulatorActivity.EXTRA_OVERLAY, Gson().toJson(prefs.overlay.value))
            .putExtra(EmulatorActivity.EXTRA_ATALHOS, atalhosJson.toString())
            .putExtra(EmulatorActivity.EXTRA_AUTOSALVAR, prefs.autoSalvar.value)
            .putExtra(EmulatorActivity.EXTRA_AUTOCARREGAR, prefs.autoCarregar.value)
            .putExtra(EmulatorActivity.EXTRA_TEMA, prefs.tema.value.name)
            .putExtra(EmulatorActivity.EXTRA_ACABAMENTO, prefs.acabamento.value.name)
            .putExtra(EmulatorActivity.EXTRA_REDUZIR_EFEITOS, prefs.reduzirEfeitos.value)
            .putExtra(EmulatorActivity.EXTRA_AMOLED, prefs.amoled.value)
            .putExtra(EmulatorActivity.EXTRA_SOMBRAS, prefs.sombras.value)
        if (rom != null) intent.putExtra(EmulatorActivity.EXTRA_ROM, rom)
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
