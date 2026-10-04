package com.dfdx047.phoenixemu.emulator

import android.util.Log
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.R
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.AtalhoDaAcao
import com.dfdx047.phoenixemu.data.ConfigDeAtalhos
import com.dfdx047.phoenixemu.data.nomeDaTecla
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Estrutura com todas as cores derivadas para o menu de pausa.
 *
 * Quando "Seguir o tema" está desligado, esta função retorna a paleta
 * antiga (cores fixas) para manter o comportamento inalterado.
 */
internal data class PaletaMenu(
    val texto: Color,
    val textoSecundario: Color,
    val destaque: Color,
    val contorno: Color,
    val fundoBotao: Color,
    val fundoPainel: Color,
)

/**
 * Deriva a paleta final do menu a partir das cores brutas recebidas via Intent.
 *
 * Regras:
 *  - base do painel:
 *      VIDRO  → lerp(Black, primária, 0.12)  [escuro] / lerp(White, primária, 0.12) [claro]
 *      SÓLIDO → lerp(superfície, primária, 0.14)
 *      AMOLED + escuro → base = Black
 *  - fundoPainel = base com alpha = opacidade (clamp 0.30..1.00).
 *  - texto: se contraste texto/base < 4.5:1, usa branco (painel escuro) ou
 *    preto (painel claro).
 *  - destaque: se contraste destaque/base < 3:1, ajusta (clareia com branco
 *    em painel escuro, escurece com preto em painel claro) em passos de 10%.
 *    Se alpha=0 ou muito próximo da base, usa o destaque fixo antigo (#AA00FF).
 *  - contorno: texto com alpha 0.25; fundo botao: texto com alpha 0.08.
 */
private fun derivarPaletaFinal(
    temaCorPrimaria: Int,
    temaCorSuperficie: Int,
    temaCorTexto: Int,
    acabamento: String, // "VIDRO" ou "SOLIDO"
    menuOpacidade: Float,
    amoled: Boolean,
    ehPainelEscuro: Boolean,
): PaletaMenu {
    val corPrimaria = Color(temaCorPrimaria)
    val corSuperficieRaw = Color(temaCorSuperficie)
    val corTextoRaw = Color(temaCorTexto)

    // --- base do painel (opaca, alpha=1) ---
    val base: Color
    if (amoled && ehPainelEscuro) {
        base = Color.Black
    } else {
        base = when (acabamento.uppercase()) {
            "SOLIDO" -> corSuperficieRaw.copy(alpha = 1f).mixWith(corPrimaria.copy(alpha = 1f), 0.14f)
            else /* VIDRO */ -> {
                if (ehPainelEscuro) {
                    Color.Black.copy(alpha = 1f).mixWith(corPrimaria.copy(alpha = 1f), 0.12f)
                } else {
                    Color.White.copy(alpha = 1f).mixWith(corPrimaria.copy(alpha = 1f), 0.12f)
                }
            }
        }
    }

    // --- fundo do painel (com opacidade) ---
    val fundoPainel = base.copy(alpha = menuOpacidade.coerceIn(0.30f, 1.0f))

    // --- Texto (contraste ≥ 4.5:1 contra base opaca) ---
    val contrasteTexto = contrasteEntre(corTextoRaw, fundoPainel)
    val texto = if (contrasteTexto >= 4.5f) {
        corTextoRaw
    } else {
        if (ehPainelEscuro) Color.White else Color.Black
    }

    // --- Destaque (contraste ≥ 3:1 contra base opaca) ---
    val destaque: Color
    if (corPrimaria.alpha == 0f || contrasteEntre(corPrimaria, fundoPainel) < 1.05f) {
        destaque = Color(0xFFAA00FF)
    } else {
        var d = corPrimaria
        val maxIteracoes = 20
        for (i in 1..maxIteracoes) {
            val c = contrasteEntre(d, fundoPainel)
            if (c >= 3.0f) break
            d = if (ehPainelEscuro) {
                d.mixWith(Color.White, 0.1f)
            } else {
                d.mixWith(Color.Black, 0.1f)
            }
        }
        if (contrasteEntre(d, fundoPainel) < 3.0f) {
            destaque = Color(0xFFAA00FF)
        } else {
            destaque = d
        }
    }

    // --- Contorno e fundo dos botões ---
    val contorno = texto.copy(alpha = 0.25f)
    val fundoBotao = texto.copy(alpha = 0.08f)
    val textoSecundario = texto.copy(alpha = 0.7f)

    return PaletaMenu(
        texto = texto,
        textoSecundario = textoSecundario,
        destaque = destaque,
        contorno = contorno,
        fundoBotao = fundoBotao,
        fundoPainel = fundoPainel,
    )
}

