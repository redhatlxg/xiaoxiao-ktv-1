package com.local.ktv.issuer
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phone-only, fully offline short-code issuer. */
class IssuerActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("short_issuer_v1", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var machine: EditText
    private lateinit var days: EditText
    private lateinit var output: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        refreshStatus()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFFF5F7FB.toInt())
        }
        setContentView(ScrollView(this).apply { addView(root) })
        root.addView(TextView(this).apply {
            text = "晓晓呀 KTV"
            textSize = 26f
            setTextColor(0xFF17243A.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "手机离线授权助手"
            textSize = 17f
            setTextColor(0xFF52627A.toInt())
            setPadding(0, dp(3), 0, dp(16))
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF24415F.toInt())
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setBackgroundColor(0xFFE4ECF6.toInt())
        }
        root.addView(status, matchWrap())
        section(root, "第一步 · 输入电视机器码")
        machine = EditText(this).apply {
            hint = "例如：A1:B2:C3:D4:E5:F6"
            textSize = 17f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        root.addView(machine, matchWrap())
        section(root, "第二步 · 设置激活天数")
        days = EditText(this).apply {
            setText("30")
            hint = "输入天数；0 代表永久授权"
            textSize = 17f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(days, matchWrap())
        val presets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            "7天" to "7", "15天" to "15", "30天" to "30", "90天" to "90",
            "180天" to "180", "365天" to "365", "永久" to "0"
        ).chunked(4).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { (label, value) ->
                row.addView(Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = 13f
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                    setOnClickListener { days.setText(value) }
                }, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    marginEnd = dp(4)
                })
            }
            presets.addView(row, matchWrap())
        }
        root.addView(presets, matchWrap())
        root.addView(TextView(this).apply {
            text = "可手动输入 1–65535 天。期限从电视第一次成功激活时开始计算。"
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(12))
        })
        section(root, "第三步 · 生成短激活码")
        root.addView(Button(this).apply {
            text = "生成激活码"
            isAllCaps = false
            textSize = 17f
            setOnClickListener { issue() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            bottomMargin = dp(12)
        })
        output = EditText(this).apply {
            hint = "激活码会显示在这里"
            minLines = 2
            textSize = 17f
            setTextIsSelectable(true)
        }
        root.addView(output, matchWrap())
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "复制激活码"
            isAllCaps = false
            setOnClickListener { copyCode() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(Button(this).apply {
            text = "分享激活码"
            isAllCaps = false
            setOnClickListener { shareCode() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        root.addView(actions, matchWrap())
        root.addView(Button(this).apply {
            text = "查看手机签发记录"
            isAllCaps = false
            setOnClickListener { showHistory() }
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "完全离线，无需服务器。短码只适用于对应机器码。离线短码防伪强度低于非对称长签名方案，请妥善保管授权手机。"
            textSize = 12f
            setTextColor(0xFF9B3434.toInt())
            setPadding(0, dp(18), 0, 0)
        })
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(0xFF1D3555.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(6))
        })
    }

    private fun issue() {
        try {
            val device = ShortLicenseCodec.normalizeMachineCode(machine.text.toString())
                ?: error("机器码格式不正确，请从电视激活页面复制 12 位机器码")
            val term = days.text.toString().trim().toIntOrNull()
                ?: error("请填写激活天数；0 代表永久")
            require(term in 0..65535) { "激活天数范围为 0–65535" }
            val code = ShortLicenseCodec.issue(device, term)
            output.setText(code)
            saveHistory(device, term, code)
            Toast.makeText(this, "短激活码生成成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("生成失败")
                .setMessage(e.message ?: "请输入有效的机器码和激活天数")
                .setPositiveButton("确定", null).show()
        }
    }

    private fun refreshStatus() {
        val count = runCatching { JSONArray(prefs.getString("history", "[]")).length() }.getOrDefault(0)
        status.text = "授权方式：离线短码\n历史签发：$count 条\n无需登录、服务器或联网"
    }

    private fun saveHistory(machineCode: String, term: Int, code: String) {
        val history = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrElse { JSONArray() }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        history.put(JSONObject().put("machine", machineCode).put("days", term)
            .put("code", code).put("createdAt", date))
        while (history.length() > 100) history.remove(0)
        prefs.edit().putString("history", history.toString()).apply()
        refreshStatus()
    }

    private fun showHistory() {
        val history = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrElse { JSONArray() }
        if (history.length() == 0) {
            AlertDialog.Builder(this).setMessage("暂时没有签发记录")
                .setPositiveButton("确定", null).show()
            return
        }
        val lines = (0 until history.length()).map { index ->
            val item = history.getJSONObject(index)
            val term = if (item.optInt("days") == 0) "永久" else "${item.optInt("days")} 天"
            "${item.optString("createdAt")} · $term\n机器码：${item.optString("machine")}\n激活码：${item.optString("code")}"
        }.reversed()
        AlertDialog.Builder(this).setTitle("最近签发记录")
            .setItems(lines.toTypedArray()) { _, which ->
                val item = history.getJSONObject(history.length() - 1 - which)
                output.setText(item.optString("code"))
                machine.setText(item.optString("machine"))
                days.setText(item.optInt("days").toString())
            }.setPositiveButton("关闭", null).show()
    }

    private fun copyCode() {
        val code = output.text.toString().trim()
        if (code.isEmpty()) {
            Toast.makeText(this, "请先生成激活码", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("晓晓呀 KTV 激活码", code))
        Toast.makeText(this, "激活码已复制", Toast.LENGTH_SHORT).show()
    }

    private fun shareCode() {
        val code = output.text.toString().trim()
        if (code.isEmpty()) {
            Toast.makeText(this, "请先生成激活码", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, code)
        }, "分享晓晓呀 KTV 激活码"))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
