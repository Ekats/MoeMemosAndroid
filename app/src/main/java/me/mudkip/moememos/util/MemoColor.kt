package me.mudkip.moememos.util

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import me.mudkip.moememos.R

/**
 * Background colours a memo can be given. Stored on this device only (by [key], in the account's
 * settings), since the Memos server has no field for it. Each has a light and a dark variant.
 */
enum class MemoColor(
    val key: String,
    @param:ColorRes val color: Int,
    @param:DrawableRes val widgetCard: Int,
    @param:DrawableRes val widgetCardPinned: Int,
) {
    RED("red", R.color.memo_color_red, R.drawable.widget_card_color_red, R.drawable.widget_card_color_red_pinned),
    ORANGE("orange", R.color.memo_color_orange, R.drawable.widget_card_color_orange, R.drawable.widget_card_color_orange_pinned),
    YELLOW("yellow", R.color.memo_color_yellow, R.drawable.widget_card_color_yellow, R.drawable.widget_card_color_yellow_pinned),
    GREEN("green", R.color.memo_color_green, R.drawable.widget_card_color_green, R.drawable.widget_card_color_green_pinned),
    TEAL("teal", R.color.memo_color_teal, R.drawable.widget_card_color_teal, R.drawable.widget_card_color_teal_pinned),
    BLUE("blue", R.color.memo_color_blue, R.drawable.widget_card_color_blue, R.drawable.widget_card_color_blue_pinned),
    PURPLE("purple", R.color.memo_color_purple, R.drawable.widget_card_color_purple, R.drawable.widget_card_color_purple_pinned),
    PINK("pink", R.color.memo_color_pink, R.drawable.widget_card_color_pink, R.drawable.widget_card_color_pink_pinned),
    GRAY("gray", R.color.memo_color_gray, R.drawable.widget_card_color_gray, R.drawable.widget_card_color_gray_pinned);

    companion object {
        fun fromKey(key: String?): MemoColor? = entries.firstOrNull { it.key == key }
    }
}
