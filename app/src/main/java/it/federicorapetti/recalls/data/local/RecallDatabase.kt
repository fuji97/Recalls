package it.federicorapetti.recalls.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecallEntity::class, SourceStateEntity::class, SgBarcodeEntity::class],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sg_barcodes` (`recallId` TEXT NOT NULL, `codes` TEXT NOT NULL, PRIMARY KEY(`recallId`))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "productName", "brand", "operator", "lot", "plantMark", "producer",
                    "plantSite", "expiry", "quantity", "reasonDetail", "warnings"
                ).forEach { db.execSQL("ALTER TABLE recalls ADD COLUMN pdf_$it TEXT") }
                db.execSQL("ALTER TABLE recalls ADD COLUMN pdfPhotoCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recalls ADD COLUMN pdfParsedAt INTEGER")
                db.execSQL("UPDATE recalls SET imageUrl = NULL WHERE source = 'IT_OPERATOR'")
            }
        }
    }
}
