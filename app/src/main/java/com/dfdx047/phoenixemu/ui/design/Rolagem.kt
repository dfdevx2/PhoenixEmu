package com.dfdx047.phoenixemu.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Faz o chrome flutuante sair do caminho quando a lista rola.
 *
 * O problema que isto resolve: com busca, seletor, filtros, FAB e navegacao
 * flutuando por cima, sobrava pouca tela para as capas -- e em paisagem, quase
 * nenhuma. Pior, as pilulas cobriam justamente a arte que a pessoa esta
 * tentando olhar.
 *
 * O comportamento e o de barra de navegador: rolar para baixo esconde,
 * rolar para cima traz de volta na hora, sem precisar voltar ao topo. O
 * gesto nunca e consumido (`Offset.Zero`), entao a rolagem da lista segue
 * exatamente como antes.
 */
@Stable
class EstadoDoChrome(private val alturaPx: Float) {

    /** 0 = totalmente visivel, 1 = totalmente escondido. */
    var fracaoOculta by mutableFloatStateOf(0f)
        private set

    val conexao: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (alturaPx <= 0f) return Offset.Zero
            val nova = fracaoOculta - available.y / alturaPx
            fracaoOculta = nova.coerceIn(0f, 1f)
            return Offset.Zero
        }
    }

    fun mostrar() { fracaoOculta = 0f }
}

@Composable
fun lembrarEstadoDoChrome(altura: Dp): EstadoDoChrome {
    val px = with(LocalDensity.current) { altura.toPx() }
    return remember(px) { EstadoDoChrome(px) }
}

/**
 * Suaviza a fracao para a animacao. Sem isto o chrome acompanha o dedo
 * quadro a quadro, o que fica nervoso em rolagens rapidas.
 */
@Composable
fun EstadoDoChrome.fracaoSuavizada(): Float {
    val valor by animateFloatAsState(fracaoOculta, label = "chromeOculto")
    return valor
}
