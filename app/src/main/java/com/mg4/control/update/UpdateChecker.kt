package com.mg4.control.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vérifie sur l'API GitHub Releases si une version plus récente est disponible.
 * L'appel est asynchrone ; le callback [onUpdateAvailable] est exécuté sur le main thread.
 */
object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/SliDeeN/MG4Control/releases/latest"

    fun check(
        context: Context,
        onUpdateAvailable: (UpdateInfo) -> Unit,
        onNoUpdate: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Version actuellement installée
                val currentVersion = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName
                    ?: return@launch

                // Requête GitHub API
                val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "MG4Control-Android")
                    connectTimeout = 10_000
                    readTimeout    = 10_000
                }

                if (conn.responseCode != 200) return@launch

                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()

                val tagName     = json.getString("tag_name")           // "v2.1"
                val versionName = tagName.trimStart('v')                // "2.1"
                val releaseNotes = json.optString("body", "").take(400)

                // Cherche l'asset .apk dans la release
                val assets = json.optJSONArray("assets") ?: return@launch
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isEmpty()) return@launch

                // Compare uniquement si version distante > version locale
                if (isNewer(versionName, currentVersion)) {
                    val info = UpdateInfo(versionName, tagName, apkUrl, releaseNotes)
                    withContext(Dispatchers.Main) { onUpdateAvailable(info) }
                } else {
                    // Check réussi, application déjà à jour
                    withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
                }

            } catch (_: Exception) {
                // Pas de réseau, timeout, JSON malformé → callback onError
                withContext(Dispatchers.Main) { onError?.invoke() }
            }
        }
    }

    /**
     * Retourne true si [remote] est une version supérieure à [current].
     * Comparaison sémantique segment par segment (ex: "2.1.3" > "2.1.2").
     */
    private fun isNewer(remote: String, current: String): Boolean {
        fun segments(v: String) =
            v.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }

        val r = segments(remote)
        val c = segments(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false // versions identiques
    }
}
