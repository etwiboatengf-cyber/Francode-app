package com.francode.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface SideDialog {
    class Rename(val node: FileNode) : SideDialog
    class Delete(val node: FileNode) : SideDialog
}

/** Sidebar with two views: the file explorer and project-wide search. */
@Composable
fun SidePanel(
    vm: EditorViewModel,
    theme: EditorTheme,
    mode: Int,
    onMode: (Int) -> Unit,
    onOpenFolder: () -> Unit,
    onNavigate: () -> Unit,
) {
    var dialog by remember { mutableStateOf<SideDialog?>(null) }

    Column(Modifier.fillMaxSize()) {
        // ---- header
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vm.rootName.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val root = vm.root
            IconButton(onClick = { root?.let { vm.entryPrompt = NewEntry(it, "", false) } }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.NoteAdd, "New file", Modifier.size(20.dp), tint = theme.fg)
            }
            IconButton(onClick = { root?.let { vm.entryPrompt = NewEntry(it, "", true) } }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.CreateNewFolder, "New folder", Modifier.size(20.dp), tint = theme.fg)
            }
            IconButton(onClick = { vm.refreshAll() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", Modifier.size(20.dp), tint = theme.fg)
            }
            IconButton(onClick = onOpenFolder, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FolderOpen, "Open folder", Modifier.size(20.dp), tint = theme.fg)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            SideTab("Files", Icons.Default.Folder, mode == 0, theme) { onMode(0) }
            Spacer(Modifier.width(6.dp))
            SideTab("Search", Icons.Default.Search, mode == 1, theme) { onMode(1) }
        }
        HorizontalDivider(color = theme.border)

        if (mode == 0) Explorer(vm, theme, onNavigate, { dialog = it })
        else SearchView(vm, theme, onNavigate)
    }

    when (val d = dialog) {
        is SideDialog.Rename -> PromptDialog(
            title = "Rename", label = "New name", initial = d.node.name, confirm = "Rename",
            selectBaseName = !d.node.isDir,
            onDismiss = { dialog = null },
            onConfirm = { vm.renameEntry(d.node, it); dialog = null },
        )
        is SideDialog.Delete -> ConfirmDialog(
            title = "Delete ${if (d.node.isDir) "folder" else "file"}?",
            message = "\"${d.node.name}\" will be permanently deleted." +
                if (d.node.isDir) " This includes everything inside it." else "",
            confirm = "Delete",
            onDismiss = { dialog = null },
            onConfirm = { vm.deleteEntry(d.node); dialog = null },
        )
        null -> {}
    }
}

@Composable
private fun SideTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, theme: EditorTheme, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (on) theme.accent.copy(alpha = 0.2f) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (on) theme.accent else theme.muted)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = if (on) theme.fg else theme.muted)
    }
}

// ------------------------------------------------------------------ explorer

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Explorer(
    vm: EditorViewModel,
    theme: EditorTheme,
    onNavigate: () -> Unit,
    showDialog: (SideDialog) -> Unit,
) {
    val rows by remember(vm) {
        derivedStateOf {
            val out = ArrayList<Pair<FileNode, Int>>()
            fun rec(key: String, depth: Int) {
                val list = vm.children[key] ?: return
                for (n in list) {
                    out.add(n to depth)
                    if (n.isDir && vm.expanded[n.key] == true) rec(n.key, depth + 1)
                }
            }
            vm.root?.let { rec(it.uri.toString(), 0) }
            out
        }
    }

    if (rows.isEmpty()) {
        Text("This folder is empty.\nUse the buttons above to create a file.", color = theme.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.first.key }) { (n, depth) ->
            var menu by remember { mutableStateOf(false) }
            val isActive = !n.isDir && vm.active?.uri == n.uri
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isActive) theme.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (n.isDir) vm.toggleDir(n) else { vm.openNode(n); onNavigate() }
                            },
                            onLongClick = { menu = true },
                        )
                        .padding(start = (8 + depth * 14).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (n.isDir) {
                        val open = vm.expanded[n.key] == true
                        Icon(
                            if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            null, Modifier.size(18.dp), tint = theme.muted,
                        )
                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp), tint = theme.accent)
                    } else {
                        Spacer(Modifier.width(18.dp))
                        Icon(Icons.Default.Description, null, Modifier.size(18.dp), tint = theme.muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(n.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.fg)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (n.isDir) {
                        DropdownMenuItem(text = { Text("New file…") }, onClick = {
                            menu = false; vm.entryPrompt = NewEntry(n.doc, n.path, false)
                        })
                        DropdownMenuItem(text = { Text("New folder…") }, onClick = {
                            menu = false; vm.entryPrompt = NewEntry(n.doc, n.path, true)
                        })
                    }
                    DropdownMenuItem(text = { Text("Rename…") }, onClick = { menu = false; showDialog(SideDialog.Rename(n)) })
                    DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; showDialog(SideDialog.Delete(n)) })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ project search

@Composable
private fun SearchView(vm: EditorViewModel, theme: EditorTheme, onNavigate: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallField(
                vm.searchQuery, { vm.searchQuery = it }, "Search in files", theme, Modifier.weight(1f),
                imeAction = ImeAction.Search, onDone = { vm.runSearch() },
            )
            Spacer(Modifier.width(4.dp))
            ToggleChip("Aa", vm.searchCase, theme) { vm.searchCase = !vm.searchCase }
            ToggleChip(".*", vm.searchRegex, theme) { vm.searchRegex = !vm.searchRegex }
        }
        val status = when {
            vm.searching -> "Searching…"
            vm.searchDone && vm.searchHits.isEmpty() -> "No results"
            vm.searchDone -> "${vm.searchHits.size}${if (vm.searchHits.size >= 500) "+" else ""} results"
            else -> "Press search on the keyboard to start"
        }
        Text(status, fontSize = 12.sp, color = theme.muted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.searchHits) { h ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { vm.openFile(h.node.uri, h.line); onNavigate() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${h.node.path}:${h.line}", fontSize = 11.sp, color = theme.accent,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        h.preview, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = theme.fg,
                    )
                }
            }
        }
    }
}
