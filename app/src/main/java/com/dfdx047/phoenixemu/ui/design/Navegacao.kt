package com.dfdx047.phoenixemu.ui.design

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Navegacao por controle na biblioteca.
 *
 * ## Por que nao usar o sistema de foco do Compose aqui
 *
 * As duas tentativas anteriores foram por ali -- `focusable`, `focusGroup`,
 * `FocusRequester` -- e nenhuma funcionou de forma confiavel. O motivo e
 * estrutural, nao um detalhe que faltou:
 *
 * A tela tem duas camadas sobrepostas. O conteudo fica embaixo e o chrome de
 * vidro por cima, ocupando espacialmente a mesma regiao. A busca de foco em
 * duas dimensoes do Compose decide para onde ir pelas caixas na tela, e com
 * pilulas flutuando exatamente sobre as capas ela nao tem como adivinhar a
 * intencao. Some a isso uma lista preguicosa, onde os alvos so existem
 * enquanto visiveis, e o resultado e o que voce viu: no carrossel funcionava
 * "quando queria" e na grade nunca.
 *
 * ## O modelo que substitui
 *
 * Selecao explicita, que e como todo frontend de console faz (RetroArch, Big
 * Picture, o proprio XMB). A lista inteira e UM alvo de foco; dentro dela,
 * quem manda e um indice que este arquivo movimenta. Vantagens diretas:
 *
 *  - deterministico: o D-pad move o indice, ponto;
 *  - **toque e controle compartilham o mesmo estado**, entao tocar numa capa
 *    passa a selecao para ela -- exatamente o que voce pediu;
 *  - funciona igual na grade e no carrossel;
 *  - a capa selecionada e a que tem o halo, sem depender de foco nenhum.
 */
object NavegacaoDeLista {

    /** O que a tecla pediu. `NADA` significa "deixa passar, nao e minha". */
    enum class Acao {
        NADA,
        MOVER,
        /** A lista reconhece a tecla mas nao tem para onde ir. Veja abaixo. */
        LIMITE,
        ABRIR,
        OPCOES,
        SAIR_PARA_CIMA,
        SAIR_PARA_BAIXO
    }

    data class Resultado(val acao: Acao, val novoIndice: Int = 0)

    /**
     * Traduz uma tecla em movimento do indice.
     *
     * So trata KeyDown: assim segurar o direcional repete, que e o
     * comportamento esperado numa lista longa.
     *
     * ## Por que existe LIMITE
     *
     * Devolver "nao tratei" numa borda da lista parece inofensivo, mas nao e:
     * o Compose entende isso como permissao para procurar outro alvo de foco e
     * move o foco para alguma pilula do chrome. Dali em diante o D-pad nao
     * mexe mais nas capas -- e esse era o "so funciona quando quer".
     *
     * Entao as bordas HORIZONTAIS sao absorvidas (LIMITE: consome a tecla e
     * nao faz nada), e a saida da lista acontece so nas bordas VERTICAIS, de
     * proposito e na direcao onde o chrome realmente esta: para cima a busca,
     * para baixo a navegacao.
     */
    fun interpretar(
        evento: KeyEvent,
        indice: Int,
        total: Int,
        colunas: Int,
        emGrade: Boolean
    ): Resultado {
        if (evento.type != KeyEventType.KeyDown || total <= 0) return Resultado(Acao.NADA)

        val porFileira = colunas.coerceAtLeast(1)
        val ultimaFileira = (total - 1) / porFileira

        return when (evento.key) {
            Key.DirectionLeft -> mover(indice, -1, total)
            Key.DirectionRight -> mover(indice, +1, total)

            // No carrossel nao existe "fileira de cima": subir e sair.
            Key.DirectionUp -> when {
                !emGrade -> Resultado(Acao.SAIR_PARA_CIMA)
                indice < porFileira -> Resultado(Acao.SAIR_PARA_CIMA)
                else -> mover(indice, -porFileira, total)
            }

            Key.DirectionDown -> when {
                !emGrade -> Resultado(Acao.SAIR_PARA_BAIXO)
                indice / porFileira >= ultimaFileira -> Resultado(Acao.SAIR_PARA_BAIXO)
                // Na penultima fileira o passo cheio pode passar do fim; o
                // clamp leva ao ultimo item, que e a fileira de baixo mesmo.
                else -> mover(indice, +porFileira, total)
            }

            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter ->
                Resultado(Acao.ABRIR, indice)

            Key.ButtonY, Key.ButtonStart, Key.Menu ->
                Resultado(Acao.OPCOES, indice)

            else -> Resultado(Acao.NADA)
        }
    }

    private fun mover(indice: Int, delta: Int, total: Int): Resultado {
        val novo = (indice + delta).coerceIn(0, total - 1)
        return if (novo == indice) Resultado(Acao.LIMITE) else Resultado(Acao.MOVER, novo)
    }

    val direcaoDeSaida: FocusDirection get() = FocusDirection.Up
    val direcaoDeSaidaAbaixo: FocusDirection get() = FocusDirection.Down
}
