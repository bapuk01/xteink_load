package com.xtink.sync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var readerUrl: EditText
    private lateinit var remoteFolder: EditText
    private lateinit var folderLabel: TextView
    private lateinit var status: TextView
    private lateinit var syncButton: Button

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Provider does not keep permissions; the folder still works until the app restarts.
            }
            prefs.localFolder = uri
            updateFolderLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        readerUrl = findViewById(R.id.readerUrl)
        remoteFolder = findViewById(R.id.remoteFolder)
        folderLabel = findViewById(R.id.folderLabel)
        status = findViewById(R.id.status)
        syncButton = findViewById(R.id.syncNow)

        readerUrl.setText(prefs.readerUrl)
        remoteFolder.setText(prefs.remoteFolder)
        status.text = prefs.lastStatus.ifBlank { getString(R.string.status_idle) }
        updateFolderLabel()

        findViewById<Button>(R.id.pickFolder).setOnClickListener { pickFolder.launch(null) }
        findViewById<Button>(R.id.saveSettings).setOnClickListener {
            saveSettings()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        syncButton.setOnClickListener { syncNow() }

        SyncScheduler.schedulePeriodic(this)
    }

    private fun saveSettings() {
        // Keep only characters valid in an address: keyboards like to add spaces and stray letters.
        val cleanUrl = readerUrl.text.filter { (it.isLetterOrDigit() && it.code < 128) || it in ".:/-_" }.toString()
        if (cleanUrl != readerUrl.text.toString()) readerUrl.setText(cleanUrl)
        prefs.readerUrl = cleanUrl
        prefs.remoteFolder = remoteFolder.text.toString()
    }

    private fun updateFolderLabel() {
        val folder = prefs.localFolder
        folderLabel.text = if (folder != null) {
            getString(R.string.folder_selected, folder.lastPathSegment ?: folder.toString())
        } else {
            getString(R.string.folder_none)
        }
    }

    private fun syncNow() {
        saveSettings()
        syncButton.isEnabled = false
        status.text = getString(R.string.status_running)
        Thread {
            val outcome = Syncer.run(applicationContext)
            runOnUiThread {
                status.text = outcome.message
                syncButton.isEnabled = true
            }
        }.start()
    }
}
