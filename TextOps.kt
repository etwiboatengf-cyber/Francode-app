package com.francode.app

/** Pure text-editing helpers. All functions work on (text, selection) and return a new [Edit]. */
data class Edit(val text: String, val selStart: Int, val selEnd: Int = selStart)

object TextOps {
    private const val OPEN = "([{"
    private const val CLOSE = ")]}"
    private const val QUOTES = "\"'`"

    fun lineStart(t: String, i: Int): Int = if (i <= 0) 0 else t.lastIndexOf('\n', i - 1) + 1
    fun lineEnd(t: String, i: Int): Int {
        val e = t.indexOf('\n', i)
        return if (e < 0) t.length else e
    }

    /** Range [start, end) of the lines touched by the selection (end excludes the trailing \n). */
    private fun block(t: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
        val lo = minOf(selStart, selEnd)
        var hi = maxOf(selStart, selEnd)
        if (hi > lo && hi > 0 && t[hi - 1] == '\n') hi -= 1
        return lineStart(t, lo) to lineEnd(t, hi)
    }

    /**
     * Called when the user typed a single character. [old] is the state before the keystroke and
     * [new] the state the IME produced. Returns a corrected edit (auto-indent, auto-close brackets,
     * type-over closers, pair deletion) or null if the raw edit should be kept.
     */
    fun smartEdit(
        old: Edit, new: Edit, lang: LangDef, indentUnit: String, autoClose: Boolean,
    ): Edit? {
        if (old.selStart != old.selEnd) return null
        val ot = old.text
        val nt = new.text
        val pos = old.selStart
        if (new.selStart != new.selEnd) return null

        // ---- single character inserted at the cursor
        if (nt.length == ot.length + 1 && new.selStart == pos + 1 &&
            nt.regionMatches(0, ot, 0, pos) && nt.regionMatches(pos + 1, ot, pos, ot.length - pos)
        ) {
            val ch = nt[pos]
            val next = if (pos < ot.length) ot[pos] else '\u0000'
            val prev = if (pos > 0) ot[pos - 1] else '\n'

            if (ch == '\n') return newline(ot, pos, lang, indentUnit)

            if (!autoClose) return null

            if ((ch in CLOSE || ch in QUOTES) && next == ch) {
                // type over an existing closer
                return Edit(ot, pos + 1)
            }
            val nextOk = next == '\u0000' || next.isWhitespace() || next in ")]};,.:>"
            if (ch in OPEN && nextOk) {
                val close = CLOSE[OPEN.indexOf(ch)]
                return Edit(ot.substring(0, pos) + ch + close + ot.substring(pos), pos + 1)
            }
            if (ch in QUOTES && nextOk && !prev.isLetterOrDigit() && prev != ch) {
                return Edit(ot.substring(0, pos) + ch + ch + ot.substring(pos), pos + 1)
            }
            return null
        }

        // ---- backspace between an empty pair: delete both
        if (autoClose && nt.length == ot.length - 1 && pos > 0 && new.selStart == pos - 1 &&
            pos < ot.length && nt == ot.removeRange(pos - 1, pos)
        ) {
            val d = ot[pos - 1]
            val n = ot[pos]
            val paired = (d in OPEN && CLOSE[OPEN.indexOf(d)] == n) || (d in QUOTES && d == n)
            if (paired) return Edit(ot.removeRange(pos - 1, pos + 1), pos - 1)
        }
        return null
    }

    private fun newline(ot: String, pos: Int, lang: LangDef, unit: String): Edit {
        val ls = lineStart(ot, pos)
        val line = ot.substring(ls, pos)
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val last = line.trimEnd().lastOrNull()
        val opens = last != null && (last in OPEN ||
            ((lang.id == "python" || lang.id == "yaml") && last == ':'))
        val next = if (pos < ot.length) ot[pos] else '\u0000'
        val ins: String
        val cursor: Int
        if (last != null && last in OPEN && next == CLOSE[OPEN.indexOf(last)]) {
            ins = "\n$indent$unit\n$indent"
            cursor = pos + 1 + indent.length + unit.length
        } else {
            val extra = if (opens) unit else ""
            ins = "\n$indent$extra"
            cursor = pos + ins.length
        }
        return Edit(ot.substring(0, pos) + ins + ot.substring(pos), cursor)
    }

    /** Tab / Shift+Tab. With a multi-line selection every line is (de)indented. */
    fun indent(e: Edit, unit: String, outdent: Boolean): Edit {
        val t = e.text
        val lo = minOf(e.selStart, e.selEnd)
        val hi = maxOf(e.selStart, e.selEnd)
        val multi = t.substring(lo, hi).contains('\n')
        if (!multi && !outdent) {
            return Edit(t.substring(0, lo) + unit + t.substring(hi), lo + unit.length)
        }
        val (s, en) = block(t, e.selStart, e.selEnd)
        val lines = t.substring(s, en).split("\n")
        val out = lines.map { l ->
            if (!outdent) {
                if (l.isEmpty()) l else unit + l
            } else {
                when {
                    l.startsWith("\t") -> l.substring(1)
                    else -> {
                        var n = 0
                        while (n < unit.length && n < l.length && l[n] == ' ') n++
                        l.substring(n)
                    }
                }
            }
        }
        val nb = out.joinToString("\n")
        val nt = t.substring(0, s) + nb + t.substring(en)
        return if (e.selStart == e.selEnd) {
            val d = nb.length - (en - s)
            val c = (e.selStart + d).coerceIn(s, s + nb.length)
            Edit(nt, c)
        } else Edit(nt, s, s + nb.length)
    }

