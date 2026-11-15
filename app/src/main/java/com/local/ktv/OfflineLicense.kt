package com.local.ktv

import android.content.Context
import android.os.Environment
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Offline device-bound licensing using only phone-generated short activation codes. */
object OfflineLicense {
    private const val PREFS = "offline_license_v1"
    private const val KEY_LICENSE = "license_code"
    private const val KEY_START = "activation_start_ms"
    private const val KEY_LAST_SEEN = "activation_last_seen_ms"
    private const val KEY_DAYS = "activation_days"
    private const val DAY_MS = 86_400_000L
    private const val TRIAL_DURATION_MS = DAY_MS
    private const val KEY_TRIAL_START = "trial_start_ms"
    private const val KEY_TRIAL_LAST_SEEN = "trial_last_seen_ms"
    private const val TRIAL_SALT = "xiaoxiao-ktv-offline-trial-v1"
    private const val RECORD_SALT = "xiaoxiao-ktv-short-license-record-v1"

    fun trialRemainingMillis(context: Context): Long = runCatching {
        val id = deviceId(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dir = markerDirectory()
        check((dir.exists() || dir.mkdirs()) && dir.isDirectory)
        val marker = File(dir, "trial_" + sha256(id).take(24) + ".json")
        val now = System.currentTimeMillis()
        var start: Long
        var last: Long
        if (marker.exists()) {
            val data = JSONObject(marker.readText(Charsets.UTF_8))
            start = data.getLong("start")
            last = data.getLong("lastSeen")
            require(data.getString("check") == trialCheck(id, start, last))
            if (data.optBoolean("expired", false)) return -1L
        } else {
            start = prefs.getLong(KEY_TRIAL_START, 0L).takeIf { it > 0L } ?: now
            last = prefs.getLong(KEY_TRIAL_LAST_SEEN, start).coerceAtLeast(start)
        }
        if (now < last || now < start || now - start >= TRIAL_DURATION_MS) {
            val expiredLast = maxOf(last, now)
            writeJsonAtomically(marker, JSONObject().put("start", start).put("lastSeen", expiredLast)
                .put("expired", true).put("check", trialCheck(id, start, expiredLast)))
            check(prefs.edit().putLong(KEY_TRIAL_START, start).putLong(KEY_TRIAL_LAST_SEEN, expiredLast).commit())
            return -1L
        }
        writeJsonAtomically(marker, JSONObject().put("start", start).put("lastSeen", now)
            .put("check", trialCheck(id, start, now)))
        check(prefs.edit().putLong(KEY_TRIAL_START, start).putLong(KEY_TRIAL_LAST_SEEN, now).commit())
        (TRIAL_DURATION_MS - (now - start)).coerceAtLeast(0L)
    }.getOrDefault(-1L)

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }?.lowercase()
            ?: error("无法读取设备标识，请联系软件提供方")

    fun machineCode(context: Context): String =
        sha256(deviceId(context)).take(12).uppercase().chunked(2).joinToString(":")

    private fun machineRaw(context: Context) = sha256(deviceId(context)).take(12).uppercase()

    fun isActivated(context: Context): Boolean {
        val code = savedCode(context) ?: return false
        val valid = ShortLicenseCodec.verify(machineRaw(context), code) ?: return false
        val record = loadRecord(context, code) ?: return false
        val start = record.optLong("start", 0L)
        val last = record.optLong("lastSeen", 0L)
        val days = record.optInt("days", -1)
        if (start <= 0L || last < start || days != valid.days) return false
        if (record.optString("check") != activationCheck(machineRaw(context), code, start, last, days)) return false
        val now = System.currentTimeMillis()
        if (now < last || now < start || (days > 0 && now - start >= days * DAY_MS)) return false
        if (now - last >= 60_000L) persistRecord(context, code, start, now, days)
        return true
    }