/**
 * Mistura esta cor com [outro] na proporção [fraction].
 * fraction=0.1 → 10% de outro, 90% desta.
 */
private fun Color.mixWith(outro: Color, fraction: Float): Color {
    val r = this.red * (1f - fraction) + outro.red * fraction
    val g = this.green * (1f - fraction) + outro.green * fraction
    val b = this.blue * (1f - fraction) + outro.blue * fraction
    val a = this.alpha * (1f - fraction) + outro.alpha * fraction
    return Color(r, g, b, a)
}

/**
 * Calcula o ratio de contraste relativo entre duas cores (WCAG 2.0).
 * Retorna um valor >= 0.
 */
private fun contrasteEntre(cor1: Color, cor2: Color): Float {
    val l1 = luminanciaRelativa(cor1)
    val l2 = luminanciaRelativa(cor2)
    val maisClara = max(l1, l2)
    val maisEscura = min(l1, l2)
    return (maisClara + 0.05f) / (maisEscura + 0.05f)
}

private fun luminanciaRelativa(c: Color): Float {
    val r = lineariza(c.red)
    val g = lineariza(c.green)
    val b = lineariza(c.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun lineariza(canal: Float): Float = when {
    canal <= 0.04045f -> canal / 12.92f
    else -> ((canal + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
}


/**
 * Menu de pausa completo: fundo desfocado + painel lateral esquerdo com
 * cabecalho, abas e conteudo da aba selecionada.
 *
 * Controle (teclado / gamepad):
 *   L1 / R1      – troca a aba (quando menu visivel)
 */

internal data class CursorPos(
    val linha: Int = 1,
    val coluna: Int = 0,
)

@Composable
internal fun MenuDePausa(
    visivel: Boolean,
    fundo: Bitmap?,
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
    slots: List<SlotData>,
    slotSelecionado: Int,
    abaAtual: AbaDoMenu,
    aoFechar: () -> Unit,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    aoMudarSlot: (Int) -> Unit,
    aoMudarAba: (AbaDoMenu) -> Unit,
    ajustes: AjustesDeJogo,
    aoMudarAjuste: (String, Any, String) -> Unit,
    aoLimparAjustesJogo: () -> Unit,
    capaLocalPath: String?,
    tempoJogadoMs: Long,
    plataforma: String,
    menuDesfoque: Float,
    menuOpacidade: Float,
    menuLado: String,
    menuTema: Boolean,
    temaCorPrimaria: Int,
    temaCorSuperficie: Int,
    temaCorTexto: Int,
    amoled: Boolean,
    acabamento: String,
    reduzirEfeitos: Boolean,
    mapeamento: IntArray?,
    atalhosJson: String,
    atalhosCfg: ConfigDeAtalhos,
    capturando: String?,
    aoCapturar: (String) -> Unit,
    aoCancelarCaptura: () -> Unit,
    aoLimparAtalho: (AcaoAtalho) -> Unit,
    aoLimparHotkey: () -> Unit,
    aoAlternarHotkey: (AcaoAtalho, Boolean) -> Unit,
    aoRestaurarAtalhos: () -> Unit,
    aoRestaurarBotoes: () -> Unit,
) {
    AnimatedVisibility(
        visible = visivel,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 4 },
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        ),
    ) {
        MenuDePausaInner(
            fundo = fundo,
            nomeDoJogo = nomeDoJogo,
            tempoJogadoMinutos = tempoJogadoMinutos,
            slots = slots,
            slotSelecionadoInicial = slotSelecionado,
            abaAtual = abaAtual,
            aoFechar = aoFechar,
            aoSalvarEstado = aoSalvarEstado,
            aoCarregarEstado = aoCarregarEstado,
            aoContinuar = aoContinuar,
            aoReiniciar = aoReiniciar,
            aoSair = aoSair,
            aoMudarSlot = aoMudarSlot,
            aoMudarAba = aoMudarAba,
            ajustes = ajustes,
            aoMudarAjuste = aoMudarAjuste,
            aoLimparAjustesJogo = aoLimparAjustesJogo,
            capaLocalPath = capaLocalPath,
            tempoJogadoMs = tempoJogadoMs,
            plataforma = plataforma,
            menuDesfoque = menuDesfoque,
            menuOpacidade = menuOpacidade,
            menuLado = menuLado,
            menuTema = menuTema,
            temaCorPrimaria = temaCorPrimaria,
            temaCorSuperficie = temaCorSuperficie,
            temaCorTexto = temaCorTexto,
            amoled = amoled,
            acabamento = acabamento,
            reduzirEfeitos = reduzirEfeitos,
            mapeamento = mapeamento,
            atalhosJson = atalhosJson,
            atalhosCfg = atalhosCfg,
            capturando = capturando,
            aoCapturar = aoCapturar,
            aoCancelarCaptura = aoCancelarCaptura,
            aoLimparAtalho = aoLimparAtalho,
            aoLimparHotkey = aoLimparHotkey,
            aoAlternarHotkey = aoAlternarHotkey,
            aoRestaurarAtalhos = aoRestaurarAtalhos,
            aoRestaurarBotoes = aoRestaurarBotoes,
        )
    }
}

@Composable
private fun MenuDePausaInner(
    fundo: Bitmap?,
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
    slots: List<SlotData>,
    slotSelecionadoInicial: Int,
    abaAtual: AbaDoMenu,
    aoFechar: () -> Unit,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    aoMudarSlot: (Int) -> Unit,
    aoMudarAba: (AbaDoMenu) -> Unit,
    ajustes: AjustesDeJogo,
    aoMudarAjuste: (String, Any, String) -> Unit,
    aoLimparAjustesJogo: () -> Unit,
    capaLocalPath: String?,
    tempoJogadoMs: Long,
    plataforma: String,
    menuDesfoque: Float,
    menuOpacidade: Float,
    menuLado: String,
    menuTema: Boolean,
    temaCorPrimaria: Int,
    temaCorSuperficie: Int,
    temaCorTexto: Int,
    amoled: Boolean,
    acabamento: String,
    reduzirEfeitos: Boolean,
    mapeamento: IntArray?,
    atalhosJson: String,
    atalhosCfg: ConfigDeAtalhos,
    capturando: String?,
    aoCapturar: (String) -> Unit,
    aoCancelarCaptura: () -> Unit,
    aoLimparAtalho: (AcaoAtalho) -> Unit,
    aoLimparHotkey: () -> Unit,
    aoAlternarHotkey: (AcaoAtalho, Boolean) -> Unit,
    aoRestaurarAtalhos: () -> Unit,
    aoRestaurarBotoes: () -> Unit,
) {
    val corPrimaria = Color(temaCorPrimaria)
    val corSuperficie = Color(temaCorSuperficie)
    val corTexto = Color(temaCorTexto)
    val superficieLuminancia = corSuperficie.luminance()
    val ehPainelEscuro = superficieLuminancia < 0.5f

    Log.d("PhoenixMenu", "abriu acabamento=$acabamento opacidade=$menuOpacidade desfoque=$menuDesfoque reduzirEfeitos=$reduzirEfeitos amoled=$amoled escuro=$ehPainelEscuro superficie=0x${corSuperficie.value.toString(16).padStart(8, '0').uppercase()} destaque=0x${corPrimaria.value.toString(16).padStart(8, '0').uppercase()}")

    val paleta = if (menuTema) {
        derivarPaletaFinal(temaCorPrimaria, temaCorSuperficie, temaCorTexto, acabamento, menuOpacidade, amoled, ehPainelEscuro)
    } else {
        // Paleta antiga (cores fixas) quando "Seguir o tema" está desligado
        PaletaMenu(
            texto = Color.White,
            textoSecundario = Color.White.copy(alpha = 0.7f),
            destaque = Color(0xFFAA00FF),
            contorno = Color.White.copy(alpha = 0.25f),
            fundoBotao = Color.White.copy(alpha = 0.08f),
            fundoPainel = Color.Black.copy(alpha = menuOpacidade),
        )
    }

    // Log do design para debug
    val modo = if (acabamento.uppercase() == "SOLIDO") "SOLIDO" else "VIDRO"
    val baseHex = String.format("#%08X", (0L or paleta.fundoPainel.value.toLong()).and(0xFFFFFFFFL))
    val destaqueHex = String.format("#%08X", paleta.destaque.value.toLong().and(0xFFFFFFFFL))
    val alpha = menuOpacidade.coerceIn(0.30f, 1.0f)
    Log.d("PhoenixAjustes", "design=$modo base=$baseHex destaque=$destaqueHex alpha=$alpha")

    val config = LocalConfiguration.current
    val telaLarguraDp = config.screenWidthDp.dp
    val painelLarguraDp = (telaLarguraDp * 0.42f).coerceIn(340.dp, 420.dp)

    // Estado local único e mutável para o slot selecionado
    var slotSelecionado by remember { mutableIntStateOf(slotSelecionadoInicial.coerceIn(1, 4)) }

    val isVidro = acabamento == "VIDRO"
    val isEsquerda = menuLado == "ESQUERDA"
    val corPainel = paleta.fundoPainel
    val alignPainel = if (isEsquerda) Alignment.CenterStart else Alignment.CenterEnd
    val shapePainel = if (isEsquerda) RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp) else RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo — snapshot congelado
        if (fundo != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = fundo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && menuDesfoque > 0f && !reduzirEfeitos) {
                                it.blur(menuDesfoque.dp)
                            } else it
                        },
                    contentScale = ContentScale.Crop,
                )
                // Overlay escuro — 25% SÓLIDO, 35% VIDRO
                val overlayAlpha = if (acabamento.uppercase() == "SOLIDO") 0.25f else 0.35f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                ) {
                    Box(modifier = Modifier.fillMaxSize().clickable(onClick = aoFechar)) {}
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                Box(modifier = Modifier.fillMaxSize().clickable(onClick = aoFechar)) {}
            }
        }

        // Painel lateral
        Surface(
            modifier = if (amoled && ehPainelEscuro && acabamento == "SOLIDO") {
                Modifier
                    .width(painelLarguraDp)
                    .fillMaxHeight()
                    .align(alignPainel)
                    .border(1.dp, paleta.destaque.copy(alpha = 0.5f), shape = shapePainel)
            } else {
                Modifier
                    .width(painelLarguraDp)
                    .fillMaxHeight()
                    .align(alignPainel)
            },
            shape = shapePainel,
            color = corPainel,
        ) {
            // Fundo do painel derivado da paleta final
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
                primary = paleta.destaque,
                surface = paleta.fundoPainel,
                onSurface = paleta.texto,
            )) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // — Cabeçalho fixo —
                    CabecalhoPausa(
                        nomeDoJogo = nomeDoJogo,
                        tempoJogadoMinutos = tempoJogadoMinutos,
                        aoFechar = aoFechar,
                        capaLocalPath = capaLocalPath,
                        tempoJogadoMs = tempoJogadoMs,
                        plataforma = plataforma,
                        paleta = paleta,
                    )

                    // — Barra de abas fixa —
                    BarraDeAbas(
                        abaAtual = abaAtual,
                        aoMudarAba = aoMudarAba,
                        paleta = paleta,
                        acabamento = acabamento,
                    )

                    // — Área rolável com peso —
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        when (abaAtual) {
                            AbaDoMenu.JOGO -> ConteudoAbaJogo(
                                slots = slots,
                                slotSelecionado = slotSelecionado,
                                aoSalvarEstado = aoSalvarEstado,
                                aoCarregarEstado = aoCarregarEstado,
                                aoMudarSlot = { novoSlot ->
                                    slotSelecionado = novoSlot
                                    aoMudarSlot(novoSlot)
                                },
                                paleta = paleta,
                                acabamento = acabamento,
                                reduzirEfeitos = reduzirEfeitos,
                            )
                            AbaDoMenu.AJUSTES -> ConteudoAbaAjustes(
                                ajustes = ajustes,
                                aoMudarAjuste = aoMudarAjuste,
                                aoLimparAjustesJogo = aoLimparAjustesJogo,
                                paleta = paleta,
                                acabamento = acabamento,
                            )
                            AbaDoMenu.CONTROLES -> ConteudoAbaControles(
                                paleta = paleta,
                                mapeamento = mapeamento ?: IntArray(12),
                                atalhosJson = atalhosJson,
                                atalhosCfg = atalhosCfg,
                                plataforma = plataforma,
                                capturando = capturando,
                                aoCapturar = aoCapturar,
                                aoCancelarCaptura = aoCancelarCaptura,
                                aoLimpar = aoLimparAtalho,
                                aoLimparHotkey = aoLimparHotkey,
                                aoAlternarHotkey = aoAlternarHotkey,
                                aoRestaurar = aoRestaurarAtalhos,
                                aoRestaurarBotoes = aoRestaurarBotoes,
                                ajustes = ajustes,
                                aoMudarAjuste = aoMudarAjuste
                            )
                            else -> ConteudoAbaJogo(
                                slots = slots,
                                slotSelecionado = slotSelecionado,
                                aoSalvarEstado = aoSalvarEstado,
                                aoCarregarEstado = aoCarregarEstado,
                                aoMudarSlot = { novoSlot ->
                                    slotSelecionado = novoSlot
                                    aoMudarSlot(novoSlot)
                                },
                                paleta = paleta,
                                acabamento = acabamento,
                                reduzirEfeitos = reduzirEfeitos,
                            )
                        }
                    }

                    // — Rodapé fixo —
                    RodapeFixo(
                        aoContinuar = aoContinuar,
                        aoReiniciar = aoReiniciar,
                        aoSair = aoSair,
                        paleta = paleta,
                        acabamento = acabamento,
                    )
                }
            }
        }
    }
}

