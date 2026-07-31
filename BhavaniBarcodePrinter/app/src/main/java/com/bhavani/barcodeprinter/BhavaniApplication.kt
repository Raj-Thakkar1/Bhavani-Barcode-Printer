package com.bhavani.barcodeprinter

import android.app.Application

class BhavaniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Installed here (not in MainActivity) so it also catches crashes that happen
        // before any UI is shown, e.g. during AppContainer/database initialization.
        CrashHandler.install(this)
    }
}
