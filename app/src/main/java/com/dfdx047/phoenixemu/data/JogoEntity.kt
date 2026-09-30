package com.dfdx047.phoenixemu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.Sistema

/**
 * A tabela.
 *
 * Ela e separada do `Jogo` (o modelo que a UI usa) de proposito. Duas razoes
 * concretas, nao purismo:
 *
 *  1. A coluna `nome_ordenacao` nao tem nada que fazer na UI. Ela existe para
 *     o banco: `ORDER BY LOWER(nome)` obriga o SQLite a calcular LOWER() em
 *     cada linha e a montar uma B-tree temporaria, porque nenhum indice serve.
 *     Guardando o nome ja em minusculas, o indice resolve a ordenacao sozinho.
 *  2. Os nomes de coluna ficam em snake_case e estaveis, independentes de como
 *     as propriedades Kotlin forem renomeadas depois. Renomear um campo do
 *     `Jogo` nao vira migracao de banco.
 *
 * O mapeamento entre os dois mora neste arquivo, num lugar so.
 */
@Entity(
    tableName = "jogos",
    indices = [
        Index("sistema"),
        Index("favorito"),
        Index("ultima_vez_jogado"),
        Index("tempo_jogado"),
        Index("nome_ordenacao"),
        Index("ausente"),
        Index("capa_pendente"),
        Index("hash_ra")
    ]
)
data class JogoEntity(
    /** A URI do documento SAF. Identidade de verdade, desde a Fase 0. */
    @PrimaryKey
    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "nome") val nome: String,

    /** `nome` em minusculas. Existe so para o indice de ordenacao. */
    @ColumnInfo(name = "nome_ordenacao") val nomeOrdenacao: String,

    @ColumnInfo(name = "nome_arquivo") val nomeArquivo: String,
    @ColumnInfo(name = "extensao") val extensao: String,

    /** Guardado como texto ("NES" / "SNES"): legivel no diff do schema. */
    @ColumnInfo(name = "sistema") val sistema: String,

    @ColumnInfo(name = "regiao") val regiao: String,
    @ColumnInfo(name = "favorito") val favorito: Boolean,
    @ColumnInfo(name = "ultima_vez_jogado") val ultimaVezJogado: Long,
    @ColumnInfo(name = "tempo_jogado") val tempoJogadoMinutos: Int,
    @ColumnInfo(name = "capa_url") val capaUrl: String?,
    @ColumnInfo(name = "capa_local") val capaLocal: String?,
    @ColumnInfo(name = "capa_pendente") val capaPendente: Boolean,
    @ColumnInfo(name = "tamanho_bytes") val tamanhoBytes: Long,
    @ColumnInfo(name = "modificado_em") val modificadoEm: Long,
    @ColumnInfo(name = "hash_ra") val hashRa: String?,
    @ColumnInfo(name = "crc32") val crc32: String?,
    @ColumnInfo(name = "ausente") val ausente: Boolean
)

/**
 * Projecao leve para a varredura incremental.
 *
 * Comparar 5.000 arquivos nao precisa das capas, dos hashes nem dos nomes:
 * precisa de tres colunas. Trazer a tabela inteira para a memoria so para
 * isso e desperdicio que cresce com a biblioteca.
 */
data class AssinaturaDeJogo(
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "tamanho_bytes") val tamanhoBytes: Long,
    @ColumnInfo(name = "modificado_em") val modificadoEm: Long
)

// =====================================================================
// MAPEAMENTO
// =====================================================================

fun JogoEntity.paraModelo(): Jogo = Jogo(
    nome = nome,
    nomeArquivoOriginal = nomeArquivo,
    extensao = extensao,
    uriString = uri,
    // Sistema desconhecido no banco (schema mexido a mao, linha corrompida)
    // cai em NES em vez de derrubar a tela inteira com uma excecao.
    sistema = Sistema.deNome(sistema) ?: Sistema.NES,
    regiao = regiao,
    isFavorito = favorito,
    ultimaVezJogado = ultimaVezJogado,
    tempoJogadoMinutos = tempoJogadoMinutos,
    capaUrl = capaUrl,
    capaLocal = capaLocal,
    capaPendente = capaPendente,
    tamanhoBytes = tamanhoBytes,
    modificadoEm = modificadoEm,
    hashRa = hashRa,
    crc32 = crc32,
    ausente = ausente
)

fun Jogo.paraEntidade(): JogoEntity = JogoEntity(
    uri = uriString,
    nome = nome,
    nomeOrdenacao = nome.lowercase(),
    nomeArquivo = nomeArquivoOriginal,
    extensao = extensao,
    sistema = sistema.name,
    regiao = regiao,
    favorito = isFavorito,
    ultimaVezJogado = ultimaVezJogado,
    tempoJogadoMinutos = tempoJogadoMinutos,
    capaUrl = capaUrl,
    capaLocal = capaLocal,
    capaPendente = capaPendente,
    tamanhoBytes = tamanhoBytes,
    modificadoEm = modificadoEm,
    hashRa = hashRa,
    crc32 = crc32,
    ausente = ausente
)

fun List<JogoEntity>.paraModelos(): List<Jogo> = map { it.paraModelo() }
