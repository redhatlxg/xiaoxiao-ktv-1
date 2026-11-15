package com.local.ktv.issuer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Compact offline code: duration(2B) + nonce(2B) + HMAC-SHA256 tag(6B). */
object ShortLicenseCodec {
    private const val SECRET_HEX = "80dd9ab61f688aeee3de2caaf3fc67064ef3a17202ca576ce76fbe92f74e4c69"
    private const val PRODUCT = "xiaoxiao-ktv-short-v1"
    private val secret = hexToBytes(SECRET_HEX)
    private val random = SecureRandom()
    data class VerifiedCode(val days: Int, val nonce: String)

    fun normalizeMachineCode(input: String): String? {
        val value = input.trim().uppercase().replace(":", "").replace("-", "").replace(" ", "")
        return value.takeIf { it.matches(Regex("[0-9A-F]{12}")) }
    }
    fun normalizeActivationCode(input: String): String? {
        val value = input.trim().uppercase().replace(":", "").replace("-", "").replace(" ", "")
        return value.takeIf { it.matches(Regex("[0-9A-F]{20}")) }?.chunked(2)?.joinToString(":")
    }
    fun issue(machineCode: String, days: Int): String {
        require(days in 0..65535) { "激活天数必须为 0（永久）或 1–65535 天" }
        val machine = normalizeMachineCode(machineCode) ?: error("机器码应为 12 位十六进制字符")
        val nonceBytes = ByteArray(2).also { random.nextBytes(it) }
        val nonce = nonceBytes.toHex()
        val tag = hmac("$PRODUCT|$machine|$days|$nonce").copyOf(6)
        val duration = byteArrayOf((days ushr 8).toByte(), days.toByte())
        return (duration + nonceBytes + tag).toHex().chunked(2).joinToString(":")
    }
    fun verify(machineCode: String, input: String): VerifiedCode? = runCatching {
        val machine = normalizeMachineCode(machineCode) ?: return null
        val normalized = normalizeActivationCode(input) ?: return null
        val bytes = hexToBytes(normalized.replace(":", ""))
        val days = ((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
        val nonce = bytes.sliceArray(2..3).toHex()
        val supplied = bytes.sliceArray(4..9)
        val expected = hmac("$PRODUCT|$machine|$days|$nonce").copyOf(6)
        if (!MessageDigest.isEqual(supplied, expected)) return null
        VerifiedCode(days, nonce)
    }.getOrNull()
    fun keyFingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(secret).take(8).toByteArray().toHex()
    private fun hmac(message: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
    }
    private fun hexToBytes(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 255) }
}
