package com.local.ktv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.ceil

/** 商用电视端分类歌单卡片：统一封面、完整标题、曲目数和整卡遥控器焦点。 */
class PlaylistGridAdapter(
    private val context: Context,
    private val playlists: List<Array<String?>>,
    private val coverImageRes: Int = R.drawable.ic_default_playlist_avatar,
    private val onPlaylistClick: (Array<String?>) -> Unit,
) : BaseAdapter() {
    private val density = context.resources.displayMetrics.density

    override fun getCount(): Int = ceil(playlists.size / COLUMN_COUNT.toDouble()).toInt()
    override fun getItem(position: Int): Any = playlists.getOrNull(position * COLUMN_COUNT) ?: position
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }
        row.removeAllViews()
        repeat(COLUMN_COUNT) { column ->
            val playlist = playlists.getOrNull(position * COLUMN_COUNT + column)
            val tile = playlist?.let(::createTile) ?: View(context).apply { visibility = View.INVISIBLE }
            row.addView(tile, LinearLayout.LayoutParams(0, dp(CARD_HEIGHT), 1f).apply {
                setMargins(dp(5), dp(4), dp(5), dp(7))
            })
        }
        return row
    }

    private fun createTile(playlist: Array<String?>): View {
        val name = playlist.getOrNull(1).orEmpty().ifBlank { "精选歌单" }
        val count = playlist.getOrNull(2)?.toIntOrNull()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Color.rgb(27, 35, 47), Color.rgb(61, 73, 89), 14, 1)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setAccessibleFocus("focus:playlist:${playlist.getOrNull(0) ?: name}",
                "歌单，$name，${count?.let { "$it 首歌曲" } ?: "精选分类"}")

            val artwork = FrameLayout(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(35, 38, 59), Color.rgb(22, 29, 45), Color.rgb(39, 30, 57)),
                ).apply {
                    cornerRadii = floatArrayOf(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), 0f, 0f, 0f, 0f)
                }
                if (coverImageRes == R.drawable.ic_default_playlist_avatar) {
                    addView(ImageView(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setImageDrawable(AppCompatResources.getDrawable(context, iconRes(name)))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(dp(0), dp(0), dp(0), dp(0))
                        adjustViewBounds = true
                        elevation = dp(3).toFloat()
                    }, FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER))
                } else {
                    addView(ImageView(context).apply {
                        setImageResource(coverImageRes)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }, FrameLayout.LayoutParams(-1, -1))
                }
            }
            addView(artwork, LinearLayout.LayoutParams(-1, 0, 1f))

            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(8))
                setBackgroundColor(Color.rgb(25, 32, 43))
                addView(TextView(context).apply {
                    text = name
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(Color.rgb(246, 244, 239))
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = true
                }, LinearLayout.LayoutParams(-1, dp(39)))
                addView(TextView(context).apply {
                    text = count?.takeIf { it > 0 }?.let { "SONGS  ·  $it" } ?: "精选曲目"
                    textSize = 9f
                    letterSpacing = 0.04f
                    setTextColor(Color.rgb(157, 169, 184))
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 1
                }, LinearLayout.LayoutParams(-1, dp(17)))
            }
            addView(details, LinearLayout.LayoutParams(-1, dp(64)))

            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.975f).scaleY(0.975f).setDuration(75L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                }
                false
            }
            setOnClickListener { onPlaylistClick(playlist) }
            TvFocusStyler.install(this)
        }
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    /** 每个分类使用本项目原创的专属矢量图标，不再复用旧贴图素材。 */
    private fun iconRes(name: String): Int = when {
        name.contains("disco", ignoreCase = true) || name.contains("迪斯科") || name.contains("舞厅") -> R.drawable.cat_icon_disco
        name.contains("戏曲") || name.contains("京剧") || name.contains("越剧") || name.contains("黄梅") ||
            name.contains("豫剧") || name.contains("评剧") || name.contains("粤剧") || name.contains("剧种") ||
            name.endsWith("剧") -> R.drawable.cat_icon_opera
        name.contains("儿歌") || name.contains("儿童") || name.contains("卡通") || name.contains("动画") ||
            name.contains("童谣") -> R.drawable.cat_icon_party
        name.contains("情歌") || name.contains("爱情") || name.contains("恋") || name.contains("相思") ||
            name.contains("甜蜜") || name.contains("情人") || name.contains("祝福") || name.contains("生日") ->
            R.drawable.cat_icon_love
        name.contains("影视") || name.contains("电影") || name.contains("电视剧") || name.contains("主题曲") ||
            name.contains("插曲") || name.contains("片段") -> R.drawable.cat_icon_movie
        name.contains("舞") || name.contains("节奏") || name.contains("广场舞") -> R.drawable.cat_icon_dance
        name.contains("摇滚") || name.contains("电音") || name.contains("DJ") || name.contains("劲爆") ||
            name.contains("爵士") || name.contains("蓝调") -> R.drawable.cat_icon_rock
        name.contains("民歌") || name.contains("民族") || name.contains("山歌") || name.contains("乡土") ||
            name.contains("地方") -> R.drawable.cat_icon_folk
        name.contains("校园") || name.contains("青春") || name.contains("毕业") -> R.drawable.cat_icon_campus
        name.contains("友谊") || name.contains("友情") || name.contains("朋友") || name.contains("同学") ||
            name.contains("亲情") || name.contains("家庭") || name.contains("家乡") || name.contains("妈妈") ->
            R.drawable.cat_icon_friendship
        name.contains("经典") || name.contains("怀旧") || name.contains("老歌") || name.contains("金曲") ->
            R.drawable.cat_icon_classic
        name.contains("榜") || name.contains("排行") || name.contains("热门") || name.contains("流行") ||
            name.contains("热歌") || name.contains("新歌") -> R.drawable.cat_icon_pop
        name.contains("军旅") || name.contains("爱国") || name.contains("红歌") || name.contains("主旋律") ||
            name.contains("祝贺") || name.contains("贺岁") || name.contains("喜庆") -> R.drawable.cat_icon_party
        name.contains("网络") || name.contains("外语") || name.contains("欧美") || name.contains("日韩") ||
            name.contains("全球") -> R.drawable.cat_icon_disco
        name.contains("演唱") || name.contains("卡拉") || name.contains("翻唱") || name.contains("歌曲") ->
            R.drawable.cat_icon_pop
        else -> R.drawable.cat_icon_classic
    }

    companion object {
        private const val COLUMN_COUNT = 4
        private const val CARD_HEIGHT = 204
    }
}
