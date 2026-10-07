package com.francode.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class EditorTheme(
    val name: String,
    val dark: Boolean,
    bg: Long, fg: Long, panel: Long, accent: Long, selection: Long, line: Long, gutter: Long,
    comment: Long, string: Long, number: Long, keyword: Long, type: Long, function: Long,
    tag: Long, attr: Long, meta: Long,
) {
    val bg = Color(bg or 0xFF000000)
    val fg = Color(fg or 0xFF000000)
    val panel = Color(panel or 0xFF000000)
    val accent = Color(accent or 0xFF000000)
    val selection = Color(selection or 0xFF000000)
    val line = Color(line or 0xFF000000)
    val gutter = Color(gutter or 0xFF000000)
    val cursor = this.accent
    val muted = this.fg.copy(alpha = 0.6f)
    val border = this.fg.copy(alpha = 0.12f)
    val findMatch = Color(0xFFEAC54F).copy(alpha = 0.35f)
    val findCurrent = Color(0xFFF28B30).copy(alpha = 0.65f)
    val onAccent = if (dark) Color.White else Color.White

    private val tokens = mapOf(
        Tok.Comment to Color(comment or 0xFF000000),
        Tok.String to Color(string or 0xFF000000),
        Tok.Number to Color(number or 0xFF000000),
        Tok.Keyword to Color(keyword or 0xFF000000),
        Tok.Type to Color(type or 0xFF000000),
        Tok.Function to Color(function or 0xFF000000),
        Tok.Tag to Color(tag or 0xFF000000),
        Tok.Attr to Color(attr or 0xFF000000),
        Tok.Meta to Color(meta or 0xFF000000),
    )

    fun color(t: Tok): Color = tokens.getValue(t)

    fun colorScheme(): ColorScheme =
        if (dark) darkColorScheme(
            primary = accent, onPrimary = Color.White, background = bg, onBackground = fg,
            surface = panel, onSurface = fg, surfaceVariant = line, onSurfaceVariant = muted,
            surfaceContainer = panel, surfaceContainerHigh = panel, surfaceContainerHighest = panel,
            surfaceContainerLow = panel, surfaceContainerLowest = bg,
            outline = border, secondaryContainer = selection, onSecondaryContainer = fg,
            primaryContainer = selection, onPrimaryContainer = fg,
        ) else lightColorScheme(
            primary = accent, onPrimary = Color.White, background = bg, onBackground = fg,
            surface = panel, onSurface = fg, surfaceVariant = line, onSurfaceVariant = muted,
            surfaceContainer = panel, surfaceContainerHigh = panel, surfaceContainerHighest = panel,
            surfaceContainerLow = panel, surfaceContainerLowest = bg,
            outline = border, secondaryContainer = selection, onSecondaryContainer = fg,
            primaryContainer = selection, onPrimaryContainer = fg,
        )
}

object EditorThemes {
    val all: List<EditorTheme> = listOf(
        EditorTheme(
            "Francode Dark", true, bg = 0x0D1117, fg = 0xC9D1D9, panel = 0x161B22, accent = 0x2F81F7,
            selection = 0x264F78, line = 0x161B22, gutter = 0x6E7681,
            comment = 0x8B949E, string = 0xA5D6FF, number = 0x79C0FF, keyword = 0xFF7B72,
            type = 0xFFA657, function = 0xD2A8FF, tag = 0x7EE787, attr = 0x79C0FF, meta = 0xFFA657,
        ),
        EditorTheme(
            "Dark+", true, bg = 0x1E1E1E, fg = 0xD4D4D4, panel = 0x252526, accent = 0x007ACC,
            selection = 0x264F78, line = 0x2A2D2E, gutter = 0x858585,
            comment = 0x6A9955, string = 0xCE9178, number = 0xB5CEA8, keyword = 0x569CD6,
            type = 0x4EC9B0, function = 0xDCDCAA, tag = 0x569CD6, attr = 0x9CDCFE, meta = 0xC586C0,
        ),
        EditorTheme(
            "Light+", false, bg = 0xFFFFFF, fg = 0x1F1F1F, panel = 0xF3F3F3, accent = 0x007ACC,
            selection = 0xADD6FF, line = 0xF5F5F5, gutter = 0x237893,
            comment = 0x008000, string = 0xA31515, number = 0x098658, keyword = 0x0000FF,
            type = 0x267F99, function = 0x795E26, tag = 0x800000, attr = 0xE50000, meta = 0xAF00DB,
        ),
        EditorTheme(
            "Monokai", true, bg = 0x272822, fg = 0xF8F8F2, panel = 0x1E1F1C, accent = 0xF92672,
            selection = 0x49483E, line = 0x3E3D32, gutter = 0x90908A,
            comment = 0x75715E, string = 0xE6DB74, number = 0xAE81FF, keyword = 0xF92672,
            type = 0x66D9EF, function = 0xA6E22E, tag = 0xF92672, attr = 0xA6E22E, meta = 0x66D9EF,
        ),
        EditorTheme(
            "Dracula", true, bg = 0x282A36, fg = 0xF8F8F2, panel = 0x21222C, accent = 0xBD93F9,
            selection = 0x44475A, line = 0x2F3140, gutter = 0x6272A4,
            comment = 0x6272A4, string = 0xF1FA8C, number = 0xBD93F9, keyword = 0xFF79C6,
            type = 0x8BE9FD, function = 0x50FA7B, tag = 0xFF79C6, attr = 0x50FA7B, meta = 0xFFB86C,
        ),
        EditorTheme(
            "One Dark", true, bg = 0x282C34, fg = 0xABB2BF, panel = 0x21252B, accent = 0x61AFEF,
            selection = 0x3E4451, line = 0x2C313A, gutter = 0x636D83,
            comment = 0x5C6370, string = 0x98C379, number = 0xD19A66, keyword = 0xC678DD,
            type = 0xE5C07B, function = 0x61AFEF, tag = 0xE06C75, attr = 0xD19A66, meta = 0x56B6C2,
        ),
        EditorTheme(
            "Solarized Dark", true, bg = 0x002B36, fg = 0x93A1A1, panel = 0x073642, accent = 0x268BD2,
            selection = 0x0A4A5A, line = 0x073642, gutter = 0x586E75,
            comment = 0x586E75, string = 0x2AA198, number = 0xD33682, keyword = 0x859900,
            type = 0xB58900, function = 0x268BD2, tag = 0x268BD2, attr = 0x93A1A1, meta = 0xCB4B16,
        ),
        EditorTheme(
            "Solarized Light", false, bg = 0xFDF6E3, fg = 0x586E75, panel = 0xEEE8D5, accent = 0x268BD2,
            selection = 0xD6D0B8, line = 0xEEE8D5, gutter = 0x93A1A1,
            comment = 0x93A1A1, string = 0x2AA198, number = 0xD33682, keyword = 0x859900,
            type = 0xB58900, function = 0x268BD2, tag = 0x268BD2, attr = 0x657B83, meta = 0xCB4B16,
        ),
    )

    fun byName(name: String): EditorTheme = all.firstOrNull { it.name == name } ?: all.first()
}
