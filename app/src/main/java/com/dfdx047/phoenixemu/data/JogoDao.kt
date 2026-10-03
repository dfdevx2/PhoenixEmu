package com.dfdx047.phoenixemu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Acesso a tabela de jogos.
 *
 * Duas decisoes que valem explicacao:
 *
 * **Updates pontuais em vez de reescrever a lista.** Na Fase 1A, marcar um
 * favorito significava copiar a lista inteira em memoria e reserializar o
 * JSON. Aqui e um `UPDATE ... WHERE uri = ?`: uma linha. Com 5.000 jogos a
 * diferenca deixa de ser detalhe.
 *
 * **Tres consultas de listagem, uma por ordenacao, em vez de uma so com
 * CASE WHEN.** O truque do `ORDER BY CASE WHEN :ordem = 0 THEN ...` e mais
 * compacto, mas impede o SQLite de usar indice: ele monta uma B-tree
 * temporaria toda vez. Com consultas separadas, cada `ORDER BY` cai direto
 * no indice correspondente. O custo e repetir o WHERE tres vezes.
 *
 * A busca por nome (`LIKE '%termo%'`) e varredura completa por natureza --
 * indice nenhum serve para curinga a esquerda. Numa biblioteca de milhares
 * isso ainda e barato; se um dia incomodar, a saida e uma tabela FTS.
 */
@Dao
interface JogoDao {

    // ------------------------------------------------------------ listagem

    @Query(
        """
        SELECT * FROM jogos
        WHERE (:sistema IS NULL OR sistema = :sistema)
          AND (:somenteFavoritos = 0 OR favorito = 1)
          AND (:somenteRecentes = 0 OR ultima_vez_jogado > 0)
          AND (:termo = '' OR nome LIKE '%' || :termo || '%')
        ORDER BY nome_ordenacao ASC
        """
    )
    fun observarPorNome(
        sistema: String?,
        somenteFavoritos: Boolean,
        somenteRecentes: Boolean,
        termo: String
    ): Flow<List<JogoEntity>>

    @Query(
        """
        SELECT * FROM jogos
        WHERE (:sistema IS NULL OR sistema = :sistema)
          AND (:somenteFavoritos = 0 OR favorito = 1)
          AND (:somenteRecentes = 0 OR ultima_vez_jogado > 0)
          AND (:termo = '' OR nome LIKE '%' || :termo || '%')
        ORDER BY tempo_jogado DESC, nome_ordenacao ASC
        """
    )
    fun observarPorTempoJogado(
        sistema: String?,
        somenteFavoritos: Boolean,
        somenteRecentes: Boolean,
        termo: String
    ): Flow<List<JogoEntity>>

    @Query(
        """
        SELECT * FROM jogos
        WHERE (:sistema IS NULL OR sistema = :sistema)
          AND (:somenteFavoritos = 0 OR favorito = 1)
          AND (:somenteRecentes = 0 OR ultima_vez_jogado > 0)
          AND (:termo = '' OR nome LIKE '%' || :termo || '%')
        ORDER BY ultima_vez_jogado DESC, nome_ordenacao ASC
        """
    )
    fun observarPorUltimaVezJogado(
        sistema: String?,
        somenteFavoritos: Boolean,
        somenteRecentes: Boolean,
        termo: String
    ): Flow<List<JogoEntity>>

    @Query("SELECT COUNT(*) FROM jogos")
    suspend fun contar(): Int

    /** Todos os jogos, sem filtro de sistema/busca/favoritos. */
    @Query("SELECT * FROM jogos ORDER BY nome_ordenacao ASC")
    fun observarTodos(): Flow<List<JogoEntity>>

    @Query("SELECT * FROM jogos WHERE uri = :uri LIMIT 1")
    suspend fun porUri(uri: String): JogoEntity?

    // ---------------------------------------------------------- varredura

    /** So as tres colunas que a comparacao incremental precisa. */
    @Query("SELECT uri, tamanho_bytes, modificado_em FROM jogos")
    suspend fun assinaturas(): List<AssinaturaDeJogo>

