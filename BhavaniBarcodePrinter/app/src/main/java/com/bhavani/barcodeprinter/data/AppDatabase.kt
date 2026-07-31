package com.bhavani.barcodeprinter.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun toFieldDataType(value: String): FieldDataType = FieldDataType.valueOf(value)

    @TypeConverter
    fun fromFieldDataType(value: FieldDataType): String = value.name
}

/** v1 -> v2: adds the prn_history table. Existing items/fields/history are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `prn_history` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `content` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `savedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [Item::class, PriceHistoryEntry::class, CustomFieldDef::class, PrintHistoryEntry::class, PrnHistoryEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun printHistoryDao(): PrintHistoryDao
    abstract fun prnHistoryDao(): PrnHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bhavani_barcode_printer.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
