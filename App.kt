package com.francode.app

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

@Composable
fun FrancodeApp(vm: EditorViewModel) {
    val theme = EditorThemes.byName(vm.themeName)
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    SideEffect {
        activity?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !theme.dark
            c.isAppearanceLightNavigationBars = !theme.dark
        }
    }
    MaterialTheme(colorScheme = theme.colorScheme()) {
        Surface(Modifier.fillMaxSize(), color = theme.panel, contentColor = theme.fg) {
            AppScaffold(vm, theme)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppScaffold(vm: EditorViewModel, theme: EditorTheme) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val wide = LocalConfiguration.current.screenWidthDp >= 840

    var sideMode by remember { mutableIntStateOf(0) }
    var sidebarVisible by remember { mutableStateOf(true) }
    var showPalette by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var showGoto by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showLangs by remember { mutableStateOf(false) }
    var quickFiles by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var quickLoading by remember { mutableStateOf(false) }

    // ---- activity result launchers
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.openTreeUri(uri)
    }
    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        for (u in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
                try {
                    context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }
            vm.openFile(u)
        }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.saveActiveAs(uri)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { m -> launch { snackbar.showSnackbar(m) } }
    }
    LaunchedEffect(showQuick) {
        if (showQuick) {
            quickLoading = true
            quickFiles = vm.fileList()
            quickLoading = false
        }
    }

    BackHandler(enabled = vm.previewTab != null) { vm.previewTab = null }
    BackHandler(enabled = vm.find.visible && vm.previewTab == null) { vm.closeFind() }

    fun openSide(mode: Int) {
        sideMode = mode
        if (wide) sidebarVisible = true else scope.launch { drawerState.open() }
    }
    fun closeSide() {
        if (!wide) scope.launch { drawerState.close() }
    }

    val actions = remember {
        EditorActions(
            palette = { showPalette = true },
            quickOpen = { showQuick = true },
            gotoLine = { showGoto = true },
        )
    }

    // ---- command palette contents
    fun commands(): List<PickItem> {
        val out = ArrayList<PickItem>()
        fun cmd(title: String, hint: String = "", f: () -> Unit) { out.add(PickItem(title, hint = hint, onPick = f)) }
        val t = vm.active
        cmd("File: New File…") { vm.root?.let { vm.entryPrompt = NewEntry(it, "", false) } }
        cmd("File: New Folder…") { vm.root?.let { vm.entryPrompt = NewEntry(it, "", true) } }
        cmd("File: Open Folder…") { openFolder.launch(null) }
        cmd("File: Open File…") { openFiles.launch(arrayOf("*/*")) }
        cmd("File: Open Francode Projects (app storage)") { vm.openInternalWorkspace() }
        cmd("File: Go to File…", "Ctrl+P") { showQuick = true }
        if (t != null) {
            cmd("File: Save", "Ctrl+S") { vm.saveActive() }
            cmd("File: Save All", "Ctrl+Shift+S") { vm.saveAll(); vm.toast("Saved all files") }
            cmd("File: Save As…") { saveAs.launch(t.name) }
            cmd("File: Close Tab", "Ctrl+W") { vm.requestClose(t) }
            cmd("File: Close All Tabs") { vm.closeAll() }
            cmd("Edit: Undo", "Ctrl+Z") { t.undo() }
            cmd("Edit: Redo", "Ctrl+Y") { t.redo() }
            cmd("Edit: Find", "Ctrl+F") { vm.openFind(false) }
            cmd("Edit: Replace", "Ctrl+H") { vm.openFind(true) }
            cmd("Edit: Go to Line…", "Ctrl+G") { showGoto = true }
            cmd("Edit: Toggle Line Comment", "Ctrl+/") { vm.toggleComment() }
            cmd("Edit: Duplicate Line", "Ctrl+D") { vm.duplicateLine() }
            cmd("Edit: Delete Line", "Ctrl+Shift+K") { vm.deleteLine() }
            cmd("Edit: Move Line Up", "Alt+↑") { vm.moveLine(true) }
            cmd("Edit: Move Line Down", "Alt+↓") { vm.moveLine(false) }
            cmd("Edit: Indent", "Tab") { vm.indent(false) }
            cmd("Edit: Outdent", "Shift+Tab") { vm.indent(true) }
            cmd("Edit: Select All") { vm.selectAll() }
            cmd("Edit: Trim Trailing Whitespace") { vm.trimTrailingWhitespace() }
            if (t.lang.id == "json") cmd("Edit: Format JSON") { vm.formatJson() }
            if (vm.canPreview(t)) cmd("View: Preview", "▶") { vm.previewTab = t }
            cmd("View: Change Language Mode…") { showLangs = true }
        }
        cmd("Search: Find in Files") { openSide(1) }
        cmd("View: Show Explorer") { openSide(0) }
        cmd("View: Toggle Word Wrap") { vm.setWordWrap(!vm.wordWrap) }
        cmd("View: Toggle Line Numbers") { vm.setLineNumbers(!vm.lineNumbers) }
        cmd("View: Zoom In (Larger Font)") { vm.setFontSize(vm.fontSize + 1) }
        cmd("View: Zoom Out (Smaller Font)") { vm.setFontSize(vm.fontSize - 1) }
        cmd("Preferences: Color Theme…") { showThemes = true }
        cmd("Preferences: Toggle Auto Save", if (vm.autoSave) "on" else "off") { vm.setAutoSave(!vm.autoSave) }
        cmd("Preferences: Open Settings") { showSettings = true }
        return out
    }

    // ---- main column
    val main: @Composable () -> Unit = {
        val tab = vm.active
        val imeVisible = WindowInsets.isImeVisible
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(
                vm, theme, wide,
                onMenu = { if (wide) sidebarVisible = !sidebarVisible else openSide(sideMode) },
                onPalette = { showPalette = true },
            )
            if (vm.tabs.isNotEmpty()) TabBar(vm, theme) else HorizontalDivider(color = theme.border)
            Box(Modifier.weight(1f).fillMaxWidth().background(theme.bg)) {
                if (tab == null) {
                    Welcome(
                        vm, theme,
                        onOpenFolder = { openFolder.launch(null) },
                        onOpenFile = { openFiles.launch(arrayOf("*/*")) },
                        onNewFile = { vm.root?.let { vm.entryPrompt = NewEntry(it, "", false) } },
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (vm.find.visible) FindBar(vm, theme)
                        key(tab) {
                            CodeEditor(tab, vm, theme, actions, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                vm.previewTab?.let { PreviewPane(it, theme) { vm.previewTab = null } }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }
            if (tab != null) {
                if (imeVisible) SymbolBar(tab, theme)
                else StatusBar(tab, vm, theme) { showLangs = true }
            }
        }
    }

    val side: @Composable () -> Unit = {
        SidePanel(
            vm, theme, sideMode, onMode = { sideMode = it },
            onOpenFolder = { openFolder.launch(null) },
            onNavigate = { closeSide() },
        )
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (sidebarVisible) {
                Box(Modifier.width(300.dp).fillMaxHeight().background(theme.panel).systemBarsPadding()) { side() }
                VerticalDivider(color = theme.border)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { main() }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen || vm.active == null,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = theme.panel, modifier = Modifier.width(310.dp)) { side() }
            },
            content = main,
        )
    }

    // ---- dialogs & overlays
    if (showPalette) PickerDialog("Type a command", commands(), theme, onDismiss = { showPalette = false })

    if (showQuick) {
        val items = quickFiles.map { f ->
            PickItem(f.name, subtitle = f.path) { vm.openNode(f) }
        }
        PickerDialog("Go to file…", items, theme, onDismiss = { showQuick = false }, loading = quickLoading)
    }

    if (showThemes) {
        val items = EditorThemes.all.map { t ->
            PickItem(t.name, hint = if (t.name == vm.themeName) "✓" else "") { vm.setTheme(t.name) }
        }
        PickerDialog("Select color theme", items, theme, onDismiss = { showThemes = false })
    }

    if (showLangs) {
        val t = vm.active
        if (t != null) {
            val items = Languages.all.map { l ->
                PickItem(l.label, hint = if (l.id == t.lang.id) "✓" else "") { t.langId = l.id }
            }
            PickerDialog("Select language mode", items, theme, onDismiss = { showLangs = false })
        } else showLangs = false
    }

    if (showGoto) {
        val t = vm.active
        if (t == null) showGoto = false
        else PromptDialog(
            title = "Go to line", label = "Line or line:column", confirm = "Go",
            keyboardType = KeyboardType.Number,
            message = "Lines: 1 – ${t.value.text.count { it == '\n' } + 1}",
            onDismiss = { showGoto = false },
            onConfirm = { s ->
                val parts = s.split(':')
                val line = parts[0].trim().toIntOrNull()
                val col = parts.getOrNull(1)?.trim()?.toIntOrNull()?.minus(1) ?: 0
                if (line != null) t.goToLine(line, col)
                showGoto = false
            },
        )
    }

    if (showSettings) SettingsDialog(vm, theme) { showSettings = false }

    vm.entryPrompt?.let { p ->
        PromptDialog(
            title = if (p.isDir) "New folder" else "New file",
            label = if (p.isDir) "Folder name" else "File name (e.g. src/main.py)",
            confirm = "Create",
            message = "In: " + (if (p.path.isEmpty()) vm.rootName else p.path),
            onDismiss = { vm.entryPrompt = null },
            onConfirm = { name -> vm.createEntry(p.dir, p.path, name, p.isDir); vm.entryPrompt = null },
        )
    }

    vm.pendingClose?.let { t ->
        ConfirmDialog(
            title = "Unsaved changes",
            message = "Save changes to ${t.name} before closing?",
            confirm = "Save",
            neutral = "Don't save",
            onDismiss = { vm.pendingClose = null },
            onNeutral = { vm.pendingClose = null; vm.closeTab(t, force = true) },
            onConfirm = { vm.pendingClose = null; vm.saveAsync(t); vm.closeTab(t, force = true) },
        )
    }
}

