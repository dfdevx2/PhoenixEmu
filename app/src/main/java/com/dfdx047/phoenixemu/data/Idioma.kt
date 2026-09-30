package com.dfdx047.phoenixemu.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Idioma do app.
 *
 * ## Por que isto nao usa DataStore
 *
 * O idioma precisa ser conhecido em `attachBaseContext`, ANTES de qualquer
 * recurso ser carregado -- e naquele momento nao da para esperar uma leitura
 * assincrona. Por isso, e so por isso, esta preferencia mora num
 * SharedPreferences proprio, lido de forma sincrona. Todo o resto continua
 * no DataStore.
 *
 * ## Por que nao AppCompatDelegate
 *
 * `AppCompatDelegate.setApplicationLocales` e o caminho oficial, mas exige a
 * dependencia appcompat mais um `service` no manifesto, e foi desenhado em
 * volta de `AppCompatActivity` -- que nao e o caso aqui, onde a Activity e
 * uma `ComponentActivity` pura. Envolver o contexto na configuracao certa faz
 * a mesma coisa, funciona desde a API 26 e nao acrescenta dependencia
 * nenhuma.
 */
object Idioma {

    private const val ARQUIVO = "idioma"
    private const val CHAVE = "tag"

    /** Vazio significa "seguir o sistema". */
    const val SISTEMA = ""

    val disponiveis = listOf(SISTEMA, "pt", "en")

    fun atual(context: Context): String =
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .getString(CHAVE, SISTEMA) ?: SISTEMA

    fun definir(context: Context, tag: String) {
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(CHAVE, tag)
            .apply()
    }

    /** Envolve o contexto no idioma escolhido. Chamado de attachBaseContext. */
    fun aplicar(base: Context): Context {
        val tag = atual(base)
        if (tag.isBlank()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
