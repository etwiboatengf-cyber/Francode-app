package com.francode.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** One entry in the file tree. [parentKey] is the uri string of the directory listing it came from. */
class FileNode(
    val doc: DocumentFile,
    val name: String,
    val isDir: Boolean,
    val path: String,
    val parentKey: String,
) {
    val uri: Uri get() = doc.uri
    val key: String get() = doc.uri.toString()
}

class LoadedFile(val text: String, val crlf: Boolean)

class SearchHit(val node: FileNode, val line: Int, val preview: String)

object Fs {
    /** Folders skipped when indexing the workspace for Quick Open / Search. */
    private val SKIP_DIRS = setOf(
        ".git", "node_modules", ".gradle", "build", ".idea", "__pycache__", ".dart_tool",
        ".venv", "venv", "dist", ".next", "Pods", ".cxx",
    )
    private val BINARY_EXT = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "pdf", "zip", "jar", "aar", "apk", "aab",
        "so", "class", "dex", "mp3", "mp4", "mkv", "mov", "wav", "ogg", "ttf", "otf", "woff", "woff2",
        "7z", "gz", "tar", "rar", "bin", "exe", "dll", "o", "a", "keystore", "jks",
    )
    const val MAX_FILE_BYTES = 5L * 1024 * 1024

    fun isBinaryName(name: String) = name.substringAfterLast('.', "").lowercase() in BINARY_EXT

    fun list(dir: DocumentFile, parentPath: String): List<FileNode> {
        val key = dir.uri.toString()
        return dir.listFiles().map {
            val n = it.name ?: "?"
            FileNode(it, n, it.isDirectory, if (parentPath.isEmpty()) n else "$parentPath/$n", key)
        }.sortedWith(compareByDescending<FileNode> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun displayName(ctx: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        return DocumentFile.fromSingleUri(ctx, uri)?.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "untitled"
    }

    fun read(ctx: Context, uri: Uri): LoadedFile {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")
        if (bytes.size > MAX_FILE_BYTES) throw IllegalStateException("File is larger than 5 MB")
        val probe = minOf(bytes.size, 8000)
        for (i in 0 until probe) if (bytes[i].toInt() == 0) throw IllegalStateException("Binary file")
        var s = bytes.toString(Charsets.UTF_8)
        if (s.startsWith("﻿")) s = s.substring(1)
        val crlf = s.contains("\r\n")
        if (s.contains('\r')) s = s.replace("\r\n", "\n").replace('\r', '\n')
        return LoadedFile(s, crlf)
    }

    fun write(ctx: Context, uri: Uri, text: String, crlf: Boolean) {
        val out = if (crlf) text.replace("\n", "\r\n") else text
        val os = ctx.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Cannot open file for writing")
        os.use { it.write(out.toByteArray(Charsets.UTF_8)) }
    }

    /** Creates [name] (may contain '/' to create nested folders) inside [dir]. */
    fun create(dir: DocumentFile, name: String, isDir: Boolean): DocumentFile {
        val parts = name.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.isNotEmpty()) { "Empty name" }
        var cur = dir
        for (p in parts.dropLast(1)) {
            val existing = cur.findFile(p)
            cur = if (existing != null && existing.isDirectory) existing
            else cur.createDirectory(p) ?: throw IllegalStateException("Cannot create folder $p")
        }
        val last = parts.last()
        val existing = cur.findFile(last)
        if (existing != null) throw IllegalStateException("\"$last\" already exists")
        return if (isDir) cur.createDirectory(last) ?: throw IllegalStateException("Cannot create folder")
        else cur.createFile("application/octet-stream", last) ?: throw IllegalStateException("Cannot create file")
    }

    suspend fun walk(root: DocumentFile, limit: Int = 20000): List<FileNode> = withContext(Dispatchers.IO) {
        val out = ArrayList<FileNode>()
        val stack = ArrayDeque<Pair<DocumentFile, String>>()
        stack.add(root to "")
        while (stack.isNotEmpty() && out.size < limit) {
            currentCoroutineContext().ensureActive()
            val (d, p) = stack.removeAt(stack.lastIndex)
            for (n in list(d, p)) {
                if (n.isDir) {
                    if (n.name !in SKIP_DIRS) stack.add(n.doc to n.path)
                } else out.add(n)
            }
        }
        out
    }

    suspend fun search(
        ctx: Context, files: List<FileNode>, re: Regex, limit: Int = 500,
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val hits = ArrayList<SearchHit>()
        for (f in files) {
            currentCoroutineContext().ensureActive()
            if (hits.size >= limit) break
            if (isBinaryName(f.name)) continue
            if (f.doc.length() > 1_000_000) continue
            val text = try { read(ctx, f.uri).text } catch (e: Exception) { continue }
            var ln = 0
            for (line in text.lineSequence()) {
                ln++
                if (re.containsMatchIn(line)) {
                    hits.add(SearchHit(f, ln, line.trim().take(160)))
                    if (hits.size >= limit) break
                }
            }
        }
        hits
    }

    /** App-private workspace that works without any storage permission. */
    fun internalRoot(ctx: Context): DocumentFile {
        val dir = File(ctx.filesDir, "workspace")
        if (!dir.exists()) {
            dir.mkdirs()
            seed(dir)
        }
        return DocumentFile.fromFile(dir)
    }

    private fun seed(dir: File) {
        File(dir, "Welcome.md").writeText(WELCOME)
        File(dir, "hello.py").writeText(
            "def greet(name: str) -> str:\n    return f\"Hello, {name}!\"\n\n\nif __name__ == \"__main__\":\n    print(greet(\"Francode\"))\n"
        )
        File(dir, "index.html").writeText(
            "<!DOCTYPE html>\n<html>\n<head>\n  <meta charset=\"utf-8\">\n  <title>Francode demo</title>\n  <style>\n    body { font-family: sans-serif; text-align: center; padding: 3rem; }\n    h1 { color: #2f81f7; }\n  </style>\n</head>\n<body>\n  <h1>Hello from Francode</h1>\n  <p>Tap the ▶ button in the top bar to preview this page.</p>\n  <script>\n    document.body.insertAdjacentHTML('beforeend', '<p>JavaScript works too: ' + new Date().toDateString() + '</p>');\n  </script>\n</body>\n</html>\n"
        )
        File(dir, "Main.kt").writeText(
            "package demo\n\ndata class User(val name: String, val age: Int)\n\nfun main() {\n    val users = listOf(User(\"Ama\", 31), User(\"Kofi\", 27))\n    users.sortedBy { it.age }.forEach { println(\"\${it.name} is \${it.age}\") }\n}\n"
        )
    }

    private val WELCOME = """# Welcome to Francode

A code editor for Android, inspired by VS Code.

## Getting started
- Tap **☰** to open the file explorer, or use **Open Folder** to edit a project anywhere on your device.
- Tap **▶** to preview HTML and Markdown files.
- Open the **command palette** (⋮ menu, or `Ctrl+Shift+P` on a keyboard) to find every command.

## Editor features
- Syntax highlighting for 20+ languages
- Auto-indent, auto-closing brackets and quotes
- Find / replace with regex, search across all files
- Quick Open (`Ctrl+P`), Go to Line (`Ctrl+G`)
- Toggle comment (`Ctrl+/`), duplicate line (`Ctrl+D`), move line (`Alt+↑/↓`)
- Tabs, undo / redo, auto-save, 8 colour themes

Happy coding!
"""
}
