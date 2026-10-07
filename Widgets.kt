package com.francode.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ------------------------------------------------------------------ small building blocks

@Composable
fun SmallField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    theme: EditorTheme,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
    imeAction: ImeAction = ImeAction.Search,
    onDone: () -> Unit = {},
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        modifier = modifier,
        textStyle = TextStyle(color = theme.fg, fontSize = 14.sp),
        cursorBrush = SolidColor(theme.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }, onGo = { onDone() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(theme.bg, RoundedCornerShape(6.dp))
                    .border(1.dp, if (invalid) Color(0xFFE5534B) else theme.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = theme.muted, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
fun ToggleChip(label: String, on: Boolean, theme: EditorTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .background(if (on) theme.accent else Color.Transparent, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            color = if (on) Color.White else theme.muted,
        )
    }
}

// ------------------------------------------------------------------ tab bar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(vm: EditorViewModel, theme: EditorTheme) {
    val state = rememberLazyListState()
    LaunchedEffect(vm.activeIndex, vm.tabs.size) {
        if (vm.activeIndex in vm.tabs.indices) state.animateScrollToItem(vm.activeIndex)
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().background(theme.panel),
    ) {
        itemsIndexed(vm.tabs, key = { _, t -> System.identityHashCode(t) }) { i, t ->
            val active = i == vm.activeIndex
            Row(
                Modifier
                    .height(38.dp)
                    .background(if (active) theme.bg else theme.panel)
                    .drawBehind {
                        if (active) drawRect(theme.accent, Offset.Zero, Size(size.width, 2.dp.toPx()))
                    }
                    .combinedClickable(onClick = { vm.select(i) }, onLongClick = { vm.requestClose(t) })
                    .padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (active) theme.fg else theme.muted,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                Box(Modifier.size(30.dp).clickable { vm.requestClose(t) }, contentAlignment = Alignment.Center) {
                    if (t.dirty) Box(Modifier.size(9.dp).background(theme.fg.copy(alpha = 0.8f), CircleShape))
                    else Icon(Icons.Default.Close, "Close", Modifier.size(15.dp), tint = theme.muted)
                }
            }
        }
    }
    HorizontalDivider(color = theme.border)
}

// ------------------------------------------------------------------ symbol bar & status bar

@Composable
fun SymbolBar(tab: Tab, theme: EditorTheme) {
    val keyboard = LocalSoftwareKeyboardController.current
    val keys = remember {
        listOf(
            "⇥", "←", "→", "{}", "()", "[]", "<>", "\"\"", "''", ";", ":", "=", "+", "-", "*", "/",
            "\\", "|", "&", "_", "$", "#", "!", "?", "%", "@", "~", "`", ",", ".",
        )
    }
    Row(
        Modifier.fillMaxWidth().background(theme.panel).height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolKey("↶", theme) { tab.undo() }
        SymbolKey("↷", theme) { tab.redo() }
        LazyRow(Modifier.weight(1f)) {
            items(keys) { k ->
                SymbolKey(k, theme) {
                    when (k) {
                        "⇥" -> tab.commit(TextOps.indent(tab.edit(), " ".repeat(4), false))
                        "←" -> tab.moveCursor(-1)
                        "→" -> tab.moveCursor(1)
                        "{}", "()", "[]", "<>", "\"\"", "''" -> tab.insert(k, cursorBack = 1)
                        else -> tab.insert(k)
                    }
                }
            }
        }
        IconButton(onClick = { keyboard?.hide() }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.KeyboardHide, "Hide keyboard", tint = theme.muted)
        }
    }
}

@Composable
private fun SymbolKey(label: String, theme: EditorTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .width(40.dp)
            .height(42.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, fontFamily = FontFamily.Monospace, color = theme.fg)
    }
}

