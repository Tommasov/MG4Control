package com.mg4.control.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Télécharge un fichier APK depuis une URL vers un fichier local.
 * Gère les redirections HTTP/HTTPS (GitHub CDN).
 * La progression (0–100) est remontée via [onProgress] sur le thread appelant.
 */
object ApkDownloader {

    suspend fun download(
        url: String,
        destFile: File,
        onProgress: suspend (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Supprimer un éventuel fichier précédent
            if (destFile.exists()) destFile.delete()

            val conn = openConnection(url)
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext false
            }

            val totalBytes = conn.contentLengthLong
            var downloaded = 0L

            conn.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8_192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = (downloaded * 100L / totalBytes).toInt()
                            withContext(Dispatchers.Main) { onProgress(pct) }
                        }
                    }
                }
            }

            conn.disconnect()
            destFile.exists() && destFile.length() > 0

        } catch (_: Exception) {
            destFile.delete()
            false
        }
    }

    /** Ouvre une connexion en suivant manuellement les redirections (GitHub → CDN). */
    private fun openConnection(urlStr: String, depth: Int = 0): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MG4Control-Android")
            connectTimeout = 15_000
            readTimeout    = 120_000
            connect()
        }
        // Fallback de suivi de redirect manuel (au cas où instanceFollowRedirects est ignoré)
        val code = conn.responseCode
        if ((code == 301 || code == 302 || code == 303) && depth < 5) {
            val location = conn.getHeaderField("Location") ?: return conn
            conn.disconnect()
            return openConnection(location, depth + 1)
        }
        return conn
    }
}
