package com.bhavani.barcodeprinter

import android.content.Context
import com.bhavani.barcodeprinter.data.AppDatabase
import com.bhavani.barcodeprinter.data.SettingsStore

/** Lightweight manual DI: one instance per app process, created from Application context. */
object AppContainer {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var usbPrinter: UsbPrinter
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        db = AppDatabase.get(appContext)
        settings = SettingsStore(appContext)
        usbPrinter = UsbPrinter(appContext)
        initialized = true
    }
}
