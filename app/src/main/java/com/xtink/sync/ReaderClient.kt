package com.xtink.sync

import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Minimal client for the CrossPoint Reader web server (File Transfer / Join Network mode).
 * Endpoints used: GET /api/files?path=... and POST /upload?path=... (multipart, field "file").
 */
class ReaderClient(baseUrl: String) {

    // Accept "192.168.1.47" as well as "http://192.168.1.47/": add the scheme if the user left it out.
    private val base = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) it
        else "http://$it"
    }

    private fun connect(pathAndQuery: String, readTimeoutMs: Int): HttpURLConnection {
        val conn = URL(base + pathAndQuery).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = readTimeoutMs
        return conn
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** Names of the files (not folders) that already exist in [folder] on the reader. */
    fun listFileNames(folder: String): MutableSet<String> {
        val conn = connect("/api/files?path=" + encode(folder), 10_000)
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("список файлов на читалке: HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val names = HashSet<String>()
            try {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    if (!item.optBoolean("isDirectory", false)) {
                        names.add(item.getString("name"))
                    }
                }
            } catch (e: JSONException) {
                throw IOException("неожиданный ответ читалки", e)
            }
            return names
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Creates [folder] (and its parents) on the reader if it is missing; the reader fails uploads into
     * folders that do not exist. Best effort: POST /mkdir on an existing folder just returns an error.
     */
    fun ensureFolder(folder: String) {
        var parent = "/"
        for (part in folder.split('/').filter { it.isNotBlank() }) {
            val conn = connect("/mkdir", 10_000)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            try {
                conn.outputStream.use {
                    it.write("name=${encode(part)}&path=${encode(parent)}".toByteArray(StandardCharsets.UTF_8))
                }
                conn.responseCode // result ignored on purpose
            } finally {
                conn.disconnect()
            }
            parent = if (parent == "/") "/$part" else "$parent/$part"
        }
    }

    /**
     * Uploads one file into [folder] on the reader.
     * [openStream] is called once to get the file content. Throws IOException on failure.
     */
    fun upload(folder: String, fileName: String, length: Long, openStream: () -> InputStream) {
        val boundary = "XtinkBoundary" + UUID.randomUUID().toString().replace("-", "")
        val safeName = fileName.replace("\"", "_").replace("\r", "").replace("\n", "")
        val head = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)

        val conn = connect("/upload?path=" + encode(folder), 120_000)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.useCaches = false
        // The reader's firmware does not cope with chunked bodies: send an exact Content-Length like curl does.
        conn.setFixedLengthStreamingMode(head.size + length + tail.size)
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        try {
            conn.outputStream.use { out ->
                out.write(head)
                openStream().use { input -> input.copyTo(out, 4 * 1024) }
                out.write(tail)
            }
            val code = conn.responseCode
            if (code in 200..299) return
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // The reader refuses to overwrite; treat "already there" as success.
            if (code == 400 && body.contains("already exists", ignoreCase = true)) return
            throw IOException("загрузка на читалку: HTTP $code $body")
        } finally {
            conn.disconnect()
        }
    }
}
