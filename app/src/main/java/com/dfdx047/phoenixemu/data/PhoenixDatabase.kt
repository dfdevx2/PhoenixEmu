package com.dfdx047.phoenixemu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [JogoEntity::class],
    version = 1,
    exportSchema = true
)
abstract class PhoenixDatabase : RoomDatabase() {

    abstract fun jogos(): JogoDao

    companion object {
        private const val NOME = "phoenix.db"

        @Volatile
        private var instancia: PhoenixDatabase? = null

        fun obter(context: Context): PhoenixDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhoenixDatabase::class.java,
                    NOME
                )
                    // Nada de fallbackToDestructiveMigration. Uma biblioteca
                    // com favoritos e tempo de jogo nao pode ser apagada
                    // porque o schema mudou; a partir da versao 2, cada
                    // mudanca ganha sua Migration escrita a mao, conferida
                    // contra o schema exportado em app/schemas/.
                    .build()
                    .also { instancia = it }
            }
    }
}
