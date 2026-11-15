package com.local.ktv

import android.content.Context
import android.os.Environment
import java.io.File
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.MessageDigest

/**
 * Fully offline RSA-SHA256 license verifier.
 * The private key must only exist in the seller's offline registration tool.
 */
object OfflineLicense {
    private const val PREFS = "offline_license_v1"
    private const val KEY_LICENSE = "license_code"
    private const val PRODUCT = "xiaoxiao-ktv"
    private const val TRIAL_DURATION_MS = 24L * 60L * 60L * 1000L
    private const val KEY_TRIAL_START = "trial_start_ms"
    private const val KEY_TRIAL_LAST_SEEN = "trial_last_seen_ms"
    private const val TRIAL_SALT = "xiaoxiao-ktv-offline-trial-v1"

    /**
     * Returns remaining trial time. A persistent marker in shared public storage survives
     * ordinary uninstall/reinstall; SharedPreferences is a secondary copy. Fail closed if
     * the marker is corrupt or storage cannot be read/written.
     */
    fun trialRemainingMillis(context: Context): Long {
        return runCatching {
            val id = deviceId(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val markerDir = File(Environment.getExternalStorageDirectory(), ".xiaoxiao_ktv")
            check((markerDir.exists() || markerDir.mkdirs()) && markerDir.isDirectory)
            val marker = File(markerDir, "trial_" + sha256(id).take(24) + ".json")
            val now = System.currentTimeMillis()
            var start: Long
            var lastSeen: Long

            if (marker.exists()) {
                val data = JSONObject(marker.readText(Charsets.UTF_8))
                start = data.getLong("start")
                lastSeen = data.getLong("lastSeen")
                require(data.getString("check") == trialCheck(id, start, lastSeen))
            } else {
                // Preserve an existing trial when upgrading from a build that already
                // stored its first-run timestamp; otherwise start the 24-hour clock now.
                start = prefs.getLong(KEY_TRIAL_START, 0L).takeIf { it > 0L } ?: now
                lastSeen = prefs.getLong(KEY_TRIAL_LAST_SEEN, start).coerceAtLeast(start)
            }

            // Clock rollback is treated as expiration rather than granting extra time.
            if (now < lastSeen || now < start || now - start >= TRIAL_DURATION_MS) return -1L
            lastSeen = now
            val data = JSONObject()
                .put("start", start)
                .put("lastSeen", lastSeen)
                .put("check", trialCheck(id, start, lastSeen))
            val temp = File(markerDir, marker.name + ".tmp")
            temp.writeText(data.toString(), Charsets.UTF_8)
            if (marker.exists()) marker.delete()
            check(temp.renameTo(marker))
            check(prefs.edit()
                .putLong(KEY_TRIAL_START, start)
                .putLong(KEY_TRIAL_LAST_SEEN, lastSeen)
                .commit())
            (TRIAL_DURATION_MS - (now - start)).coerceAtLeast(0L)
        }.getOrDefault(-1L)
    }

    private fun trialCheck(id: String, start: Long, lastSeen: Long): String =
        sha256("$TRIAL_SALT|$id|$start|$lastSeen")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun deviceId(context: Context): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: error("无法读取设备标识，请联系软件提供方")
        return raw.lowercase()
    }

    fun machineCode(context: Context): String =
        deviceId(context).chunked(4).joinToString("-").uppercase()

    fun isActivated(context: Context): Boolean =
        verify(context, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LICENSE, null))

    fun activate(context: Context, input: String): Boolean {
        val code = input.trim().replace("\\s".toRegex(), "")
        if (!verify(context, code)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LICENSE, code).commit()
        return isActivated(context)
    }

    private fun verify(context: Context, code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        return runCatching {
            val parts = code.split(".")
            require(parts.size == 2)
            val decoder = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            val payloadBytes = Base64.decode(parts[0], decoder)
            val signatureBytes = Base64.decode(parts[1], decoder)
            val publicKeyText = BuildConfig.KTV_LICENSE_PUBLIC_KEY_B64
            require(publicKeyText.isNotBlank()) { "license public key not configured" }
            val publicKeyBytes = Base64.decode(publicKeyText, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(payloadBytes)
            require(verifier.verify(signatureBytes))
            val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
            payload.getString("product") == PRODUCT &&
                payload.getString("deviceId") == deviceId(context) &&
                payload.optBoolean("perpetual", false)
        }.getOrDefault(false)
    }
}
