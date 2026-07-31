package com.bhavani.barcodeprinter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bhavani.barcodeprinter.printing.PrnCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "bhavani_settings")

object SettingsKeys {
    val PRINTER_VENDOR_ID = intPreferencesKey("printer_vendor_id")
    val PRINTER_PRODUCT_ID = intPreferencesKey("printer_product_id")
    val PRINTER_DEVICE_NAME = stringPreferencesKey("printer_device_name")
    val DEFAULT_COPIES = intPreferencesKey("default_copies")
    val PRN_TEMPLATE_TEXT = stringPreferencesKey("prn_template_text")
}

class SettingsStore(private val context: Context) {

    val defaultCopies: Flow<Int> =
        context.settingsDataStore.data.map { it[SettingsKeys.DEFAULT_COPIES] ?: 1 }

    val savedPrinterDeviceName: Flow<String?> =
        context.settingsDataStore.data.map { it[SettingsKeys.PRINTER_DEVICE_NAME] }

    /** The current PRN template text — single source of truth for both printing and editing. */
    val prnTemplateText: Flow<String> =
        context.settingsDataStore.data.map {
            it[SettingsKeys.PRN_TEMPLATE_TEXT] ?: PrnCodec.DEFAULT_PRN_TEMPLATE
        }

    suspend fun currentPrnTemplate(): String = prnTemplateText.first()

    /** Just updates the "current" pointer. Callers are responsible for also logging a
     *  PrnHistoryEntry (kept in AppDatabase, not here, so this store stays storage-only). */
    suspend fun savePrnTemplate(text: String) {
        context.settingsDataStore.edit { it[SettingsKeys.PRN_TEMPLATE_TEXT] = text }
    }

    suspend fun saveDefaultCopies(copies: Int) {
        context.settingsDataStore.edit { it[SettingsKeys.DEFAULT_COPIES] = copies }
    }

    suspend fun savePrinterSelection(vendorId: Int, productId: Int, deviceName: String) {
        context.settingsDataStore.edit {
            it[SettingsKeys.PRINTER_VENDOR_ID] = vendorId
            it[SettingsKeys.PRINTER_PRODUCT_ID] = productId
            it[SettingsKeys.PRINTER_DEVICE_NAME] = deviceName
        }
    }
}
