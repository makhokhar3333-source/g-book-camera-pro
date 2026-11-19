package com.example.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Silent background Live-OTA synchronizer connected to g-book-live.
 * Caches remote web bundle updates locally when online without ever opening external browsers
 * and preserves 100% offline fallback at all times.
 */
object LiveOtaSync {
    private const val OTA_LIVE_URL = "https://makhokhar3333-source.github.io/g-book-live/index.html"
    private const val PREFS_NAME = "gbook_ota_prefs"
    private const val KEY_OTA_HASH = "ota_sha256"
    private const val KEY_OTA_SYNC_MS = "ota_last_sync_ms"
    private const val CACHE_FILE_NAME = "gbook_live_ota.html"

    suspend fun syncSilently(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cacheBustUrl = "$OTA_LIVE_URL?t=${System.currentTimeMillis()}"
            val conn = (URL(cacheBustUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 5000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            }

            try {
                if (conn.responseCode !in 200..299) return@withContext false
                val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (!html.contains("G-Book Camera") || !html.contains("</html>")) {
                    return@withContext false
                }

                val hash = sha256(html)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val cachedFile = File(context.filesDir, CACHE_FILE_NAME)
                if (prefs.getString(KEY_OTA_HASH, null) != hash || !cachedFile.exists()) {
                    cachedFile.writeText(html, Charsets.UTF_8)
                    prefs.edit()
                        .putString(KEY_OTA_HASH, hash)
                        .putLong(KEY_OTA_SYNC_MS, System.currentTimeMillis())
                        .apply()
                }
                true
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    fun getCachedOtaHtml(context: Context): String? {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        return if (file.exists() && file.length() > 0L) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
