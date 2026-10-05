package com.dfdx047.phoenixemu.emulator

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dfdx047.phoenixemu.data.ControleNaTela
import com.dfdx047.phoenixemu.data.TipoControleNaTela
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Formato de uma skin da comunidade: um .zip com
//   skin.json   -> metadados e quais imagens usar
//   *.png       -> imagens com fundo transparente
//
// {
//   "id": "meu_pack", "nome": "Meu Pack", "autor": "fulano", "versao": 1,
//   "cor_rotulo": "#FFFFFF", "rotulos": true,
//   "imagens": {
//     "botao":  { "normal": "botao.png",  "pressionado": "botao_p.png" },
//     "pilula": { "normal": "pilula.png", "pressionado": "pilula_p.png" },
//     "dpad":   { "normal": "dpad.png",   "pressionado": "dpad_p.png" }
//   }
// }
// "pressionado" e opcional. Chave que faltar usa o desenho da skin Moderna.
// ---------------------------------------------------------------------------

data class ImagemSkinJson(
    val normal: String? = null,
    val pressionado: String? = null
)

data class SkinJson(
    val id: String? = null,
    val nome: String? = null,
    val autor: String? = null,
    val versao: Int? = null,
    @SerializedName("cor_rotulo") val corRotulo: String? = null,
    val rotulos: Boolean? = null,
    val imagens: Map<String, ImagemSkinJson>? = null
)

class SkinDeArquivo(
    override val id: String,
    override val nome: String,
    override val autor: String,
    val versao: Int,
    private val corRotulo: Color?,
    private val comRotulos: Boolean,
    private val normal: Map<String, ImageBitmap>,
    private val pressionadas: Map<String, ImageBitmap>
) : SkinDeControles {

    override fun desenharControle(
        escopoCanvas: DrawScope,
        controle: ControleNaTela,
        centro: Offset,
        raioOuTamanho: Float,
        pressionado: Boolean,
        corBase: Color,
        corAcento: Color,
        opacidade: Float,
        mask: Int,
        mostrarRotulos: Boolean,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        val chave = when {
            controle.tipo == TipoControleNaTela.DPAD -> "dpad"
            controle.formato == "PILULA" -> "pilula"
            else -> "botao"
        }
        val imgPress = if (pressionado) pressionadas[chave] else null
        val img = imgPress ?: normal[chave]
        if (img == null) {
            SkinModerna.desenharControle(
                escopoCanvas, controle, centro, raioOuTamanho, pressionado, corBase, corAcento,
                opacidade, mask, mostrarRotulos, textMeasurer, textStyle
            )
            return
        }
        val escala = if (pressionado && imgPress == null) 0.94f else 1f
        val (w, h) = when (chave) {
            "dpad" -> (raioOuTamanho * 2.2f) to (raioOuTamanho * 2.2f)
            "pilula" -> (raioOuTamanho * 3f) to (raioOuTamanho * 1.4f)
            else -> (raioOuTamanho * 2f) to (raioOuTamanho * 2f)
        }
        val tw = (w * escala).roundToInt().coerceAtLeast(1)
        val th = (h * escala).roundToInt().coerceAtLeast(1)
        escopoCanvas.drawImage(
            image = img,
            dstOffset = IntOffset((centro.x - tw / 2f).roundToInt(), (centro.y - th / 2f).roundToInt()),
            dstSize = IntSize(tw, th),
            alpha = opacidade.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium
        )
        if (comRotulos && chave != "dpad") {
            desenharRotulo(
                escopoCanvas, controle, centro, raioOuTamanho, corRotulo ?: Color.White,
                mostrarRotulos, textMeasurer, textStyle, opacidade
            )
        }
    }
}

sealed interface ResultadoSkin {
    data class Ok(val skin: SkinDeArquivo) : ResultadoSkin
    /** codigos: zip_invalido, arquivo_proibido, grande, sem_manifesto, manifesto_invalido, imagem_invalida, id_reservado */
    data class Erro(val codigo: String, val detalhe: String = "") : ResultadoSkin
}

object CarregadorDeSkins {
    private val ID_OK = Regex("^[a-z0-9_]{3,40}$")
    private val ARQ_OK = Regex("^[A-Za-z0-9_\\-]{1,40}\\.(png|json)$")
    private const val MAX_ARQ = 2L * 1024 * 1024
    private const val MAX_TOTAL = 6L * 1024 * 1024
    private const val MAX_ENTRADAS = 16
    private const val MAX_LADO = 1024
    private val CHAVES = setOf("botao", "pilula", "dpad")

    fun pasta(ctx: Context): File = File(ctx.filesDir, "skins")

    fun carregarInstaladas(ctx: Context): List<SkinDeArquivo> =
        pasta(ctx).listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { d -> runCatching { ler(d) }.getOrNull() }
            ?.sortedBy { it.nome.lowercase() }
            ?: emptyList()

