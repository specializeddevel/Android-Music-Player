package com.raulburgosmurray.musicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Tracks only this app's transfer temporaries, not arbitrary files in the library. */
// Explicit commit() runs on IO and must persist before bytes are written. Keep its success result.
@SuppressLint("ApplySharedPref", "UseKtx")
object TransferTemporaryFiles {
    private const val PREFS = "transfer_temporaries"
    private const val ENTRIES = "pending"
    private var startupCleaned = false

    fun isTemporaryName(name: String?): Boolean =
        name != null && name.startsWith("litera-transfer-") && name.endsWith(".part")

    fun cleanEntries(entries: Set<String>, delete: (String) -> Boolean): Set<String> =
        entries.filterTo(mutableSetOf()) { !runCatching { delete(it) }.getOrDefault(false) }

    /** Must run off the main thread. The lock prevents cleanup racing a new receive. */
    @Synchronized
    fun cleanupOnStartup(context: Context) {
        if (startupCleaned) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(ENTRIES, emptySet())!!.toSet()
        val remaining = cleanEntries(pending) { target ->
            if (target.startsWith("content://")) {
                val uri = target.toUri()
                val document = DocumentFile.fromSingleUri(context, uri)
                    ?: return@cleanEntries false
                // Query directly: DocumentFile.exists() hides permission errors as "missing".
                context.contentResolver.query(
                    uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) true
                    else {
                        val name = cursor.getString(0) ?: return@use false
                        if (!isTemporaryName(name)) true // May already have been finalized.
                        else document.delete()
                    }
                } ?: false
            } else {
                val file = File(target)
                if (!isTemporaryName(file.name)) true
                else !file.exists() || file.delete()
            }
        }
        prefs.edit().putStringSet(ENTRIES, remaining).commit()
        startupCleaned = true
    }

    /** Persist before writing bytes so process death doesn't lose the cleanup target. */
    @Synchronized
    fun register(context: Context, target: String) {
        cleanupOnStartup(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(ENTRIES, emptySet())!!.toMutableSet()
        pending.add(target)
        check(prefs.edit().putStringSet(ENTRIES, pending).commit()) {
            "No se pudo registrar el archivo temporal"
        }
    }

    @Synchronized
    fun forget(context: Context, target: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(ENTRIES, emptySet())!!.toMutableSet()
        pending.remove(target)
        prefs.edit().putStringSet(ENTRIES, pending).commit()
    }
}
