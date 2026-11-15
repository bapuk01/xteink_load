package com.xtink.sync

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** Small wrapper around SharedPreferences for the app settings and the list of already sent books. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("xtink_sync", Context.MODE_PRIVATE)

    /** Reader address as shown on its screen, e.g. http://192.168.1.102 */
    var readerUrl: String
        get() = sp.getString(KEY_READER_URL, "").orEmpty()
        set(value) = sp.edit { putString(KEY_READER_URL, value.trim()) }

    /** Folder on the reader's SD card where books go, e.g. /Books */
    var remoteFolder: String
        get() = sp.getString(KEY_REMOTE_FOLDER, DEFAULT_REMOTE_FOLDER).orEmpty().trim().ifBlank { DEFAULT_REMOTE_FOLDER }
        set(value) = sp.edit { putString(KEY_REMOTE_FOLDER, value.trim()) }

    /** SAF tree URI of the folder on the phone that is watched for books. */
    var localFolder: Uri?
        get() = sp.getString(KEY_LOCAL_FOLDER, null)?.let(Uri::parse)
        set(value) = sp.edit { putString(KEY_LOCAL_FOLDER, value?.toString()) }

    /** Keys "name|size" of books that were already sent (or were already on the reader). */
    var uploaded: Set<String>
        get() = sp.getStringSet(KEY_UPLOADED, emptySet()).orEmpty().toSet()
        set(value) = sp.edit { putStringSet(KEY_UPLOADED, HashSet(value)) }

    /** Human-readable result of the last sync, shown in the UI. */
    var lastStatus: String
        get() = sp.getString(KEY_LAST_STATUS, "").orEmpty()
        set(value) = sp.edit { putString(KEY_LAST_STATUS, value) }

    companion object {
        const val DEFAULT_REMOTE_FOLDER = "/Books"
        private const val KEY_READER_URL = "reader_url"
        private const val KEY_REMOTE_FOLDER = "remote_folder"
        private const val KEY_LOCAL_FOLDER = "local_folder"
        private const val KEY_UPLOADED = "uploaded"
        private const val KEY_LAST_STATUS = "last_status"
    }
}
