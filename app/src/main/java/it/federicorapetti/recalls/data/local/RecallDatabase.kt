package it.federicorapetti.recalls.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecallEntity::class, SourceStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RecallDatabase : RoomDatabase() {
    abstract fun recallDao(): RecallDao

    companion object {
        const val DB_NAME = "recalls.db"
    }
}
