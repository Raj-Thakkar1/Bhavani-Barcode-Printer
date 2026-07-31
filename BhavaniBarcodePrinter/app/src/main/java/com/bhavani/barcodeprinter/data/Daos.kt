package com.bhavani.barcodeprinter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY name ASC")
    fun allActive(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun allDeleted(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Item?

    @Query("SELECT * FROM items WHERE barcodeNum = :barcode AND deletedAt IS NULL LIMIT 1")
    suspend fun getByBarcode(barcode: String): Item?

    /** Used for the "this item name already exists" duplicate-barcode safeguard. */
    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByExactName(name: String): Item?

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND LOWER(name) LIKE '%' || LOWER(:name) || '%' LIMIT 5")
    suspend fun findSimilarByName(name: String): List<Item>

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("UPDATE items SET deletedAt = :when WHERE id = :id")
    suspend fun softDelete(id: Long, `when`: Long = System.currentTimeMillis())

    @Query("UPDATE items SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("SELECT * FROM items WHERE deletedAt IS NULL")
    suspend fun allActiveOnce(): List<Item>
}

@Dao
interface PriceHistoryDao {
    @Insert
    suspend fun insert(entry: PriceHistoryEntry)

    @Query("SELECT * FROM price_history WHERE itemId = :itemId ORDER BY changedAt DESC")
    fun forItem(itemId: Long): Flow<List<PriceHistoryEntry>>
}

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields ORDER BY sortOrder ASC")
    fun all(): Flow<List<CustomFieldDef>>

    @Query("SELECT * FROM custom_fields ORDER BY sortOrder ASC")
    suspend fun allOnce(): List<CustomFieldDef>

    @Insert
    suspend fun insert(field: CustomFieldDef): Long

    @Update
    suspend fun update(field: CustomFieldDef)

    @Query("DELETE FROM custom_fields WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM custom_fields WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): CustomFieldDef?
}

@Dao
interface PrintHistoryDao {
    @Insert
    suspend fun insert(entry: PrintHistoryEntry)

    @Query("SELECT * FROM print_history ORDER BY printedAt DESC LIMIT 200")
    fun recent(): Flow<List<PrintHistoryEntry>>
}

@Dao
interface PrnHistoryDao {
    @Insert
    suspend fun insert(entry: PrnHistoryEntry): Long

    @Query("SELECT * FROM prn_history ORDER BY savedAt DESC")
    fun all(): Flow<List<PrnHistoryEntry>>

    @Query("SELECT * FROM prn_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PrnHistoryEntry?

    /** Manual deletion only — there is deliberately no auto-purge for PRN history. */
    @Query("DELETE FROM prn_history WHERE id = :id")
    suspend fun delete(id: Long)
}
