package com.francode.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** One open editor tab. */
@Stable
class Tab(uri: Uri, name: String, text: String, val crlf: Boolean) {
    var uri by mutableStateOf(uri)
    var name by mutableStateOf(name)
    var value by mutableStateOf(TextFieldValue(text))
    var savedText by mutableStateOf(text)
    var revealNonce by mutableIntStateOf(0)
    /** Language override chosen by the user (null = detect from file name). */
    var langId by mutableStateOf<String?>(null)
    /** Remembered scroll offsets so switching tabs keeps your place. */
    var scrollY = 0
    var scrollX = 0

    val dirty: Boolean get() = value.text != savedText
    val lang: LangDef get() = langId?.let { Languages.ofId(it) } ?: Languages.forName(name)

    private val undoStack = ArrayList<TextFieldValue>()
    private val redoStack = ArrayList<TextFieldValue>()
    private var lastEditTime = 0L

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Typing: consecutive small edits are merged into one undo step. */
    fun typed(new: TextFieldValue) {
        val old = value
        if (new.text != old.text) {
            val now = SystemClock.uptimeMillis()
            val merge = abs(new.text.length - old.text.length) <= 1 &&
                now - lastEditTime < 700 && undoStack.isNotEmpty()
            if (!merge) push(old)
            lastEditTime = now
            redoStack.clear()
        }
        value = new
    }

    /** Programmatic edit: always its own undo step. */
    fun commit(e: Edit) {
        val old = value
        if (e.text != old.text) {
            push(old)
            redoStack.clear()
        }
        lastEditTime = 0
        value = TextFieldValue(e.text, TextRange(e.selStart, e.selEnd))
    }

    private fun push(v: TextFieldValue) {
        undoStack.add(v)
        if (undoStack.size > 300) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(value)
        value = undoStack.removeAt(undoStack.lastIndex)
        lastEditTime = 0
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(value)
        value = redoStack.removeAt(redoStack.lastIndex)
        lastEditTime = 0
    }

    fun edit(): Edit = Edit(value.text, value.selection.start, value.selection.end)

    fun select(start: Int, end: Int = start) {
        val len = value.text.length
        value = value.copy(selection = TextRange(start.coerceIn(0, len), end.coerceIn(0, len)))
    }

    fun reveal() { revealNonce++ }

    fun insert(text: String, cursorBack: Int = 0) {
        val v = value
        val s = v.selection.min
        val e = v.selection.max
        val nt = v.text.substring(0, s) + text + v.text.substring(e)
        val c = s + text.length - cursorBack
        commit(Edit(nt, c))
    }

    fun moveCursor(delta: Int) {
        val v = value
        val c = (v.selection.end + delta).coerceIn(0, v.text.length)
        value = v.copy(selection = TextRange(c))
    }

    fun goToLine(line: Int, col: Int = 0) {
        val off = TextOps.offsetOf(value.text, line, col)
        select(off)
        reveal()
    }
}

@Stable
class FindState {
    var visible by mutableStateOf(false)
    var replaceMode by mutableStateOf(false)
    var query by mutableStateOf("")
    var replacement by mutableStateOf("")
    var caseSensitive by mutableStateOf(false)
    var regex by mutableStateOf(false)
    var wholeWord by mutableStateOf(false)
    var current by mutableIntStateOf(0)
}

class Recent(val uri: String, val name: String)

