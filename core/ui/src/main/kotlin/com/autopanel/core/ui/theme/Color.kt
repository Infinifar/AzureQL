package com.autopanel.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/** 默认主题色（品牌 teal） */
val DefaultSeedColor = Color(0xFF04727F)

/** 预设主题色（含中英文名称，用于「当前选择」显示） */
data class PresetColor(
    val color: Color,
    val nameZh: String,
    val nameEn: String
)

/** 设置页「主题颜色」预设色板 */
val ThemePresetColors = listOf(
    PresetColor(Color(0xFF04727F), "青色", "Teal"),
    PresetColor(Color(0xFF6750A4), "紫色", "Purple"),
    PresetColor(Color(0xFF00639B), "天蓝色", "Sky blue"),
    PresetColor(Color(0xFF386A20), "绿色", "Green"),
    PresetColor(Color(0xFF835500), "琥珀色", "Amber"),
    PresetColor(Color(0xFF9C4146), "红棕色", "Maroon"),
    PresetColor(Color(0xFF5B4BCF), "靛蓝色", "Indigo"),
    PresetColor(Color(0xFF8E4957), "玫红色", "Rose"),
    PresetColor(Color(0xFF4355B9), "蓝紫色", "Blue violet"),
    PresetColor(Color(0xFF984061), "粉红色", "Pink"),
    PresetColor(Color(0xFF8B5000), "橙色", "Orange"),
    PresetColor(Color(0xFF3F6374), "蓝灰色", "Blue gray"),
    PresetColor(Color(0xFF586500), "青柠色", "Lime"),
)

/** 根据种子色查找预设颜色名称（用于显示当前选择），找不到返回 null */
fun presetColorName(seedColor: Color, isEnglish: Boolean): String? {
    val argb = seedColor.toArgb()
    return ThemePresetColors.firstOrNull { it.color.toArgb() == argb }?.let {
        if (isEnglish) it.nameEn else it.nameZh
    }
}

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