    @Query("SELECT uri FROM jogos WHERE ausente = 1")
    suspend fun urisAusentes(): List<String>

    @Query("UPDATE jogos SET ausente = 1 WHERE uri IN (:uris)")
    suspend fun marcarAusentes(uris: List<String>)

    @Query("UPDATE jogos SET ausente = 0 WHERE uri IN (:uris)")
    suspend fun marcarPresentes(uris: List<String>)

    // ------------------------------------------------------------ escrita

    @Upsert
    suspend fun salvar(jogos: List<JogoEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirIgnorando(jogos: List<JogoEntity>): List<Long>

    @Query("DELETE FROM jogos WHERE uri = :uri")
    suspend fun remover(uri: String)

    @Query("UPDATE jogos SET favorito = NOT favorito WHERE uri = :uri")
    suspend fun alternarFavorito(uri: String)

    @Query(
        """
        UPDATE jogos
        SET ultima_vez_jogado = :quando,
            tempo_jogado = tempo_jogado + :minutos
        WHERE uri = :uri
        """
    )
    suspend fun registrarSessao(uri: String, quando: Long, minutos: Int)

    /**
     * Preserva o que e do usuario quando um arquivo muda de conteudo.
     * O hash e zerado de proposito: o conteudo e outro, o hash antigo mente.
     */
    @Query(
        """
        UPDATE jogos
        SET nome = :nome,
            nome_ordenacao = :nomeOrdenacao,
            nome_arquivo = :nomeArquivo,
            extensao = :extensao,
            sistema = :sistema,
            regiao = :regiao,
            tamanho_bytes = :tamanho,
            modificado_em = :modificado,
            hash_ra = NULL,
            crc32 = NULL,
            ausente = 0,
            capa_pendente = (capa_local IS NULL OR capa_local = '')
        WHERE uri = :uri
        """
    )
    suspend fun atualizarArquivoAlterado(
        uri: String,
        nome: String,
        nomeOrdenacao: String,
        nomeArquivo: String,
        extensao: String,
        sistema: String,
        regiao: String,
        tamanho: Long,
        modificado: Long
    )

    // -------------------------------------------------- usado pelos Workers

    @Query(
        """
        SELECT * FROM jogos
        WHERE capa_pendente = 1
          AND (capa_local IS NULL OR capa_local = '')
          AND ausente = 0
        ORDER BY nome_ordenacao ASC
        """
    )
    suspend fun semCapa(): List<JogoEntity>

    @Query(
        """
        SELECT * FROM jogos
        WHERE (hash_ra IS NULL OR hash_ra = '')
          AND ausente = 0
        ORDER BY tamanho_bytes ASC
        """
    )
    suspend fun semHash(): List<JogoEntity>

    @Query(
        """
        UPDATE jogos
        SET capa_local = :capaLocal,
            capa_url = :capaUrl,
            capa_pendente = :pendente
        WHERE uri = :uri
        """
    )
    suspend fun definirCapa(uri: String, capaLocal: String?, capaUrl: String?, pendente: Boolean)

    @Query(
        """
        UPDATE jogos
        SET capa_pendente = 0, capa_url = NULL, capa_local = NULL
        WHERE uri = :uri
        """
    )
    suspend fun marcarSemCapa(uri: String)

    @Query("UPDATE jogos SET crc32 = :crc32, hash_ra = :hashRa WHERE uri = :uri")
    suspend fun definirIdentidade(uri: String, crc32: String, hashRa: String)

    /** Registra que o jogo acabou de ser aberto: atualiza o timestamp de última vez jogado. */
    @Query(
        """
        UPDATE jogos
        SET ultima_vez_jogado = :quando
        WHERE uri = :uri
        """
    )
    suspend fun registrarAbertura(uri: String, quando: Long)

    /** Soma tempo de sessão ao total já acumulado. */
    @Query(
        """
        UPDATE jogos
        SET tempo_jogado = tempo_jogado + :minutos
        WHERE uri = :uri
        """
    )
    suspend fun somarTempo(uri: String, minutos: Int)
}