    /** null means perpetual/no timed short-code license; otherwise remaining milliseconds. */
    fun licenseRemainingMillis(context: Context): Long? {
        val code = savedCode(context) ?: return null
        val valid = ShortLicenseCodec.verify(machineRaw(context), code) ?: return null
        if (valid.days == 0) return null
        val record = loadRecord(context, code) ?: return 0L
        val start = record.optLong("start", 0L)
        val last = record.optLong("lastSeen", 0L)
        val days = record.optInt("days", -1)
        if (start <= 0L || last < start || days != valid.days ||
            record.optString("check") != activationCheck(machineRaw(context), code, start, last, days)) return 0L
        val now = System.currentTimeMillis()
        if (now < last || now < start) return 0L
        return (days.toLong() * DAY_MS - (now - start)).coerceAtLeast(0L)
    }

    /** Term begins on the first successful TV activation. Re-entering the same code won't reset it. */
    fun activate(context: Context, input: String): Boolean {
        val inputCode = input.trim()
        if (inputCode.isEmpty()) return false
        val code = ShortLicenseCodec.normalizeActivationCode(inputCode) ?: return false
        val valid = ShortLicenseCodec.verify(machineRaw(context), code) ?: return false
        if (savedCode(context) == code) return isActivated(context)
        val now = System.currentTimeMillis()
        persistRecord(context, code, now, now, valid.days)
        return isActivated(context)
    }


    private fun savedCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_LICENSE, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val record = activationFile(context)?.let {
            runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull()
        } ?: return null
        val code = record.optString("code").takeIf { it.isNotBlank() } ?: return null
        prefs.edit().putString(KEY_LICENSE, code).putLong(KEY_START, record.optLong("start"))
            .putLong(KEY_LAST_SEEN, record.optLong("lastSeen")).putInt(KEY_DAYS, record.optInt("days", -1)).commit()
        return code
    }

    private fun loadRecord(context: Context, code: String): JSONObject? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_START, 0L)
        val days = prefs.getInt(KEY_DAYS, -1)
        if (start > 0L && prefs.getString(KEY_LICENSE, null) == code && days >= 0) {
            val last = prefs.getLong(KEY_LAST_SEEN, start)
            return JSONObject().put("code", code).put("start", start).put("lastSeen", last).put("days", days)
                .put("check", activationCheck(machineRaw(context), code, start, last, days))
        }
        val fileRecord = activationFile(context)?.let {
            runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull()
        } ?: return null
        if (fileRecord.optString("code") != code) return null
        prefs.edit().putString(KEY_LICENSE, code).putLong(KEY_START, fileRecord.optLong("start"))
            .putLong(KEY_LAST_SEEN, fileRecord.optLong("lastSeen"))
            .putInt(KEY_DAYS, fileRecord.optInt("days", -1)).commit()
        return fileRecord
    }

    private fun persistRecord(context: Context, code: String, start: Long, last: Long, days: Int) {
        val data = JSONObject().put("code", code).put("start", start).put("lastSeen", last).put("days", days)
            .put("check", activationCheck(machineRaw(context), code, start, last, days))
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LICENSE, code).putLong(KEY_START, start).putLong(KEY_LAST_SEEN, last)
            .putInt(KEY_DAYS, days).commit())
        activationFile(context)?.let { runCatching { writeJsonAtomically(it, data) } }
    }

    private fun activationFile(context: Context): File? = runCatching {
        val dir = markerDirectory()
        check((dir.exists() || dir.mkdirs()) && dir.isDirectory)
        File(dir, "activation_" + sha256(deviceId(context)).take(24) + ".json")
    }.getOrNull()

    private fun markerDirectory() = File(Environment.getExternalStorageDirectory(), ".xiaoxiao_ktv")

    private fun writeJsonAtomically(file: File, data: JSONObject) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(data.toString(), Charsets.UTF_8)
        if (file.exists()) check(file.delete())
        check(temp.renameTo(file))
    }

    private fun activationCheck(machine: String, code: String, start: Long, last: Long, days: Int): String =
        sha256("$RECORD_SALT|${ShortLicenseCodec.keyFingerprint()}|$machine|$code|$start|$last|$days")

    private fun trialCheck(id: String, start: Long, last: Long): String =
        sha256("$TRIAL_SALT|$id|$start|$last")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