// =====================================================================
// Cabecalho
// =====================================================================

@Composable
private fun CabecalhoPausa(
    nomeDoJogo: String,
    tempoJogadoMinutos: Int,
    aoFechar: () -> Unit,
    capaLocalPath: String?,
    tempoJogadoMs: Long,
    plataforma: String,
    paleta: PaletaMenu,
) {
    val ctx = LocalContext.current
    val nomeAmigavel = rememberNomeAmigavel(nomeDoJogo)

    // Carregar capa com BitmapFactory (segundo plano, inSampleSize)
    val bitmapCapa = remember(capaLocalPath) {
        var bmp: android.graphics.Bitmap? = null
        if (!capaLocalPath.isNullOrBlank()) {
            try {
                val file = java.io.File(capaLocalPath)
                if (file.exists()) {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(capaLocalPath, opts)
                    val largura = opts.outWidth
                    val amostra = if (largura > 200) largura / 200 else 1
                    var realAmostra = 1
                    while (realAmostra * 2 <= amostra) realAmostra *= 2
                    opts.inSampleSize = realAmostra
                    opts.inJustDecodeBounds = false
                    opts.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    bmp = android.graphics.BitmapFactory.decodeFile(capaLocalPath, opts)
                }
            } catch (_: Exception) {
                bmp = null
            }
        }
        bmp
    }

    // Tempo total = tempo acumulado antes da sessao
    val tempoTotalMsFinal = tempoJogadoMs
    val tempoTexto = remember(tempoTotalMsFinal) {
        val totalMin = (tempoTotalMsFinal / 1000 / 60).toInt()
        when {
            totalMin < 1 -> ctx.getString(R.string.jogo_menu_tempo_sem_tempo)
            totalMin < 60 -> "${totalMin} min"
            else -> {
                val h = totalMin / 60
                val m = totalMin % 60
                if (m > 0) "${h} h ${m} min" else "${h} h"
            }
        }
    }
    val textoPlataformaTempo = remember(plataforma, tempoTexto) {
        if (tempoTexto == "—") plataforma
        else "$plataforma · $tempoTexto"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Capa ou placeholder
        if (bitmapCapa != null) {
            Image(
                bitmap = bitmapCapa.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A2A3E).copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(image = Icons.Outlined.Gamepad),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(paleta.texto.copy(alpha = 0.7f)),
                )
            }
        }

        // Nome + plataforma/tempo
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = nomeAmigavel,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paleta.texto),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = textoPlataformaTempo,
                style = TextStyle(fontSize = 12.sp, color = paleta.textoSecundario),
            )
        }

        // Botao X 40dp
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(paleta.texto.copy(alpha = 0.15f))
                .clickable(onClick = aoFechar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u2715",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = paleta.texto,
            )
        }
    }
}

