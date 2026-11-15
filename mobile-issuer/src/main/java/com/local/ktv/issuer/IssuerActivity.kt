package com.local.ktv.issuer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Offline mobile issuer. Import the existing desktop PEM + public_key.b64 once.
 * The private key is encrypted at rest with an Android Keystore AES-GCM key.
 * No network permission is declared; the signing key never leaves this device.
 */
class IssuerActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("issuer_secure_v1", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var machine: EditText
    private lateinit var output: EditText
    private var pendingImport = 0 // 1 private PEM, 2 public DER-base64

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        refreshStatus()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "晓晓呀 KTV\n手机离线授权助手"
            textSize = 25f
            setTextColor(0xFF172033.toInt())
        })
        root.addView(TextView(this).apply {
            text = "无需联网 · 一机一码 · 永久授权"
            textSize = 14f
            setPadding(0, dp(6), 0, dp(16))
            setTextColor(0xFF526078.toInt())
        })
        status = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFEAF0F8.toInt())
        }
        root.addView(status, matchWrap())

        addButton(root, "首次生成授权密钥（仅首次使用）") { generateKeyPair() }
        addButton(root, "导出公钥（用于正式版配置）") { exportPublicKey() }
        addButton(root, "导入电脑注册机私钥（private_key.pem）") { pickFile(1) }
        addButton(root, "导入对应公钥（public_key.b64）") { pickFile(2) }
        addButton(root, "创建加密私钥备份") { backupPrivateKey() }
        addButton(root, "从加密备份恢复私钥") { pickFile(3) }
        root.addView(TextView(this).apply {
            text = "首次设置：把电脑注册机目录中的这两个文件导入手机。导入后，私钥会用 Android 系统密钥库加密保存在本机。"
            textSize = 13f
            setTextColor(0xFF596579.toInt())
            setPadding(0, dp(8), 0, dp(14))
        })

        root.addView(TextView(this).apply { text = "客户电视设备机器码"; textSize = 16f })
        machine = EditText(this).apply {
            hint = "粘贴电视激活页面显示的机器码"
            isSingleLine = false
            minLines = 2
            textSize = 15f
        }
        root.addView(machine, matchWrap())

        addButton(root, "生成永久授权码") { issue() }
        root.addView(TextView(this).apply {
            text = "生成的授权码"
            textSize = 16f
            setPadding(0, dp(12), 0, dp(4))
        })
        output = EditText(this).apply {
            hint = "授权码会显示在这里"
            minLines = 4
            textSize = 12f
            setTextIsSelectable(true)
        }
        root.addView(output, matchWrap())
        addButton(root, "复制授权码") { copyToken() }
        addButton(root, "分享授权码") { shareToken() }
        addButton(root, "查看最近签发记录") { showHistory() }

        root.addView(TextView(this).apply {
            text = "安全提醒：请勿把 private_key.pem 发给客户或上传网盘/GitHub。手机丢失、重置或卸载应用前，务必保留电脑上的原始私钥备份。"
            textSize = 12f
            setTextColor(0xFFB23B35.toInt())
            setPadding(0, dp(18), 0, 0)
        })
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(button, matchWrap())
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun generateKeyPair() {
        try {
            require(!prefs.contains("private_ct") && !prefs.contains("public_der")) {
                "本机已经设置过密钥，不能重新生成，以免旧授权失效"
            }
            val generator = java.security.KeyPairGenerator.getInstance("RSA")
            generator.initialize(3072, SecureRandom())
            val pair = generator.generateKeyPair()
            val encrypted = encryptPrivate(pair.private.encoded)
            prefs.edit()
                .putString("private_iv", Base64.encodeToString(encrypted.first, Base64.NO_WRAP))
                .putString("private_ct", Base64.encodeToString(encrypted.second, Base64.NO_WRAP))
                .putString("public_der", Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
                .apply()
            refreshStatus()
            AlertDialog.Builder(this)
                .setTitle("密钥生成成功")
                .setMessage("已在本机生成 RSA-3072 密钥。私钥已由 Android 系统密钥库加密保存。请立即导出并妥善保存公钥，再配置到正式版构建环境。卸载或重置手机可能导致私钥无法恢复。")
                .setPositiveButton("导出公钥") { _, _ -> exportPublicKey() }
                .setNegativeButton("稍后", null)
                .show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("生成失败")
                .setMessage(e.message ?: "发生未知错误").setPositiveButton("确定", null).show()
        }
    }

    private fun exportPublicKey() {
        val pub = prefs.getString("public_der", null)
        if (pub.isNullOrBlank()) {
            Toast.makeText(this, "请先生成或导入公钥", Toast.LENGTH_SHORT).show()
            return
        }
        val text = Base64.encodeToString(Base64.decode(pub, Base64.DEFAULT), Base64.NO_WRAP)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "安全保存授权公钥"))
    }

    private fun pickFile(kind: Int) {
        pendingImport = kind
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 701)
    }

    @Deprecated("Uses the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 701 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取所选文件")
            if (pendingImport == 1) {
                val pem = String(bytes, Charsets.US_ASCII)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), "")
                val der = Base64.decode(pem, Base64.DEFAULT)
                val privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(der))
                val encrypted = encryptPrivate(der)
                prefs.edit()
                    .putString("private_iv", Base64.encodeToString(encrypted.first, Base64.NO_WRAP))
                    .putString("private_ct", Base64.encodeToString(encrypted.second, Base64.NO_WRAP))
                    .apply()
                // Validate key encoding before reporting success; pair check happens when public key arrives.
                privateKey.algorithm
                Toast.makeText(this, "私钥已加密导入本机", Toast.LENGTH_LONG).show()
            } else if (pendingImport == 2) {
                val text = String(bytes, Charsets.US_ASCII).trim()
                val pub = Base64.decode(text, Base64.DEFAULT)
                KeyFactory.getInstance("RSA").generatePublic(
                    java.security.spec.X509EncodedKeySpec(pub)
                )
                prefs.edit().putString("public_der", Base64.encodeToString(pub, Base64.NO_WRAP)).apply()
                Toast.makeText(this, "公钥已导入", Toast.LENGTH_SHORT).show()
            } else if (pendingImport == 3) {
                val backup = JSONObject(String(bytes, Charsets.UTF_8))
                require(backup.optInt("version") == 1) { "不支持的备份文件版本" }
                val salt = Base64.decode(backup.getString("salt"), Base64.DEFAULT)
                val iv = Base64.decode(backup.getString("iv"), Base64.DEFAULT)
                val encrypted = Base64.decode(backup.getString("ciphertext"), Base64.DEFAULT)
                promptBackupPassword("输入备份密码以恢复") { password ->
                    try {
                        val privateDer = decryptBackup(encrypted, salt, iv, password)
                        val publicDer = Base64.decode(backup.getString("publicKey"), Base64.DEFAULT)
                        verifyKeyPair(privateDer, publicDer)
                        val localEncrypted = encryptPrivate(privateDer)
                        prefs.edit()
                            .putString("private_iv", Base64.encodeToString(localEncrypted.first, Base64.NO_WRAP))
                            .putString("private_ct", Base64.encodeToString(localEncrypted.second, Base64.NO_WRAP))
                            .putString("public_der", Base64.encodeToString(publicDer, Base64.NO_WRAP))
                            .apply()
                        refreshStatus()
                        Toast.makeText(this, "密钥备份恢复成功", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        AlertDialog.Builder(this).setTitle("恢复失败")
                            .setMessage(e.message ?: "备份文件或密码不正确")
                            .setPositiveButton("确定", null).show()
                    }
                }
                return
            }
            validatePairIfReady()
            refreshStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Portable backup: PBKDF2-HMAC-SHA256 + AES-256-GCM. The password is never stored.
    private fun backupPrivateKey() {
        if (!prefs.contains("private_ct") || !prefs.contains("public_der")) {
            Toast.makeText(this, "请先生成或导入完整密钥", Toast.LENGTH_SHORT).show(); return
        }
        promptBackupPassword("设置备份密码（至少 10 位）") { password ->
            try {
                validatePairIfReady()
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, backupKey(password, salt), GCMParameterSpec(128, iv))
                val ciphertext = cipher.doFinal(decryptPrivate())
                val backup = JSONObject()
                    .put("format", "xiaoxiao-ktv-encrypted-key-backup")
                    .put("version", 1)
                    .put("kdf", "PBKDF2WithHmacSHA256")
                    .put("iterations", 210000)
                    .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .put("publicKey", prefs.getString("public_der", ""))
                    .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .toString(2)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, backup)
                }, "保存加密密钥备份文件"))
            } catch (e: Exception) {
                AlertDialog.Builder(this).setTitle("备份失败")
                    .setMessage(e.message ?: "未知错误").setPositiveButton("确定", null).show()
            }
        }
    }

    private fun promptBackupPassword(title: String, onPassword: (CharArray) -> Unit) {
        val field = EditText(this).apply {
            hint = "备份密码；请离线妥善记住"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("取消", null)
            .setPositiveButton("继续") { _, _ ->
                val password = field.text.toString().toCharArray()
                if (password.size < 10) {
                    Toast.makeText(this, "密码至少需要 10 位", Toast.LENGTH_LONG).show()
                } else onPassword(password)
            }.show()
    }

    private fun backupKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, 210000, 256)
        return try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(raw, "AES")
        } finally { spec.clearPassword(); password.fill(0.toChar()) }
    }

    private fun decryptBackup(ciphertext: ByteArray, salt: ByteArray, iv: ByteArray, password: CharArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, backupKey(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun verifyKeyPair(privateDer: ByteArray, publicDer: ByteArray) {
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateDer))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(publicDer))
        val challenge = "xiaoxiao-ktv-key-pair-check".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey); signer.update(challenge)
        val signature = signer.sign()
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey); verifier.update(challenge)
        require(verifier.verify(signature)) { "备份中的私钥和公钥不匹配" }
    }

    private fun encryptPrivate(der: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateAesKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv to cipher.doFinal(der)
    }

    private fun decryptPrivate(): ByteArray {
        val iv = Base64.decode(prefs.getString("private_iv", "")!!, Base64.DEFAULT)
        val ct = Base64.decode(prefs.getString("private_ct", "")!!, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun getOrCreateAesKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("xiaoxiao_issuer_aes", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "xiaoxiao_issuer_aes",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun validatePairIfReady() {
        val publicText = prefs.getString("public_der", null) ?: return
        val privateDer = decryptPrivate()
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateDer))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.X509EncodedKeySpec(Base64.decode(publicText, Base64.DEFAULT))
        )
        val challenge = "xiaoxiao-ktv-key-pair-check".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey); signer.update(challenge)
        val signature = signer.sign()
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey); verifier.update(challenge)
        require(verifier.verify(signature)) { "私钥与公钥不是同一对，请检查导入的文件" }
    }

    private fun refreshStatus() {
        val privateReady = prefs.contains("private_ct") && prefs.contains("private_iv")
        val publicReady = prefs.contains("public_der")
        status.text = when {
            privateReady && publicReady -> "授权密钥：已导入。可离线生成与电脑端相同规则的授权码。"
            privateReady -> "授权密钥：已导入私钥，还需要导入 public_key.b64。"
            publicReady -> "授权密钥：已导入公钥，还需要导入 private_key.pem。"
            else -> "授权密钥：尚未设置。首次使用请点击“首次生成授权密钥”。"
        }
    }

    private fun issue() {
        try {
            require(prefs.contains("private_ct") && prefs.contains("public_der")) {
                "请先导入电脑注册机的 private_key.pem 和 public_key.b64"
            }
            validatePairIfReady()
            val device = machine.text.toString().trim().replace("-".toRegex(), "").lowercase()
            require(device.isNotEmpty() && device.length <= 128 && device.all { it in "0123456789abcdef" }) {
                "机器码格式不正确，请从电视激活页面完整复制"
            }
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            val licenseId = ByteArray(8).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            val payload = JSONObject()
                .put("product", "xiaoxiao-ktv")
                .put("deviceId", device)
                .put("perpetual", true)
                .put("issuedAt", date)
                .put("licenseId", licenseId)
                .toString().toByteArray(Charsets.UTF_8)

            val privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(decryptPrivate()))
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey)
            signer.update(payload)
            val token = b64u(payload) + "." + b64u(signer.sign())
            output.setText(token)
            saveHistory(device, licenseId, date)
            Toast.makeText(this, "授权码生成成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("无法生成授权码")
                .setMessage(e.message ?: "发生未知错误").setPositiveButton("知道了", null).show()
        }
    }

    private fun b64u(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun copyToken() {
        val token = output.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "请先生成授权码", Toast.LENGTH_SHORT).show(); return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KTV授权码", token))
        Toast.makeText(this, "已复制授权码", Toast.LENGTH_SHORT).show()
    }

    private fun shareToken() {
        val token = output.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "请先生成授权码", Toast.LENGTH_SHORT).show(); return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, token)
        }, "分享授权码"))
    }

    private fun saveHistory(device: String, licenseId: String, date: String) {
        val history = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrElse { JSONArray() }
        history.put(JSONObject().put("deviceId", device).put("licenseId", licenseId).put("issuedAt", date))
        while (history.length() > 100) history.remove(0)
        prefs.edit().putString("history", history.toString()).apply()
    }

    private fun showHistory() {
        val history = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrElse { JSONArray() }
        if (history.length() == 0) {
            AlertDialog.Builder(this).setMessage("还没有签发记录").setPositiveButton("确定", null).show()
            return
        }
        val lines = (0 until history.length()).map { i ->
            val item = history.getJSONObject(i)
            "${item.optString("issuedAt")}\n设备：${item.optString("deviceId")}\n编号：${item.optString("licenseId")}"
        }.reversed()
        AlertDialog.Builder(this).setTitle("最近签发记录")
            .setItems(lines.toTypedArray(), null)
            .setPositiveButton("关闭", null).show()
    }
}
