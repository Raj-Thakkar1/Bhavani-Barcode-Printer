package com.bhavani.barcodeprinter

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches any crash that would otherwise just show "App has stopped" and writes the full
 * stack trace to a plain-text file in internal storage (app-private, no permissions needed).
 * MainActivity checks for this file on next launch and shows it in a copyable dialog
 * (see CrashLogDialog), so a crash on a device with no computer nearby (e.g. the POS tablet)
 * can still be captured and sent for debugging.
 */
object CrashHandler {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, throwable)
            } catch (_: Throwable) {
                // If even logging fails, fall through to the default handler below.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("Bhavani Barcode Printer — crash log")
            pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            pw.println("App version: ${versionName(context)}")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            pw.println()
            throwable.printStackTrace(pw)
        }
        File(context.filesDir, FILE_NAME).writeText(sw.toString())
    }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: PackageManager.NameNotFoundException) {
        "unknown"
    }

    /** Returns the last saved crash log text, or null if the app hasn't crashed since it was last read. */
    fun readCrashLog(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }

    fun clearCrashLog(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