/** Request to create a file/folder inside [dir]; shown as a dialog by the main screen. */
class NewEntry(val dir: DocumentFile, val path: String, val isDir: Boolean)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("francode", Context.MODE_PRIVATE)
    private val ctx: Context get() = getApplication<Application>()

    // ---------------------------------------------------------------- settings
    var themeName by mutableStateOf(prefs.getString("theme", "Francode Dark") ?: "Francode Dark")
        private set
    var fontSize by mutableIntStateOf(prefs.getInt("font", 14))
        private set
    var tabSize by mutableIntStateOf(prefs.getInt("tab", 4))
        private set
    var wordWrap by mutableStateOf(prefs.getBoolean("wrap", false))
        private set
    var lineNumbers by mutableStateOf(prefs.getBoolean("lines", true))
        private set
    var autoSave by mutableStateOf(prefs.getBoolean("autosave", true))
        private set
    var autoClose by mutableStateOf(prefs.getBoolean("autoclose", true))
        private set
    var highlightLine by mutableStateOf(prefs.getBoolean("hline", true))
        private set

    fun setTheme(n: String) { themeName = n; prefs.edit().putString("theme", n).apply() }
    fun setFontSize(v: Int) { fontSize = v.coerceIn(9, 28); prefs.edit().putInt("font", fontSize).apply() }
    fun setTabSize(v: Int) { tabSize = v; prefs.edit().putInt("tab", v).apply() }
    fun setWordWrap(v: Boolean) { wordWrap = v; prefs.edit().putBoolean("wrap", v).apply() }
    fun setLineNumbers(v: Boolean) { lineNumbers = v; prefs.edit().putBoolean("lines", v).apply() }
    fun setAutoSave(v: Boolean) { autoSave = v; prefs.edit().putBoolean("autosave", v).apply() }
    fun setAutoClose(v: Boolean) { autoClose = v; prefs.edit().putBoolean("autoclose", v).apply() }
    fun setHighlightLine(v: Boolean) { highlightLine = v; prefs.edit().putBoolean("hline", v).apply() }

    val indentUnit: String get() = " ".repeat(tabSize)

    // ---------------------------------------------------------------- messages
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    fun toast(s: String) { messages.tryEmit(s) }

    // ---------------------------------------------------------------- workspace
    var root by mutableStateOf<DocumentFile?>(null)
        private set
    var rootName by mutableStateOf("")
        private set
    val children = mutableStateMapOf<String, List<FileNode>>()
    val expanded = mutableStateMapOf<String, Boolean>()
    private val dirIndex = HashMap<String, Pair<DocumentFile, String>>()
    private var fileIndex: List<FileNode>? = null
    var recents by mutableStateOf(loadRecents())
        private set

    // ---------------------------------------------------------------- tabs
    val tabs = mutableStateListOf<Tab>()
    var activeIndex by mutableIntStateOf(-1)
        private set
    val active: Tab? get() = tabs.getOrNull(activeIndex)
    var pendingClose by mutableStateOf<Tab?>(null)
    var entryPrompt by mutableStateOf<NewEntry?>(null)

    // ---------------------------------------------------------------- find
    val find = FindState()

    val findMatches: List<IntRange> by derivedStateOf {
        val t = active
        if (!find.visible || t == null) emptyList()
        else computeMatches(t.value.text)
    }

    // ---------------------------------------------------------------- project search
    var searchQuery by mutableStateOf("")
    var searchCase by mutableStateOf(false)
    var searchRegex by mutableStateOf(false)
    var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchDone by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    // ---------------------------------------------------------------- preview
    var previewTab by mutableStateOf<Tab?>(null)

    init {
        restoreSession()
    }

    // ================================================================ workspace
    private fun restoreSession() {
        val saved = prefs.getString("ws", null)
        var doc: DocumentFile? = null
        var label = "Francode"
        if (saved != null && saved != "internal") {
            val uri = Uri.parse(saved)
            val ok = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            if (ok) {
                doc = DocumentFile.fromTreeUri(ctx, uri)
                if (doc != null && !doc.exists()) doc = null
                label = doc?.name ?: label
            }
        }
        if (doc == null) doc = Fs.internalRoot(ctx)
        openWorkspace(doc, label, remember = false)

        val uris = prefs.getString("tabs", "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        val act = prefs.getInt("active", 0)
        viewModelScope.launch {
            for (u in uris) openFileSuspend(Uri.parse(u), select = false, quiet = true)
            if (tabs.isNotEmpty()) activeIndex = act.coerceIn(0, tabs.lastIndex)
        }
    }

    fun openWorkspace(doc: DocumentFile, label: String, remember: Boolean = true) {
        root = doc
        rootName = if (doc.uri.scheme == "file") "Francode Projects" else label
        children.clear(); expanded.clear(); dirIndex.clear(); fileIndex = null
        expanded[doc.uri.toString()] = true
        loadChildren(doc, "")
        searchHits = emptyList(); searchDone = false
        if (remember) {
            prefs.edit().putString("ws", if (doc.uri.scheme == "file") "internal" else doc.uri.toString()).apply()
            if (doc.uri.scheme != "file") addRecent(doc.uri.toString(), label)
        }
    }

    fun openTreeUri(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        val doc = DocumentFile.fromTreeUri(ctx, uri)
        if (doc == null) { toast("Cannot open that folder"); return }
        openWorkspace(doc, doc.name ?: "Folder")
    }

    fun openInternalWorkspace() = openWorkspace(Fs.internalRoot(ctx), "Francode Projects")

    fun openRecent(r: Recent) = openTreeUri(Uri.parse(r.uri))

    private fun loadRecents(): List<Recent> =
        (prefs.getString("recent", "") ?: "").split('\n').mapNotNull {
            val p = it.split('\t')
            if (p.size == 2 && p[0].isNotBlank()) Recent(p[0], p[1]) else null
        }

    private fun addRecent(uri: String, name: String) {
        val l = (listOf(Recent(uri, name)) + recents.filter { it.uri != uri }).take(6)
        recents = l
        prefs.edit().putString("recent", l.joinToString("\n") { it.uri + "\t" + it.name.replace('\t', ' ') }).apply()
    }

    fun loadChildren(dir: DocumentFile, path: String) {
        val key = dir.uri.toString()
        dirIndex[key] = dir to path
        viewModelScope.launch {
            val l = try { withContext(Dispatchers.IO) { Fs.list(dir, path) } } catch (e: Exception) { emptyList() }
            children[key] = l
        }
    }

    fun toggleDir(n: FileNode) {
        val k = n.key
        if (expanded[k] == true) expanded[k] = false
        else {
            expanded[k] = true
            loadChildren(n.doc, n.path)
        }
    }

    fun reloadDir(key: String) {
        dirIndex[key]?.let { (d, p) -> loadChildren(d, p) }
        fileIndex = null
    }

    fun refreshAll() {
        fileIndex = null
        val r = root ?: return
        loadChildren(r, "")
        for ((k, v) in expanded.toMap()) if (v) dirIndex[k]?.let { (d, p) -> loadChildren(d, p) }
    }

    fun createEntry(dir: DocumentFile, dirPath: String, name: String, isDir: Boolean) {
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) { Fs.create(dir, name, isDir) }
                val key = dir.uri.toString()
                dirIndex[key] = dir to dirPath
                expanded[key] = true
                reloadDir(key)
                if (!isDir) openFile(created.uri)
            } catch (e: Exception) {
                toast(e.message ?: "Could not create")
            }
        }
    }

    fun renameEntry(n: FileNode, newName: String) {
        viewModelScope.launch {
            val oldKey = n.key
            val ok = withContext(Dispatchers.IO) { try { n.doc.renameTo(newName) } catch (e: Exception) { false } }
            if (!ok) { toast("Rename failed"); return@launch }
            val newUri = n.doc.uri
            if (n.isDir) {
                closeTabsUnder(oldKey)
                expanded.remove(oldKey); children.remove(oldKey); dirIndex.remove(oldKey)
            } else {
                tabs.firstOrNull { it.uri.toString() == oldKey }?.let { it.uri = newUri; it.name = newName }
            }
            reloadDir(n.parentKey)
        }
    }

    fun deleteEntry(n: FileNode) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { try { n.doc.delete() } catch (e: Exception) { false } }
            if (!ok) { toast("Delete failed"); return@launch }
            if (n.isDir) { closeTabsUnder(n.key); expanded.remove(n.key); children.remove(n.key) }
            else tabs.firstOrNull { it.uri == n.uri }?.let { closeTab(it, force = true) }
            reloadDir(n.parentKey)
        }
    }

    private fun closeTabsUnder(dirKey: String) {
        tabs.filter { it.uri.toString().startsWith(dirKey) }.forEach { closeTab(it, force = true) }
    }

    suspend fun fileList(): List<FileNode> {
        fileIndex?.let { return it }
        val r = root ?: return emptyList()
        val l = Fs.walk(r)
        fileIndex = l
        return l
    }

    // ================================================================ tabs
    fun openNode(n: FileNode) { if (!n.isDir) openFile(n.uri) }

    fun openFile(uri: Uri, line: Int? = null) {
        val i = tabs.indexOfFirst { it.uri == uri }
        if (i >= 0) {
            select(i)
            if (line != null) tabs[i].goToLine(line)
            return
        }
        viewModelScope.launch {
            openFileSuspend(uri, select = true, quiet = false)
            if (line != null) active?.goToLine(line)
        }
    }

    private suspend fun openFileSuspend(uri: Uri, select: Boolean, quiet: Boolean) {
        try {
            val (name, loaded) = withContext(Dispatchers.IO) { Fs.displayName(ctx, uri) to Fs.read(ctx, uri) }
            val tab = Tab(uri, name, loaded.text, loaded.crlf)
            tabs.add(tab)
            if (select) activeIndex = tabs.lastIndex
            if (select) saveSession()
        } catch (e: Exception) {
            if (!quiet) toast("Can't open file: ${e.message}")
        }
    }

    fun select(i: Int) {
        if (i !in tabs.indices) return
        if (autoSave) active?.let { if (it.dirty) saveAsync(it) }
        activeIndex = i
        saveSession()
    }

    fun requestClose(tab: Tab) {
        if (tab.dirty && !autoSave) pendingClose = tab else closeTab(tab, force = false)
    }

    fun closeTab(tab: Tab, force: Boolean) {
        if (!force && tab.dirty && autoSave) saveAsync(tab)
        val i = tabs.indexOf(tab)
        if (i < 0) return
        tabs.removeAt(i)
        if (previewTab === tab) previewTab = null
        activeIndex = when {
            tabs.isEmpty() -> -1
            i < activeIndex -> activeIndex - 1
            activeIndex >= tabs.size -> tabs.lastIndex
            else -> activeIndex
        }
        saveSession()
    }

    fun closeAll() {
        tabs.toList().forEach { if (it.dirty && autoSave) saveAsync(it) }
        tabs.clear(); activeIndex = -1; previewTab = null
        saveSession()
    }

    // ================================================================ editing
    fun onEdit(tab: Tab, new: androidx.compose.ui.text.input.TextFieldValue) {
        val old = tab.value
        if (new.text == old.text) { tab.value = new; return }
        val smart = TextOps.smartEdit(
            Edit(old.text, old.selection.start, old.selection.end),
            Edit(new.text, new.selection.start, new.selection.end),
            tab.lang, indentUnit, autoClose,
        )
        if (smart != null) tab.commit(smart) else tab.typed(new)
    }

    fun indent(outdent: Boolean) { active?.let { it.commit(TextOps.indent(it.edit(), indentUnit, outdent)) } }
    fun toggleComment() { active?.let { it.commit(TextOps.toggleComment(it.edit(), it.lang)) } }
    fun duplicateLine() { active?.let { it.commit(TextOps.duplicateLines(it.edit())) } }
    fun deleteLine() { active?.let { it.commit(TextOps.deleteLines(it.edit())) } }
    fun moveLine(up: Boolean) { active?.let { it.commit(TextOps.moveLines(it.edit(), up)) } }
    fun selectAll() { active?.let { it.select(0, it.value.text.length) } }

    fun formatJson() {
        val t = active ?: return
        try {
            val src = t.value.text.trim()
            val pretty = if (src.startsWith("[")) org.json.JSONArray(src).toString(2) else org.json.JSONObject(src).toString(2)
            t.commit(Edit(pretty + "\n", 0))
        } catch (e: Exception) {
            toast("Invalid JSON: ${e.message?.take(80)}")
        }
    }

    fun trimTrailingWhitespace() {
        val t = active ?: return
        val nt = t.value.text.lines().joinToString("\n") { it.trimEnd() }
        if (nt != t.value.text) t.commit(Edit(nt, minOf(t.value.selection.start, nt.length)))
    }

    // ================================================================ saving
    fun saveActive() { active?.let { saveAsync(it, notify = true) } }

    fun saveAsync(tab: Tab, notify: Boolean = false) {
        val text = tab.value.text
        val uri = tab.uri
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Fs.write(ctx, uri, text, tab.crlf) }
                tab.savedText = text
                if (notify) toast("Saved ${tab.name}")
            } catch (e: Exception) {
                toast("Couldn't save ${tab.name}: ${e.message}. Try Save As.")
            }
        }
    }

    fun saveAll() { tabs.filter { it.dirty }.forEach { saveAsync(it) } }

    fun saveActiveAs(uri: Uri) {
        val t = active ?: return
        val text = t.value.text
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Fs.write(ctx, uri, text, t.crlf) }
                t.uri = uri
                t.name = Fs.displayName(ctx, uri)
                t.savedText = text
                toast("Saved as ${t.name}")
                refreshAll()
                saveSession()
            } catch (e: Exception) {
                toast("Save As failed: ${e.message}")
            }
        }
    }

    fun onBackground() {
        if (autoSave) saveAll()
        saveSession()
    }

    private fun saveSession() {
        prefs.edit()
            .putString("tabs", tabs.joinToString("\n") { it.uri.toString() })
            .putInt("active", activeIndex)
            .apply()
    }

    // ================================================================ find / replace
    private fun buildRegex(): Regex? {
        val q = find.query
        if (q.isEmpty()) return null
        return try {
            var p = if (find.regex) q else Regex.escape(q)
            if (find.wholeWord) p = "\\b(?:$p)\\b"
            Regex(p, if (find.caseSensitive) setOf(RegexOption.MULTILINE) else setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        } catch (e: Exception) {
            null
        }
    }

    private fun computeMatches(text: String): List<IntRange> {
        val re = buildRegex() ?: return emptyList()
        val out = ArrayList<IntRange>()
        for (m in re.findAll(text)) {
            if (m.value.isEmpty()) continue
            out.add(m.range)
            if (out.size >= 5000) break
        }
        return out
    }

    val findQueryInvalid: Boolean
        get() = find.regex && find.query.isNotEmpty() && buildRegex() == null

    fun openFind(replace: Boolean) {
        val t = active ?: return
        find.visible = true
        find.replaceMode = replace
        val sel = t.value.selection
        if (!sel.collapsed && sel.length < 100) {
            val s = t.value.text.substring(sel.min, sel.max)
            if (!s.contains('\n')) find.query = s
        }
    }

    fun closeFind() { find.visible = false }

    fun findStep(forward: Boolean) {
        val t = active ?: return
        val ms = findMatches
        if (ms.isEmpty()) return
        val sel = t.value.selection
        val idx = if (forward) {
            val from = sel.max
            ms.indexOfFirst { it.first >= from }.let { if (it < 0) 0 else it }
        } else {
            val from = sel.min
            ms.indexOfLast { it.last + 1 <= from }.let { if (it < 0) ms.lastIndex else it }
        }
        selectMatch(t, ms, idx)
    }

    /** After the query changes: jump to the first match at or after the cursor. */
    fun findFromCursor() {
        val t = active ?: return
        val ms = findMatches
        if (ms.isEmpty()) return
        val from = t.value.selection.min
        selectMatch(t, ms, ms.indexOfFirst { it.first >= from }.let { if (it < 0) 0 else it })
    }

    private fun selectMatch(t: Tab, ms: List<IntRange>, idx: Int) {
        find.current = idx
        t.select(ms[idx].first, ms[idx].last + 1)
        t.reveal()
    }

    private fun expand(re: Regex, matched: String): String =
        if (find.regex) re.replace(matched, find.replacement)
        else find.replacement

    fun replaceCurrent() {
        val t = active ?: return
        val re = buildRegex() ?: return
        val ms = findMatches
        if (ms.isEmpty()) return
        val sel = t.value.selection
        val cur = ms.firstOrNull { it.first == sel.min && it.last + 1 == sel.max }
        if (cur == null) { findStep(true); return }
        val text = t.value.text
        val rep = expand(re, text.substring(cur.first, cur.last + 1))
        val nt = text.substring(0, cur.first) + rep + text.substring(cur.last + 1)
        t.commit(Edit(nt, cur.first + rep.length))
        findStep(true)
    }

    fun replaceAll() {
        val t = active ?: return
        val re = buildRegex() ?: return
        val n = findMatches.size
        if (n == 0) return
        val text = t.value.text
        val nt = if (find.regex) re.replace(text, find.replacement)
        else re.replace(text, Regex.escapeReplacement(find.replacement))
        t.commit(Edit(nt, 0))
        toast("Replaced $n occurrence${if (n == 1) "" else "s"}")
    }

    // ================================================================ project search
    fun runSearch() {
        val q = searchQuery
        if (q.isEmpty()) return
        val re = try {
            val p = if (searchRegex) q else Regex.escape(q)
            Regex(p, if (searchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (e: Exception) {
            toast("Invalid regular expression"); return
        }
        searchJob?.cancel()
        searching = true; searchDone = false
        searchJob = viewModelScope.launch {
            try {
                val files = fileList()
                searchHits = Fs.search(ctx, files, re)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) toast("Search failed: ${e.message}")
            } finally {
                searching = false; searchDone = true
            }
        }
    }

    // ================================================================ preview
    fun canPreview(t: Tab?): Boolean {
        val id = t?.lang?.id ?: return false
        return id == "html" || id == "markdown" || t.name.endsWith(".svg", true)
    }
}