@Composable
fun StatusBar(tab: Tab, vm: EditorViewModel, theme: EditorTheme, onPickLanguage: () -> Unit) {
    val v = tab.value
    val (line, col) = remember(v.text, v.selection.start) { TextOps.lineCol(v.text, v.selection.start) }
    val selLen = v.selection.length
    Row(
        Modifier.fillMaxWidth().background(theme.accent).height(26.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val c = Color.White
        Text("Ln $line, Col $col" + if (selLen > 0) " ($selLen sel)" else "", fontSize = 11.sp, color = c)
        Text("Spaces: ${vm.tabSize}", fontSize = 11.sp, color = c)
        Text("UTF-8", fontSize = 11.sp, color = c)
        Text(if (tab.crlf) "CRLF" else "LF", fontSize = 11.sp, color = c)
        Spacer(Modifier.weight(1f))
        Text(
            tab.lang.label, fontSize = 11.sp, color = c, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onPickLanguage),
        )
    }
}

// ------------------------------------------------------------------ find / replace bar

@Composable
fun FindBar(vm: EditorViewModel, theme: EditorTheme) {
    val f = vm.find
    val matches = vm.findMatches
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().background(theme.panel).padding(horizontal = 6.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallField(
                f.query, { f.query = it; vm.findFromCursor() }, "Find", theme,
                Modifier.weight(1f).focusRequester(focus),
                invalid = vm.findQueryInvalid, onDone = { vm.findStep(true) },
            )
            Spacer(Modifier.width(4.dp))
            ToggleChip("Aa", f.caseSensitive, theme) { f.caseSensitive = !f.caseSensitive; vm.findFromCursor() }
            ToggleChip(".*", f.regex, theme) { f.regex = !f.regex; vm.findFromCursor() }
            ToggleChip("W", f.wholeWord, theme) { f.wholeWord = !f.wholeWord; vm.findFromCursor() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                f.query.isEmpty() -> ""
                matches.isEmpty() -> "No results"
                else -> "${f.current.coerceIn(0, matches.lastIndex) + 1} of ${matches.size}"
            }
            Text(label, fontSize = 12.sp, color = theme.muted, modifier = Modifier.padding(start = 6.dp).weight(1f))
            IconButton(onClick = { vm.findStep(false) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = theme.fg)
            }
            IconButton(onClick = { vm.findStep(true) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Next", tint = theme.fg)
            }
            TextButton(onClick = { f.replaceMode = !f.replaceMode }) {
                Text(if (f.replaceMode) "Hide replace" else "Replace", fontSize = 12.sp)
            }
            IconButton(onClick = { vm.closeFind() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Close find", tint = theme.fg)
            }
        }
        if (f.replaceMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallField(
                    f.replacement, { f.replacement = it }, "Replace with", theme, Modifier.weight(1f),
                    imeAction = ImeAction.Done, onDone = { vm.replaceCurrent() },
                )
                TextButton(onClick = { vm.replaceCurrent() }) { Text("Replace", fontSize = 12.sp) }
                TextButton(onClick = { vm.replaceAll() }) { Text("All", fontSize = 12.sp) }
            }
        }
    }
    HorizontalDivider(color = theme.border)
}

// ------------------------------------------------------------------ picker (command palette, quick open, ...)

class PickItem(
    val title: String,
    val subtitle: String = "",
    val hint: String = "",
    val onPick: () -> Unit,
)

private fun fuzzy(q: String, s: String): Int {
    if (q.isEmpty()) return 0
    val ql = q.lowercase()
    val sl = s.lowercase()
    var qi = 0
    var score = 0
    var last = -2
    for (i in sl.indices) {
        if (qi < ql.length && sl[i] == ql[qi]) {
            score += 1
            if (i == last + 1) score += 3
            if (i == 0 || !sl[i - 1].isLetterOrDigit()) score += 4
            last = i
            qi++
        }
    }
    if (qi < ql.length) return -1
    if (sl.contains(ql)) score += 30
    return score * 10 - sl.length
}

private fun filterItems(q: String, items: List<PickItem>): List<PickItem> {
    if (q.isBlank()) return items.take(100)
    return items.mapNotNull { it ->
        val a = fuzzy(q, it.title)
        val b = if (it.subtitle.isNotEmpty()) fuzzy(q, it.subtitle) else -1
        val s = if (a >= 0) a + 200 else b
        if (s < 0) null else it to s
    }.sortedByDescending { it.second }.take(100).map { it.first }
}