@Composable
private fun TopBar(
    vm: EditorViewModel,
    theme: EditorTheme,
    wide: Boolean,
    onMenu: () -> Unit,
    onPalette: () -> Unit,
) {
    val tab = vm.active
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(theme.panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Explorer", tint = theme.fg) }
        Column(Modifier.weight(1f)) {
            Text(
                tab?.name ?: "Francode", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(vm.rootName, fontSize = 11.sp, color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (vm.canPreview(tab)) {
            IconButton(onClick = { vm.previewTab = tab }) { Icon(Icons.Default.PlayArrow, "Preview", tint = theme.accent) }
        }
        if (tab != null) {
            IconButton(onClick = { vm.saveActive() }) {
                Icon(Icons.Default.Save, "Save", tint = if (tab.dirty) theme.accent else theme.muted)
            }
            IconButton(onClick = { vm.openFind(false) }) { Icon(Icons.Default.Search, "Find", tint = theme.fg) }
        }
        IconButton(onClick = onPalette) { Icon(Icons.Default.MoreVert, "Command palette", tint = theme.fg) }
    }
}

@Composable
private fun Welcome(
    vm: EditorViewModel,
    theme: EditorTheme,
    onOpenFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("</>", fontSize = 48.sp, color = theme.accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("Francode", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("Code editing, anywhere.", fontSize = 14.sp, color = theme.muted)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenFolder, modifier = Modifier.width(220.dp)) { Text("Open Folder") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenFile, modifier = Modifier.width(220.dp)) { Text("Open File") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNewFile, modifier = Modifier.width(220.dp)) { Text("New File") }

        if (vm.recents.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("Recent folders", fontSize = 12.sp, color = theme.muted)
            for (r in vm.recents) {
                Text(
                    r.name, color = theme.accent, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { vm.openRecent(r) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.background(theme.panel, RoundedCornerShape(10.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Keyboard shortcuts", fontSize = 12.sp, color = theme.muted)
            for ((k, d) in listOf(
                "Ctrl+P" to "Go to file", "Ctrl+Shift+P" to "Command palette", "Ctrl+S" to "Save",
                "Ctrl+F / H" to "Find / replace", "Ctrl+/" to "Toggle comment", "Ctrl+G" to "Go to line",
            )) {
                Row {
                    Text(k, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(110.dp))
                    Text(d, fontSize = 12.sp, color = theme.muted)
                }
            }
        }
    }
}
