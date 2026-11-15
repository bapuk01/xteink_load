package com.xtink.sync

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncOutcome(val ok: Boolean, val message: String)

/**
 * Looks at the chosen phone folder and sends every book that has not been sent yet.
 * A book is identified by "name|size", so a file that is replaced with a different size is sent again.
 * Books that are already on the reader (same name) are marked as sent and not uploaded again.
 */
object Syncer {

    private val lock = Any()
    private val BOOK_EXTENSIONS = setOf("epub", "txt", "md", "xtc") // formats CrossPoint can open

    /** Runs one sync. Safe to call from a background thread; concurrent calls wait for each other. */
    fun run(context: Context): SyncOutcome = synchronized(lock) { doSync(context) }

    private fun doSync(context: Context): SyncOutcome {
        val prefs = Prefs(context)
        val readerUrl = prefs.readerUrl
        val folderUri = prefs.localFolder
        if (readerUrl.isBlank()) return finish(prefs, false, "Укажите адрес читалки")
        if (folderUri == null) return finish(prefs, false, "Выберите папку с книгами")

        val folder = DocumentFile.fromTreeUri(context, folderUri)
        if (folder == null || !folder.isDirectory) {
            return finish(prefs, false, "Папка недоступна, выберите её заново")
        }

        val client = ReaderClient(readerUrl)
        val remoteFolder = prefs.remoteFolder

        val remoteNames = try {
            client.listFileNames(remoteFolder)
        } catch (e: IOException) {
            return finish(
                prefs, false,
                "Читалка не отвечает (${e.message}). На ней откройте File Transfer → Join Network и проверьте адрес."
            )
        }

        val books = folder.listFiles().filter { it.isFile && isBook(it.name) }
        val known = prefs.uploaded.toMutableSet()
        var sent = 0
        var alreadyThere = 0
        var failed = 0
        var lastError = ""
        var folderReady = false

        for (file in books) {
            val name = file.name ?: continue
            val key = "$name|${file.length()}"
            if (key in known) continue

            if (name in remoteNames) {
                known += key
                alreadyThere++
                prefs.uploaded = known
                continue
            }

            try {
                if (!folderReady) {
                    client.ensureFolder(remoteFolder)
                    folderReady = true
                }
                client.upload(remoteFolder, name, file.length()) {
                    context.contentResolver.openInputStream(file.uri)
                        ?: throw IOException("нет доступа к файлу $name")
                }
                known += key
                remoteNames += name
                sent++
            } catch (e: IOException) {
                failed++
                lastError = "$name: ${e.message}"
            }
            prefs.uploaded = known
        }

        if (books.isEmpty()) {
            return finish(prefs, true, "В папке нет книг (epub, txt, md, xtc)")
        }
        val message = buildString {
            append("Отправлено: $sent, уже были на читалке: $alreadyThere")
            if (failed > 0) append(", ошибок: $failed ($lastError)")
        }
        return finish(prefs, failed == 0, message)
    }

    private fun isBook(name: String?): Boolean {
        if (name == null || name.startsWith(".")) return false
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in BOOK_EXTENSIONS
    }

    private fun finish(prefs: Prefs, ok: Boolean, message: String): SyncOutcome {
        val time = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
        val text = "$time: $message"
        prefs.lastStatus = text
        return SyncOutcome(ok, text)
    }
}