@Composable
fun PickerDialog(
    placeholder: String,
    items: List<PickItem>,
    theme: EditorTheme,
    onDismiss: () -> Unit,
    loading: Boolean = false,
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, items) { filterItems(q, items) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val none = remember { MutableInteractionSource() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = none, indication = null, onClick = onDismiss)
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.panel,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 460.dp)
                    .clickable(interactionSource = none, indication = null) {},
            ) {
                Column {
                    SmallField(
                        q, { q = it }, placeholder, theme,
                        Modifier.fillMaxWidth().padding(10.dp).focusRequester(focus),
                        imeAction = ImeAction.Done,
                        onDone = { filtered.firstOrNull()?.let { onDismiss(); it.onPick() } },
                    )
                    HorizontalDivider(color = theme.border)
                    if (filtered.isEmpty()) {
                        Text(
                            if (loading) "Indexing files…" else "No matches",
                            color = theme.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                        )
                    }
                    LazyColumn {
                        items(filtered) { item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onDismiss(); item.onPick() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (item.subtitle.isNotEmpty()) {
                                        Text(
                                            item.subtitle, fontSize = 11.sp, color = theme.muted,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (item.hint.isNotEmpty()) {
                                    Text(item.hint, fontSize = 11.sp, color = theme.muted, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ prompts

@Composable
fun PromptDialog(
    title: String,
    label: String,
    initial: String = "",
    confirm: String = "OK",
    message: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    selectBaseName: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var v by remember {
        val sel = if (selectBaseName && initial.contains('.')) initial.lastIndexOf('.') else initial.length
        mutableStateOf(TextFieldValue(initial, TextRange(0, sel)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) Text(message, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = v, onValueChange = { v = it }, singleLine = true, label = { Text(label) },
                    modifier = Modifier.focusRequester(focus),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType, imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (v.text.isNotBlank()) onConfirm(v.text.trim()) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (v.text.isNotBlank()) onConfirm(v.text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirm: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit,
    neutral: String? = null, onNeutral: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            Row {
                if (neutral != null) TextButton(onClick = onNeutral) { Text(neutral) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ------------------------------------------------------------------ settings

@Composable
fun SettingsDialog(vm: EditorViewModel, theme: EditorTheme, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(14.dp), color = theme.panel,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                Spacer(Modifier.height(8.dp))

                Text("Colour theme", fontSize = 12.sp, color = theme.muted)
                for (t in EditorThemes.all) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (t.name == vm.themeName) theme.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { vm.setTheme(t.name) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        for (tok in listOf(Tok.Keyword, Tok.String, Tok.Function, Tok.Type, Tok.Number)) {
                            Box(Modifier.padding(start = 4.dp).size(14.dp).background(t.color(tok), CircleShape))
                        }
                        Box(
                            Modifier.padding(start = 8.dp).size(width = 22.dp, height = 14.dp)
                                .background(t.bg, RoundedCornerShape(3.dp))
                                .border(1.dp, t.border, RoundedCornerShape(3.dp)),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Font size: ${vm.fontSize}", fontSize = 12.sp, color = theme.muted)
                Slider(
                    value = vm.fontSize.toFloat(), onValueChange = { vm.setFontSize(it.toInt()) },
                    valueRange = 9f..28f, steps = 18,
                )

                Text("Tab size", fontSize = 12.sp, color = theme.muted)
                Row(Modifier.padding(top = 4.dp)) {
                    for (n in listOf(2, 4, 8)) {
                        Box(
                            Modifier.padding(end = 8.dp)
                                .background(if (vm.tabSize == n) theme.accent else theme.bg, RoundedCornerShape(8.dp))
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                .clickable { vm.setTabSize(n) }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) { Text("$n", color = if (vm.tabSize == n) Color.White else theme.fg) }
                    }
                }
                Spacer(Modifier.height(10.dp))

                SwitchRow("Word wrap", vm.wordWrap, theme) { vm.setWordWrap(it) }
                SwitchRow("Line numbers", vm.lineNumbers, theme) { vm.setLineNumbers(it) }
                SwitchRow("Highlight current line", vm.highlightLine, theme) { vm.setHighlightLine(it) }
                SwitchRow("Auto-close brackets and quotes", vm.autoClose, theme) { vm.setAutoClose(it) }
                SwitchRow("Auto-save (when switching tabs or leaving the app)", vm.autoSave, theme) { vm.setAutoSave(it) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, theme: EditorTheme, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
