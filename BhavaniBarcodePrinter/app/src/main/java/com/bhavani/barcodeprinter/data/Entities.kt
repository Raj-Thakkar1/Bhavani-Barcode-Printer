package com.bhavani.barcodeprinter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One catalog item. This is the single source of truth for a product's name/barcode/prices,
 * so re-printing the same item never generates a new barcode number again.
 *
 * customFieldsJson holds a flat JSON object of extra values keyed by CustomFieldDef.key,
 * e.g. {"batch_no": "B-104", "supplier": "XYZ Traders"} — so the schema can grow without
 * a database migration every time a shop-specific field is added.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcodeNum: String,
    val mrp: String = "",
    val sp: String = "",
    val qty: String = "",
    val mfg: String = "",
    val exp: String = "",
    val customFieldsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** null = active. Non-null = soft-deleted at this time; purged 30 days after. */
    val deletedAt: Long? = null
)

/** Logged every time MRP/SP changes for an item, so price changes are auditable. */
@Entity(tableName = "price_history")
data class PriceHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val mrp: String,
    val sp: String,
    val changedAt: Long = System.currentTimeMillis()
)

enum class FieldDataType { TEXT, NUMBER, DATE }

/**
 * Definition of a custom (shop-added) field, e.g. "Batch No." / "Supplier".
 * showOnLabel and storeInDb are independent switches per the Settings requirement:
 * a field can exist in the DB only, be printed on the label only (rare), or both.
 */
@Entity(tableName = "custom_fields")
data class CustomFieldDef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val label: String,
    val dataType: FieldDataType = FieldDataType.TEXT,
    val storeInDb: Boolean = true,
    val showOnLabel: Boolean = false,
    val sortOrder: Int = 0
)

/** One row per print job, for POS reconciliation / "did I already print this?" checks. */
@Entity(tableName = "print_history")
data class PrintHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long?,
    val itemName: String,
    val barcodeNum: String,
    val copies: Int,
    val printedAt: Long = System.currentTimeMillis(),
    val success: Boolean,
    val message: String = ""
)

/**
 * A saved version of the raw PRN/TSPL template. Written every time the template is saved,
 * either from the raw text editor or from the visual Label Designer. Never auto-deleted —
 * only the person can remove a version, from the PRN History screen.
 */
@Entity(tableName = "prn_history")
data class PrnHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    /** "designer", "raw_editor", "restored", or "initial" */
    val source: String,
    val note: String = "",
    val savedAt: Long = System.currentTimeMillis()
)