private fun rememberNomeAmigavel(raw: String): String {
    val uri = try { Uri.parse(raw) } catch (e: Exception) { null }
    val nome = if (uri != null) {
        val decoded = Uri.decode(uri.lastPathSegment ?: raw)
        decoded
            .split("/", ":")
            .lastOrNull { it.isNotBlank() } ?: decoded
            .substringBeforeLast('.')
            .trim()
    } else {
        raw
            .substringBeforeLast('.')
            .trim()
    }
    // Remove extensões comuns de ROM/ZIP sem diferenciar maiúsculas/minúsculas
    val extensao = nome.substringAfterLast('.', "").lowercase()
    if (extensao in setOf("zip", "7z", "sfc", "smc", "nes", "fig", "swc")) {
        return nome.substringBeforeLast('.').trim()
    }
    return nome
}

// =====================================================================
// Barra de abas — ícone + texto, seleção com fundo e barra inferior
// =====================================================================

private data class TabItem(
    val aba: AbaDoMenu,
    val labelRes: Int,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun BarraDeAbas(
    abaAtual: AbaDoMenu,
    aoMudarAba: (AbaDoMenu) -> Unit,
    paleta: PaletaMenu,
    acabamento: String,
) {
    val ctx = LocalContext.current
    val abas = listOf(
        TabItem(AbaDoMenu.JOGO, R.string.pause_aba_jogo, Icons.Outlined.PlayArrow),
        TabItem(AbaDoMenu.AJUSTES, R.string.pause_aba_ajustes, Icons.Outlined.Settings),
        TabItem(AbaDoMenu.CONTROLES, R.string.pause_aba_controles, Icons.Outlined.Gamepad),
    )

    var abaIndex by remember { mutableIntStateOf(abas.indexOfFirst { it.aba == abaAtual }.takeIf { it >= 0 } ?: 0) }

    val corTexto = paleta.texto
    val corDestaque = paleta.destaque
    val cantos = RoundedCornerShape(16.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            abas.forEachIndexed { index, item ->
                val isSelected = index == abaIndex
                val bgAnim by animateColorAsState(
                    targetValue = if (isSelected) corDestaque.copy(alpha = 0.22f) else Color.Transparent,
                    label = "tabBg_$index",
                )
                val textAnim by animateColorAsState(
                    targetValue = if (isSelected) corTexto else corTexto.copy(alpha = 0.75f),
                    label = "tabText_$index",
                )
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(cantos)
                        .background(bgAnim)
                        .clickable(onClick = {
                            abaIndex = index
                            aoMudarAba(item.aba)
                            Log.d("PhoenixAjustes", "aba=$abaIndex")
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.iconVector,
                            contentDescription = ctx.getString(item.labelRes),
                            tint = textAnim,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = ctx.getString(item.labelRes),
                            fontSize = 14.sp,
                            fontWeight = fontWeight,
                            color = textAnim,
                        )
                    }

                    // Barra inferior 3dp (60% largura) — só na aba selecionada
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.6f)
                                .height(3.dp)
                                .background(corDestaque, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp)),
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Conteúdo da aba "Jogo"
// =====================================================================

@Composable
private fun ConteudoAbaJogo(
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoSalvarEstado: (Int) -> Unit,
    aoCarregarEstado: (Int) -> Unit,
    aoMudarSlot: (Int) -> Unit,
    paleta: PaletaMenu,
    acabamento: String,
    reduzirEfeitos: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 1 — Botões Salvar / Carregar
        LinhaSalvarCarregar(
            aoSalvar = { aoSalvarEstado(slotSelecionado) },
            aoCarregar = {
                if (slots.getOrNull(slotSelecionado - 1)?.exists == true) {
                    aoCarregarEstado(slotSelecionado)
                }
            },
            slotVazio = slots.getOrNull(slotSelecionado - 1)?.exists != true,
            numeroSlot = slotSelecionado,
            paleta = paleta,
            acabamento = acabamento,
        )

        // 2 — Rótulo SLOTS
        RótuloSeção(texto = "SLOTS", paleta = paleta)

        // 3 — Linha dos 4 slots
        LinhaSlots(slots, slotSelecionado, aoSelecionar = { aoMudarSlot(it) }, paleta = paleta, acabamento = acabamento)
    }
}

// ---- Botões Salvar/Carregar — VIDRO ou SÓLIDO conforme acabamento ----

@Composable
private fun BotaoVidro(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconRes: Int,
    labelRes: Int,
    ctx: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    paleta: PaletaMenu,
    acabamento: String,
) {
    val corDestaque = paleta.destaque
    val alphaEnabled = if (enabled) 1f else 0.4f
    val isSolido = acabamento.uppercase() == "SOLIDO"
    val cantos = RoundedCornerShape(16.dp)

    val fundoBotao = if (isSolido) {
        // SÓLIDO: fundo destaque alpha 0.16
        corDestaque.copy(alpha = 0.16f)
    } else {
        // VIDRO: fundo mais escuro que o painel, alpha 0.85
        val baseSemAlpha = paleta.fundoPainel.copy(alpha = 1f)
        val escurecido = baseSemAlpha.mixWith(Color.Black, 0.35f)
        escurecido.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .border(
                width = if (isSolido) 1.dp else 1.5.dp,
                color = corDestaque.copy(alpha = if (isSolido) 0.45f else 0.7f),
                shape = cantos,
            )
            .background(fundoBotao, cantos)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(alphaEnabled),
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (enabled) paleta.texto else paleta.texto.copy(alpha = 0.5f),
                ),
            )
            Text(
                text = ctx.getString(labelRes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) paleta.texto else paleta.texto.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun RótuloSeção(texto: String, paleta: PaletaMenu) {
    Text(
        text = texto,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = paleta.destaque.copy(alpha = 0.6f),
        letterSpacing = 2.sp,
    )
}

@Composable
private fun LinhaSalvarCarregar(
    aoSalvar: () -> Unit,
    aoCarregar: () -> Unit,
    slotVazio: Boolean,
    numeroSlot: Int,
    paleta: PaletaMenu,
    acabamento: String,
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BotaoVidro(
            onClick = aoSalvar,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = true,
            iconRes = R.drawable.ic_menu_salvar,
            labelRes = R.string.jogo_menu_salvar_estado,
            ctx = ctx,
            paleta = paleta,
            acabamento = acabamento,
        )
        BotaoVidro(
            onClick = aoCarregar,
            modifier = Modifier.weight(1f).height(48.dp),
            enabled = !slotVazio,
            iconRes = R.drawable.ic_menu_carregar,
            labelRes = R.string.jogo_menu_carregar_estado,
            ctx = ctx,
            paleta = paleta,
            acabamento = acabamento,
        )
    }
}

@Composable
private fun TextoEmBreve(paleta: PaletaMenu) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.em_breve),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = paleta.destaque,
        )
    }
}