    fun remover(ctx: Context, id: String) {
        if (!ID_OK.matches(id)) return
        File(pasta(ctx), id).deleteRecursively()
    }

    private fun ler(dir: File): SkinDeArquivo {
        val j = Gson().fromJson(File(dir, "skin.json").readText(), SkinJson::class.java)
        val id = j.id ?: error("sem id")
        require(ID_OK.matches(id) && id == dir.name) { "id invalido" }
        val normal = HashMap<String, ImageBitmap>()
        val press = HashMap<String, ImageBitmap>()
        for ((chave, par) in j.imagens.orEmpty()) {
            if (chave !in CHAVES) continue
            par.normal?.let { n -> decodificar(File(dir, n))?.let { normal[chave] = it } }
            par.pressionado?.let { n -> decodificar(File(dir, n))?.let { press[chave] = it } }
        }
        val cor = j.corRotulo?.let { c -> runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrNull() }
        return SkinDeArquivo(
            id = id,
            nome = (j.nome ?: id).trim().take(40).ifBlank { id },
            autor = (j.autor ?: "").trim().take(40),
            versao = j.versao ?: 1,
            corRotulo = cor,
            comRotulos = j.rotulos != false,
            normal = normal,
            pressionadas = press
        )
    }

    private fun decodificar(f: File): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()

    private fun medidasOk(f: File): Boolean {
        if (!f.isFile) return false
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        return o.outWidth in 1..MAX_LADO && o.outHeight in 1..MAX_LADO
    }

    /** Instala (ou atualiza) uma skin a partir de um .zip. Valida tudo antes de mexer na pasta final. */
    fun instalarZip(ctx: Context, entrada: InputStream): ResultadoSkin {
        val raiz = pasta(ctx)
        raiz.mkdirs()
        val tmp = File(raiz, ".tmp_" + System.nanoTime())
        try {
            tmp.mkdirs()
            var total = 0L
            var n = 0
            try {
                ZipInputStream(entrada.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory || e.name.startsWith("__MACOSX")) continue
                        val nome = e.name.substringAfterLast('/')
                        if (nome.startsWith("._")) continue
                        if (!ARQ_OK.matches(nome)) return ResultadoSkin.Erro("arquivo_proibido", nome)
                        if (++n > MAX_ENTRADAS) return ResultadoSkin.Erro("grande", "entradas")
                        var lido = 0L
                        File(tmp, nome).outputStream().use { out ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val r = zip.read(buf)
                                if (r < 0) break
                                lido += r
                                total += r
                                if (lido > MAX_ARQ || total > MAX_TOTAL) return ResultadoSkin.Erro("grande", nome)
                                out.write(buf, 0, r)
                            }
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                return ResultadoSkin.Erro("zip_invalido")
            }

            val manifesto = File(tmp, "skin.json")
            if (!manifesto.isFile) return ResultadoSkin.Erro("sem_manifesto")
            val j = try {
                Gson().fromJson(manifesto.readText(), SkinJson::class.java)
            } catch (e: Exception) {
                return ResultadoSkin.Erro("manifesto_invalido")
            } ?: return ResultadoSkin.Erro("manifesto_invalido")

            val id = j.id ?: return ResultadoSkin.Erro("manifesto_invalido", "id")
            if (!ID_OK.matches(id)) return ResultadoSkin.Erro("manifesto_invalido", "id")
            if (Skins.embutidas.any { it.id == id }) return ResultadoSkin.Erro("id_reservado", id)
            val imagens = j.imagens
            if (imagens.isNullOrEmpty()) return ResultadoSkin.Erro("manifesto_invalido", "imagens")
            for ((chave, par) in imagens) {
                if (chave !in CHAVES) return ResultadoSkin.Erro("manifesto_invalido", chave)
                val nomes = listOfNotNull(par.normal, par.pressionado)
                if (par.normal == null) return ResultadoSkin.Erro("manifesto_invalido", chave)
                for (nome in nomes) {
                    if (!ARQ_OK.matches(nome) || !nome.endsWith(".png")) return ResultadoSkin.Erro("imagem_invalida", nome)
                    if (!medidasOk(File(tmp, nome))) return ResultadoSkin.Erro("imagem_invalida", nome)
                }
            }

            val destino = File(raiz, id)
            if (destino.exists()) destino.deleteRecursively()
            if (!tmp.renameTo(destino)) return ResultadoSkin.Erro("zip_invalido", "mover")
            return ResultadoSkin.Ok(ler(destino))
        } catch (e: Exception) {
            return ResultadoSkin.Erro("zip_invalido", e.javaClass.simpleName)
        } finally {
            if (tmp.exists()) tmp.deleteRecursively()
        }
    }
}
