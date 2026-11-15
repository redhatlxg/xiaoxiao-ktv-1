package com.local.ktv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Public download manifest based in-app updater. */
object AppUpdateManager {
    private const val UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/redhatlxg/xiaoxiao-ktv-download/main/latest.json"

    data class Release(
        val version: String,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val sha256: String,
    )

    fun check(currentVersion: String, result: (Result<Release?>) -> Unit) {
        Thread({
            result(runCatching {
                val root = JSONObject(getText(UPDATE_MANIFEST_URL))
                if (!root.optBoolean("available", false)) return@runCatching null
                val version = root.optString("version").trim().removePrefix("v")
                val apkUrl = root.optString("apkUrl").trim()
                val apkName = root.optString("apkName").ifBlank { "xiaoxiao-ktv-$version.apk" }
                val sha256 = root.optString("sha256").trim().lowercase()
                if (version.isBlank() || apkUrl.isBlank() ||
                    !apkUrl.startsWith("https://") || !sha256.matches(Regex("[a-f0-9]{64}")) ||
                    compareVersions(version, currentVersion) <= 0
                ) null
                else Release(
                    version = version,
                    title = root.optString("title").ifBlank { "晓晓呀 KTV $version" },
                    notes = root.optString("releaseNotes"),
                    apkUrl = apkUrl,
                    apkName = apkName,
                    sha256 = sha256,
                )
            })
        }, "public-update-check").start()
    }

    fun download(
        activity: Activity,
        release: Release,
        progress: (Int) -> Unit,
        result: (Result<File>) -> Unit,
    ) {
        Thread({
            result(runCatching {
                val directory = File(activity.externalCacheDir ?: activity.cacheDir, "updates").apply { mkdirs() }
                val target = File(directory, "xiaoxiao-ktv-${release.version}.apk")
                val partial = File(directory, "${target.name}.download")
                partial.delete()
                val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "XiaoxiaoKTV/${BuildConfig.VERSION_NAME}")
                }
                try {
                    require(connection.responseCode in 200..299) { "下载失败：HTTP ${connection.responseCode}" }
                    val total = connection.contentLengthCompat()
                    connection.inputStream.use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var downloaded = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                if (total > 0) progress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                            }
                            output.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                require(partial.length() > 1_000_000L) { "下载的安装包不完整" }
                val actualSha256 = partial.inputStream().use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                }
                require(actualSha256 == release.sha256) { "安装包校验失败，请重新下载" }
                partial.inputStream().use { input ->
                    require(input.read() == 'P'.code && input.read() == 'K'.code) { "下载内容不是 APK" }
                }
                val info = activity.packageManager.getPackageArchiveInfo(partial.absolutePath, 0)
                require(info?.packageName == activity.packageName) { "安装包应用标识不匹配" }
                target.delete()
                require(partial.renameTo(target)) { "无法保存安装包" }
                target
            })
        }, "public-update-download").start()
    }

    /**
     * 直接启动系统 Package Installer 安装 APK。
     *
     * Android 8+ 不再手动跳转 Settings 权限列表 (TV 遥控器难以操作),
     * 而是把 APK URI 交给系统安装器; 若缺少"允许安装未知应用"权限,
     * 系统安装器会自动弹出授权对话框, 用户确认后即可继续安装。
     */
    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7+ 允许系统安装器直接拿到 URI 权限, 无需 NEW_TASK
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        activity.startActivity(intent)
    }

    internal fun compareVersions(left: String, right: String): Int {
        fun parts(value: String) = value.removePrefix("v").split(Regex("[^0-9]+"))
            .filter(String::isNotEmpty).map { it.toIntOrNull() ?: 0 }
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val compared = (a.getOrNull(index) ?: 0).compareTo(b.getOrNull(index) ?: 0)
            if (compared != 0) return compared
        }
        return 0
    }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "XiaoxiaoKTV/${BuildConfig.VERSION_NAME}")
        }
        return try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "检查更新失败：HTTP ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
