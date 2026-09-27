package it.federicorapetti.recalls.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecallEntity::class, SourceStateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RecallDatabase : RoomDatabase() {
    abstract fun recallDao(): RecallDao

    companion object {
        const val DB_NAME = "recalls.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE recalls SET imageUrl = attachmentUrl " +
                        "WHERE source = 'IT_OPERATOR' AND imageUrl IS NULL AND attachmentUrl IS NOT NULL"
                )
            }
        }
    }
}
