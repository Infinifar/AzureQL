package com.autopanel.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/** 默认主题色（品牌 teal） */
val DefaultSeedColor = Color(0xFF04727F)

/** 设置页「手动选取主题颜色」的预设色板 */
val ThemePresetColors = listOf(
    Color(0xFF04727F), // 默认 teal
    Color(0xFF6750A4), // Material 紫
    Color(0xFF00639B), // 蓝
    Color(0xFF386A20), // 绿
    Color(0xFF835500), // 琥珀
    Color(0xFF9C4146), // 红棕
    Color(0xFF5B4BCF), // 靛蓝
    Color(0xFF8E4957), // 玫红
    Color(0xFF4355B9), // 蓝紫
    Color(0xFF00696D), // 深青
    Color(0xFF984061), // 粉红
    Color(0xFF8B5000), // 橙
    Color(0xFF3F6374), // 蓝灰
    Color(0xFF586500), // 青柠
)

/** 解析 "#RRGGBB"/"#AARRGGBB" 为 [Color]，非法或空返回默认色 */
fun parseSeedColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return DefaultSeedColor
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        DefaultSeedColor
    }
}

/** 将 [Color] 序列化为 "#AARRGGBB" 字符串 */
fun colorToHex(color: Color): String =
    String.format(Locale.US, "#%08X", color.toArgb())
