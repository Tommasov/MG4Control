package com.mg4.control.util

import android.content.Context

/**
 * Détecte la génération de firmware à partir de ro.build.mt2712.version.
 *
 * SWI133 : "SWI133-29176-1300R32" — ADAS via getMixProperty(0x32), 5 modes, 2 alertes
 * SWI68  : "SWI68-xxxxx-xxxxx"   — ADAS via getIntProperty(0x83a), 3 modes, 1 alerte
 * UNKNOWN : firmware non reconnu — l'utilisateur peut forcer un mode de compatibilité
 */
object FirmwareInfo {

    enum class Gen { SWI133, SWI68, UNKNOWN }

    private const val PREF_NAME       = "mg4_settings"
    private const val PREF_FORCED_GEN = "forced_firmware_gen"

    @Volatile private var cached:         Gen?    = null
    @Volatile private var detectedString: String? = null

    /**
     * À appeler en premier dans MainActivity.onCreate(), avant toute inflation de fragment.
     * Lit le choix forcé éventuel depuis les SharedPreferences et l'applique au cache.
     */
    fun initWithContext(context: Context) {
        val forced = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_FORCED_GEN, null) ?: return
        cached = runCatching { Gen.valueOf(forced) }.getOrDefault(Gen.UNKNOWN)
    }

    /** Retourne la génération active (forcée ou détectée automatiquement). */
    fun getGeneration(): Gen {
        cached?.let { return it }
        val version = readProp("ro.build.mt2712.version")
            ?: readProp("ro.build.version.incremental")
        detectedString = version
        val gen = when {
            version == null              -> Gen.UNKNOWN
            version.startsWith("SWI133") -> Gen.SWI133
            version.startsWith("SWI68")  -> Gen.SWI68
            else                         -> Gen.UNKNOWN
        }
        cached = gen
        return gen
    }

    /**
     * Chaîne brute lue depuis les propriétés système (ex: "SWI69-12345-xxx").
     * Utile pour afficher la version exacte à l'utilisateur dans le dialog de warning.
     */
    fun getDetectedString(): String {
        if (detectedString == null) getGeneration() // force la lecture
        return detectedString ?: "?"
    }

    /**
     * Force le mode de compatibilité manuellement.
     * Le choix est persisté dans les SharedPreferences et survit aux redémarrages.
     */
    fun forceGeneration(context: Context, gen: Gen) {
        cached = gen
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_FORCED_GEN, gen.name).apply()
    }

    /**
     * Retourne true si le mode de compatibilité a été forcé manuellement
     * (par opposition à une détection automatique réussie).
     */
    fun isForced(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .contains(PREF_FORCED_GEN)

    private fun readProp(key: String): String? = try {
        val sp  = Class.forName("android.os.SystemProperties")
        val get = sp.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String)?.takeIf { it.isNotBlank() && it != "0" }
    } catch (_: Exception) { null }
}
