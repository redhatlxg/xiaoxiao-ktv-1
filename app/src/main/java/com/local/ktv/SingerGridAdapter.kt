package com.local.ktv

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * TV singer browser, deliberately using one singer per ListView item.
 *
 * The old implementation nested several independently focusable/clickable cards
 * inside one ListView row. That split touch and DPAD activation between child views
 * and the ListView's item-click dispatcher. This adapter has no child click targets:
 * Android's ListView owns every activation, so touch and remote OK use the same path.
 */
class SingerGridAdapter(
    private val context: Context,
    private val singers: List<Array<String?>>,
) : BaseAdapter() {
    private val density = context.resources.displayMetrics.density

    override fun getCount(): Int = singers.size
    override fun getItem(position: Int): Array<String?> = singers[position]
    override fun getItemId(position: Int): Long = singers[position].getOrNull(0)?.toLongOrNull() ?: position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val singer = getItem(position)
        val name = singer.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty) ?: "歌手资料待完善"
        val height = dp(112)
        val card = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(18), dp(10))
            setBackgroundResource(R.drawable.bg_singer_tile)
            // 卡片本身不拦截点击/焦点；由 ListView 行选择器处理遥控器焦点与确认键。
            isFocusable = false
            isClickable = false
            isFocusableInTouchMode = false
            isLongClickable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        card.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        card.contentDescription = "查看${name}演唱的歌曲"
        card.removeAllViews()

        val avatarSize = dp(88)
        val avatar = ImageView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusable = false
            isClickable = false
            scaleType = ImageView.ScaleType.CENTER_CROP
            SingerAvatarLoader.load(this, singer.getOrNull(5), R.drawable.ic_singer_placeholder)
            UiCompat.applyCircleOutline(this)
        }
        card.addView(avatar, LinearLayout.LayoutParams(avatarSize, avatarSize))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, 0, 0)
            isFocusable = false
            isClickable = false
        }
        labels.addView(TextView(context).apply {
            text = name
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        labels.addView(TextView(context).apply {
            text = "按确认键或点击查看该歌手的歌曲"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(7), 0, 0)
        })
        card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(context).apply {
            text = "查看歌曲  ›"
            textSize = 16f
            setTextColor(Color.rgb(245, 205, 126))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
}
