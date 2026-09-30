package com.dfdx047.phoenixemu

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

// =====================================================================
// TEMAS DO APLICATIVO
// Cada tema carrega o proprio rotulo como recurso de string, entao nao
// existe mais texto de UI fixo no codigo (e a traducao sai de graca).
// =====================================================================
enum class TemaApp(@StringRes val rotulo: Int) {
    DINAMICO(R.string.tema_dinamico),
    NES_US(R.string.tema_nes_us),
    NES_JP(R.string.tema_nes_jp),
    SNES_US(R.string.tema_snes_us),
    SNES_JP(R.string.tema_snes_jp);

    companion object {
        /**
         * Tolerante a valores antigos e invalidos.
         *
         * CLARO, ESCURO e AMOLED existiam e sairam. Os dois primeiros porque o
         * Material You ja segue o sistema -- ter "tema claro" ao lado dele era
         * oferecer a mesma coisa duas vezes. O AMOLED saiu porque preto
         * absoluto nao e um tema, e um acabamento: faz sentido sobre qualquer
         * tema escuro, e nao so sobre um. Virou interruptor.
         *
         * Quem tinha um dos tres salvo cai em DINAMICO, e quem tinha AMOLED
         * ganha o interruptor ligado (veja Preferencias.amoled).
         */
        fun deNome(nome: String?): TemaApp =
            entries.firstOrNull { it.name == nome } ?: DINAMICO

        const val LEGADO_AMOLED = "AMOLED"
    }
}

/**
 * Acabamento das superficies.
 *
 * Vidro fosco e bonito e nem todo mundo gosta -- e em aparelho fraco ele
 * custa. O solido usa as mesmas cores e as mesmas formas, so troca o material.
 */
enum class Acabamento(@StringRes val rotulo: Int) {
    VIDRO(R.string.acabamento_vidro),
    SOLIDO(R.string.acabamento_solido);

    companion object {
        fun deNome(nome: String?): Acabamento = entries.firstOrNull { it.name == nome } ?: VIDRO
    }
}

// =====================================================================
// SISTEMAS SUPORTADOS
// Era String ("NES" / "SNES") espalhada pelo codigo. Como enum, erro de
// digitacao passa a ser erro de compilacao. O nome do enum coincide com
// o texto antigo, entao o cache JSON existente continua desserializando.
// =====================================================================
enum class Sistema(val rotuloCurto: String, @StringRes val rotulo: Int) {
    NES("NES", R.string.tab_nes),
    SNES("SNES", R.string.tab_snes);

    companion object {
        fun deNome(nome: String?): Sistema? = entries.firstOrNull { it.name == nome }
    }
}

// =====================================================================
// MODELO DE JOGO
//
// Totalmente imutavel de proposito:
//  - @Immutable deixa o Compose pular a recomposicao de cartoes que nao
//    mudaram, o que e o que sustenta scroll fluido com milhares de itens.
//  - Antes, `var isFavorito` era alterado no lugar. O Compose nao observa
//    campo de data class, entao o coracao mudava mas o filtro "Favoritos"
//    nao reagia e nada era salvo. Agora toda mudanca passa por copy() no
//    BibliotecaStore, que recompoe e persiste.
//
// `id` e a URI do documento: identidade de verdade. Antes a comparacao
// era pelo nome do arquivo, entao "Contra (USA).nes" em duas pastas
// diferentes era tratado como o mesmo jogo.
// =====================================================================
@Immutable
data class Jogo(
    val nome: String,
    val nomeArquivoOriginal: String,
    val extensao: String,
    val uriString: String,
    val sistema: Sistema,
    val regiao: String,
    val isFavorito: Boolean = false,
    val ultimaVezJogado: Long = 0L,
    val tempoJogadoMinutos: Int = 0,
    /** URL remota resolvida pelo scraper. Serve para re-baixar se o arquivo local sumir. */
    val capaUrl: String? = null,
    /** Caminho absoluto da capa ja baixada. Quando existe, a lista nao toca na rede. */
    val capaLocal: String? = null,
    /** false = ja tentamos buscar capa e nao achamos; evita bater no servidor de novo. */
    val capaPendente: Boolean = true,
    /** Assinatura do arquivo: e o que permite a varredura incremental. */
    val tamanhoBytes: Long = 0L,
    val modificadoEm: Long = 0L,
    /** MD5 no formato do RetroAchievements. Calculado sob demanda, nunca na varredura. */
    val hashRa: String? = null,
    /** CRC32 do arquivo inteiro, para casar com DATs No-Intro mais tarde. */
    val crc32: String? = null,
    /** O arquivo nao foi encontrado na ultima varredura (cartao removido, pasta movida). */
    val ausente: Boolean = false
) {
    val id: String get() = uriString

    val uri: Uri get() = Uri.parse(uriString)

    val temRegiaoConhecida: Boolean get() = regiao != REGIAO_DESCONHECIDA

    /** Assinatura usada para decidir se o arquivo mudou desde a ultima varredura. */
    val assinatura: Assinatura get() = Assinatura(tamanhoBytes, modificadoEm)

    companion object {
        const val REGIAO_DESCONHECIDA = "Desconhecida"
    }
}

/**
 * Tamanho + data de modificacao. Se os dois batem, o arquivo nao mudou e nao
 * precisa ser reidentificado nem re-hasheado. E o que torna a reabertura do
 * app barata mesmo com milhares de ROMs.
 */
data class Assinatura(val tamanho: Long, val modificado: Long)

// =====================================================================
// MODELOS AUXILIARES
// =====================================================================
data class RetroGameStat(
    val nome: String,
    val sistema: Sistema,
    val conquistasDesbloqueadas: Int,
    val totalConquistas: Int
)

data class RawgResponse(val results: List<RawgGame>?)

data class RawgGame(val name: String?, val background_image: String?)

// =====================================================================
// MODOS DE EXIBICAO DA BIBLIOTECA
// =====================================================================
enum class ModoVisual { GRADE, XMB }

enum class FiltroBiblioteca(@StringRes val rotulo: Int) {
    TODOS(R.string.filtro_todos),
    RECENTES(R.string.filtro_recentes),
    FAVORITOS(R.string.filtro_favoritos);

    companion object {
        fun deNome(nome: String?): FiltroBiblioteca = entries.firstOrNull { it.name == nome } ?: TODOS
    }
}

enum class Ordenacao(@StringRes val rotulo: Int) {
    NOME(R.string.ordenar_nome),
    MAIS_JOGADOS(R.string.ordenar_mais_jogados),
    JOGADOS_RECENTE(R.string.ordenar_recentes);

    companion object {
        fun deNome(nome: String?): Ordenacao = entries.firstOrNull { it.name == nome } ?: NOME
    }
}