    fun toggleComment(e: Edit, lang: LangDef): Edit {
        val t = e.text
        val (s, en) = block(t, e.selStart, e.selEnd)
        val lines = t.substring(s, en).split("\n")
        val lc = lang.lineComment
        val bc = lang.blockComment
        val nonBlank = lines.filter { it.isNotBlank() }
        val out: List<String> = when {
            lc != null -> {
                val all = nonBlank.isNotEmpty() && nonBlank.all { it.trimStart().startsWith(lc) }
                if (all) lines.map { l ->
                    val i = l.indexOf(lc)
                    if (i < 0 || l.isBlank()) l else {
                        var end = i + lc.length
                        if (end < l.length && l[end] == ' ') end++
                        l.removeRange(i, end)
                    }
                } else {
                    val minIndent = nonBlank.minOfOrNull { l -> l.length - l.trimStart().length } ?: 0
                    lines.map { l ->
                        if (l.isBlank()) l else l.substring(0, minIndent) + lc + " " + l.substring(minIndent)
                    }
                }
            }
            bc != null -> {
                val all = nonBlank.isNotEmpty() && nonBlank.all {
                    val tr = it.trim(); tr.startsWith(bc.first) && tr.endsWith(bc.second)
                }
                lines.map { l ->
                    if (l.isBlank()) l
                    else if (all) {
                        val i = l.indexOf(bc.first)
                        val j = l.lastIndexOf(bc.second)
                        val inner = l.substring(i + bc.first.length, j).removePrefix(" ").removeSuffix(" ")
                        l.substring(0, i) + inner + l.substring(j + bc.second.length)
                    } else {
                        val ind = l.length - l.trimStart().length
                        l.substring(0, ind) + bc.first + " " + l.substring(ind).trimEnd() + " " + bc.second
                    }
                }
            }
            else -> return e
        }
        val nb = out.joinToString("\n")
        val nt = t.substring(0, s) + nb + t.substring(en)
        return if (e.selStart == e.selEnd) {
            val c = (e.selStart + nb.length - (en - s)).coerceIn(s, s + nb.length)
            Edit(nt, c)
        } else Edit(nt, s, s + nb.length)
    }

    fun duplicateLines(e: Edit): Edit {
        val t = e.text
        val (s, en) = block(t, e.selStart, e.selEnd)
        val b = t.substring(s, en)
        val nt = t.substring(0, en) + "\n" + b + t.substring(en)
        val shift = b.length + 1
        return Edit(nt, e.selStart + shift, e.selEnd + shift)
    }

    fun deleteLines(e: Edit): Edit {
        val t = e.text
        var (s, en) = block(t, e.selStart, e.selEnd)
        if (en < t.length) en++ else if (s > 0) s--
        val nt = t.removeRange(s, en)
        return Edit(nt, lineStart(nt, s.coerceAtMost(nt.length)))
    }

    fun moveLines(e: Edit, up: Boolean): Edit {
        val t = e.text
        val (s, en) = block(t, e.selStart, e.selEnd)
        val b = t.substring(s, en)
        if (up) {
            if (s == 0) return e
            val ps = lineStart(t, s - 1)
            val prev = t.substring(ps, s - 1)
            val nt = t.substring(0, ps) + b + "\n" + prev + t.substring(en)
            val d = prev.length + 1
            return Edit(nt, e.selStart - d, e.selEnd - d)
        } else {
            if (en >= t.length) return e
            val ne = lineEnd(t, en + 1)
            val next = t.substring(en + 1, ne)
            val nt = t.substring(0, s) + next + "\n" + b + t.substring(ne)
            val d = next.length + 1
            return Edit(nt, e.selStart + d, e.selEnd + d)
        }
    }

    /** Offset of the start of 1-based [line] (clamped) plus 0-based [col]. */
    fun offsetOf(text: String, line: Int, col: Int = 0): Int {
        var off = 0
        var l = 1
        while (l < line) {
            val i = text.indexOf('\n', off)
            if (i < 0) break
            off = i + 1
            l++
        }
        val end = lineEnd(text, off)
        return (off + col.coerceAtLeast(0)).coerceAtMost(end)
    }

    /** 1-based line and column for [offset]. */
    fun lineCol(text: String, offset: Int): Pair<Int, Int> {
        val o = offset.coerceIn(0, text.length)
        var line = 1
        var i = text.indexOf('\n')
        while (i in 0 until o) {
            line++
            i = text.indexOf('\n', i + 1)
        }
        return line to (o - lineStart(text, o) + 1)
    }
}
