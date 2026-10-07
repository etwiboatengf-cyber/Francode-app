package com.francode.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Callbacks the editor needs from the surrounding screen (hardware keyboard shortcuts). */
class EditorActions(
    val palette: () -> Unit,
    val quickOpen: () -> Unit,
    val gotoLine: () -> Unit,
)

fun buildHighlighted(text: String, lang: LangDef, theme: EditorTheme): AnnotatedString {
    val spans = lang.tokenize(text)
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (s in spans) {
            val style = if (s.tok == Tok.Comment)
                SpanStyle(color = theme.color(s.tok), fontStyle = FontStyle.Italic)
            else SpanStyle(color = theme.color(s.tok))
            addStyle(style, s.start, s.end)
        }
    }
}

@Composable
fun CodeEditor(
    tab: Tab,
    vm: EditorViewModel,
    theme: EditorTheme,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fs = vm.fontSize
    val textStyle = remember(fs, theme) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fs.sp,
            lineHeight = (fs * 1.55f).sp,
            color = theme.fg,
        )
    }
    val gutterStyle = remember(textStyle, theme) {
        textStyle.copy(color = theme.gutter, textAlign = TextAlign.End)
    }

    val value = tab.value
    val text = value.text
    val lang = tab.lang
    val base = remember(text, lang, theme) { buildHighlighted(text, lang, theme) }
    val matches = vm.findMatches
    val currentMatch = vm.find.current
    val shown = remember(base, matches, currentMatch, theme) {
        if (matches.isEmpty()) base
        else buildAnnotatedString {
            append(base)
            matches.forEachIndexed { i, r ->
                addStyle(
                    SpanStyle(background = if (i == currentMatch) theme.findCurrent else theme.findMatch),
                    r.first, r.last + 1,
                )
            }
        }
    }
    val transformation = remember(shown) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText =
                if (text.length == shown.length) TransformedText(shown, OffsetMapping.Identity)
                else TransformedText(text, OffsetMapping.Identity)
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val gutterText = remember(layout, vm.lineNumbers) {
        val l = layout
        if (!vm.lineNumbers) ""
        else if (l == null) (1..(text.count { it == '\n' } + 1)).joinToString("\n")
        else {
            val sb = StringBuilder()
            val src = l.layoutInput.text
            var n = 0
            for (i in 0 until l.lineCount) {
                val st = l.getLineStart(i)
                if (st == 0 || src[st - 1] == '\n') {
                    n++
                    sb.append(n)
                }
                if (i < l.lineCount - 1) sb.append('\n')
            }
            sb.toString()
        }
    }

    val selColors = remember(theme) {
        TextSelectionColors(handleColor = theme.accent, backgroundColor = theme.selection)
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selColors) {
        BoxWithConstraints(modifier.background(theme.bg)) {
            val viewportH = maxHeight
            val viewportHpx = with(density) { maxHeight.toPx() }
            val vScroll = rememberScrollState(tab.scrollY)
            val hScroll = rememberScrollState(tab.scrollX)

            DisposableEffect(tab) {
                onDispose {
                    tab.scrollY = vScroll.value
                    tab.scrollX = hScroll.value
                }
            }

            LaunchedEffect(tab.revealNonce) {
                if (tab.revealNonce == 0) return@LaunchedEffect
                val l = snapshotFlow { layout }.filterNotNull().first()
                val off = tab.value.selection.start.coerceIn(0, l.layoutInput.text.length)
                val top = l.getLineTop(l.getLineForOffset(off))
                vScroll.animateScrollTo((top - viewportHpx / 3f).toInt().coerceAtLeast(0))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(vScroll)
                    .heightIn(min = viewportH)
                    .drawBehind {
                        val l = layout
                        val sel = tab.value.selection
                        if (l != null && vm.highlightLine && sel.collapsed) {
                            val off = sel.start.coerceIn(0, l.layoutInput.text.length)
                            val top = l.getLineTop(l.getLineForOffset(off))
                            val bottom = l.getLineBottom(l.getLineForOffset(off))
                            drawRect(theme.line, Offset(0f, top), Size(size.width, bottom - top))
                        }
                    },
            ) {
                if (vm.lineNumbers) {
                    Text(
                        text = gutterText,
                        style = gutterStyle,
                        softWrap = false,
                        modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                    )
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val w = maxWidth
                    val minH = (viewportH - 96.dp).coerceAtLeast(0.dp)
                    Box(if (vm.wordWrap) Modifier else Modifier.horizontalScroll(hScroll)) {
                        BasicTextField(
                            value = value,
                            onValueChange = { vm.onEdit(tab, it) },
                            modifier = (if (vm.wordWrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = w))
                                .heightIn(min = minH)
                                .padding(end = 24.dp, bottom = 96.dp)
                                .onPreviewKeyEvent { e -> handleKey(e, tab, vm, actions) },
                            textStyle = textStyle,
                            cursorBrush = SolidColor(theme.cursor),
                            visualTransformation = transformation,
                            onTextLayout = { layout = it },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Default,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun handleKey(e: androidx.compose.ui.input.key.KeyEvent, tab: Tab, vm: EditorViewModel, a: EditorActions): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed || e.isMetaPressed
    val shift = e.isShiftPressed
    val alt = e.isAltPressed
    return when {
        ctrl && e.key == Key.S -> { if (shift) vm.saveAll() else vm.saveActive(); true }
        ctrl && e.key == Key.F -> { vm.openFind(false); true }
        ctrl && e.key == Key.H -> { vm.openFind(true); true }
        ctrl && e.key == Key.P -> { if (shift) a.palette() else a.quickOpen(); true }
        ctrl && e.key == Key.G -> { a.gotoLine(); true }
        ctrl && e.key == Key.Z -> { if (shift) tab.redo() else tab.undo(); true }
        ctrl && e.key == Key.Y -> { tab.redo(); true }
        ctrl && e.key == Key.Slash -> { vm.toggleComment(); true }
        ctrl && e.key == Key.D -> { vm.duplicateLine(); true }
        ctrl && shift && e.key == Key.K -> { vm.deleteLine(); true }
        ctrl && e.key == Key.W -> { vm.requestClose(tab); true }
        alt && e.key == Key.DirectionUp -> { vm.moveLine(true); true }
        alt && e.key == Key.DirectionDown -> { vm.moveLine(false); true }
        e.key == Key.Tab -> { vm.indent(outdent = shift); true }
        else -> false
    }
}