@Composable
private fun LinhaSlots(
    slots: List<SlotData>,
    slotSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
    paleta: PaletaMenu,
    acabamento: String,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
    ) {
        val cardW = ((maxWidth - 8.dp * 3) / 4).coerceAtLeast(60.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            for ((index, slot) in slots.withIndex()) {
                val numeroSlot = index + 1
                val isSelected = numeroSlot == slotSelecionado
                SlotCardMini(
                    slot = slot,
                    numeroSlot = numeroSlot,
                    selecionado = isSelected,
                    onClick = { aoSelecionar(numeroSlot) },
                    cardWidth = cardW,
                    paleta = paleta,
                    acabamento = acabamento,
                )
            }
        }
    }
}

@Composable
private fun SlotCardMini(
    slot: SlotData,
    numeroSlot: Int,
    selecionado: Boolean,
    onClick: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    paleta: PaletaMenu,
    acabamento: String,
) {
    val ctx = LocalContext.current
    val corDestaque = paleta.destaque
    val isSolido = acabamento.uppercase() == "SOLIDO"

    // VIDRO: cantos 8dp, SÓLIDO: cantos 10dp
    val cantos = if (isSolido) 10.dp else 8.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(cardWidth),
    ) {
        // (a) Miniatura numa Box com emblema sobreposto
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardWidth * 3f / 4f)
                .clip(RoundedCornerShape(cantos))
                .border(
                    width = if (selecionado) 2.dp else 1.dp,
                    color = if (selecionado) corDestaque else paleta.texto.copy(alpha = if (isSolido) 0.3f else 0.3f),
                    shape = RoundedCornerShape(cantos),
                )
                .background(
                    if (slot.exists && slot.bitmap != null) Color.Transparent else Color.DarkGray.copy(alpha = 0.5f),
                    RoundedCornerShape(cantos),
                )
                .clickable(onClick = onClick),
        ) {
            // Conteúdo da miniatura
            if (slot.exists && slot.bitmap != null) {
                Image(
                    bitmap = slot.bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ctx.getString(R.string.jogo_slot_vazio_text),
                        fontSize = 10.sp,
                        color = paleta.texto.copy(alpha = 0.5f),
                    )
                }
            }

            // (b) Emblema circular 20dp no canto superior esquerdo, com 4dp de margem
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .padding(4.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = corDestaque,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    )
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = numeroSlot.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = paleta.texto,
                )
            }
        }

        // (c) Duas linhas pequenas abaixo — data e hora
        if (slot.exists && slot.dateText.isNotBlank() && slot.timeText.isNotBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = slot.dateText,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = paleta.texto.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = slot.timeText,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = paleta.texto.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// =====================================================================
// Rodapé fixo — botões VIDRO ou SÓLIDO conforme acabamento
// =====================================================================

@Composable
private fun RodapeFixo(
    aoContinuar: () -> Unit,
    aoReiniciar: () -> Unit,
    aoSair: () -> Unit,
    paleta: PaletaMenu,
    acabamento: String,
) {
    val ctx = LocalContext.current
    val corDestaque = paleta.destaque
    val isSolido = acabamento.uppercase() == "SOLIDO"

    Column {
        // Divisor de 1dp no topo (alpha 0.12)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(corDestaque.copy(alpha = 0.12f)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RodapeBotao(
                modifier = Modifier.weight(1f),
                onClick = aoContinuar,
                labelRes = R.string.jogo_menu_continuar,
                cor = corDestaque,
                ctx = ctx,
                paleta = paleta,
                acabamento = acabamento,
                isBotaoDestaque = true,
            )
            RodapeBotao(
                modifier = Modifier.weight(1f),
                onClick = aoReiniciar,
                labelRes = R.string.jogo_menu_reiniciar,
                cor = corDestaque,
                ctx = ctx,
                paleta = paleta,
                acabamento = acabamento,
                isBotaoDestaque = false,
            )
            RodapeBotao(
                modifier = Modifier.weight(1f),
                onClick = aoSair,
                labelRes = R.string.jogo_menu_sair,
                cor = corDestaque,
                ctx = ctx,
                paleta = paleta,
                acabamento = acabamento,
                isBotaoDestaque = false,
            )
        }
    }
}

@Composable
private fun RodapeBotao(
    modifier: Modifier,
    onClick: () -> Unit,
    labelRes: Int,
    cor: Color,
    ctx: android.content.Context,
    paleta: PaletaMenu,
    acabamento: String,
    isBotaoDestaque: Boolean,
) {
    val isSolido = acabamento.uppercase() == "SOLIDO"
    val cantos = RoundedCornerShape(16.dp)

    val fundoBotao = if (isSolido) {
        // SÓLIDO: fundo destaque alpha 0.16
        cor.copy(alpha = 0.16f)
    } else {
        // VIDRO: fundo mais escuro que o painel, alpha 0.85
        val baseSemAlpha = paleta.fundoPainel.copy(alpha = 1f)
        val escurecido = baseSemAlpha.mixWith(Color.Black, 0.35f)
        escurecido.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .border(
                width = if (isSolido) 1.dp else 1.5.dp,
                color = if (isSolido) cor.copy(alpha = 0.45f) else cor.copy(alpha = 0.7f),
                shape = cantos,
            )
            .background(fundoBotao, cantos)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ctx.getString(labelRes),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = paleta.texto,
        )
    }
}
