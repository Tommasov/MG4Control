package com.mg4.control.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installe un APK via deux méthodes en cascade, adaptées aux contraintes AAOS :
 *
 *   1. pm install depuis le stockage externe  (getExternalFilesDir — accessible par pm)
 *   2. pm install depuis le stockage interne  (cacheDir — fallback si pas de stockage externe)
 *
 * Note :
 *   - PackageInstaller (API silencieuse) : bloqué par AAOS ("non-system apps on internal storage")
 *   - ACTION_INSTALL_PACKAGE : bloqué par le sharedUserId="android.uid.system" de l'app
 *     (le système refuse de mettre à jour une app système via l'installeur standard)
 *   - Seul pm install depuis un chemin lisible par le processus pm peut fonctionner
 *
 * Retourne null si une méthode a pu être lancée, message d'erreur sinon.
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"
    // Nom différent du fichier téléchargé pour éviter toute collision source/dest
    private const val PM_TMP_NAME = "mg4control_pm_tmp.apk"

    fun install(context: Context, apkFile: File): String? {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return "APK manquant ou vide : ${apkFile.absolutePath}"
        }

        // ── Stratégie 1 : pm install depuis le stockage externe ───────────────
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            val extApk = File(extDir, PM_TMP_NAME)
            val error = tryPmInstall(apkFile, extApk)
            if (error == null) {
                Log.i(TAG, "pm install (externe) : succès")
                return null
            }
            Log.w(TAG, "pm install (externe) échoué : $error")
        }

        // ── Stratégie 2 : pm install depuis le stockage interne (cache) ───────
        val intApk = File(context.cacheDir, PM_TMP_NAME)
        val error = tryPmInstall(apkFile, intApk)
        if (error == null) {
            Log.i(TAG, "pm install (interne) : succès")
            return null
        }
        Log.w(TAG, "pm install (interne) échoué : $error")

        val extMsg = if (extDir != null) "ext: échec" else "ext: absent"
        return "$extMsg | int: $error"
    }

    // ── pm install -r ─────────────────────────────────────────────────────────

    private fun tryPmInstall(source: File, dest: File): String? {
        return try {
            // Copie vers la destination (nom différent → jamais la même que source)
            source.copyTo(dest, overwrite = true)

            val process = ProcessBuilder(
                "/system/bin/pm", "install", "-r", "--user", "0", dest.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            dest.runCatching { delete() }
            Log.d(TAG, "pm install [${dest.parent}] exitCode=$exitCode output=$output")

            if (output.contains("Success", ignoreCase = true) || exitCode == 0) {
                null
            } else {
                "exitCode=$exitCode | $output"
            }
        } catch (e: Exception) {
            dest.runCatching { delete() }
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
