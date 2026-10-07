package com.francode.app

/** A small dependency-free Markdown → HTML converter for the preview pane. */
object Markdown {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private val heading = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val hr = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val ul = Regex("^\\s*[-*+]\\s+(.*)$")
    private val ol = Regex("^\\s*\\d+[.)]\\s+(.*)$")

    fun toHtml(src: String): String {
        val lines = src.replace("\r\n", "\n").split("\n")
        val out = StringBuilder()
        var list: String? = null
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotEmpty()) {
                out.append("<p>").append(inline(para.toString().trim())).append("</p>\n")
                para.setLength(0)
            }
        }
        fun closeList() {
            list?.let { out.append("</").append(it).append(">\n") }
            list = null
        }

        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            val t = l.trimStart()
            when {
                t.startsWith("```") -> {
                    flushPara(); closeList()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.append(esc(lines[i])).append('\n'); i++
                    }
                    out.append("<pre><code>").append(code).append("</code></pre>\n")
                }
                l.isBlank() -> { flushPara(); closeList() }
                heading.matches(l) -> {
                    flushPara(); closeList()
                    val m = heading.find(l)!!
                    val n = m.groupValues[1].length
                    out.append("<h$n>").append(inline(m.groupValues[2])).append("</h$n>\n")
                }
                hr.matches(l) -> { flushPara(); closeList(); out.append("<hr>\n") }
                t.startsWith(">") -> {
                    flushPara(); closeList()
                    out.append("<blockquote>").append(inline(t.removePrefix(">").trim())).append("</blockquote>\n")
                }
                ul.matches(l) || ol.matches(l) -> {
                    flushPara()
                    val isUl = ul.matches(l)
                    val tag = if (isUl) "ul" else "ol"
                    if (list != tag) { closeList(); out.append("<$tag>\n"); list = tag }
                    val body = (if (isUl) ul else ol).find(l)!!.groupValues[1]
                    out.append("<li>").append(inline(body)).append("</li>\n")
                }
                else -> { closeList(); para.append(l).append(' ') }
            }
            i++
        }
        flushPara(); closeList()
        return out.toString()
    }

    private fun inline(s: String): String {
        var t = esc(s)
        t = Regex("`([^`]+)`").replace(t) { "<code>" + it.groupValues[1] + "</code>" }
        t = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)").replace(t) { "<img alt=\"${it.groupValues[1]}\" src=\"${it.groupValues[2]}\">" }
        t = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)").replace(t) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        t = Regex("\\*\\*(.+?)\\*\\*").replace(t) { "<strong>" + it.groupValues[1] + "</strong>" }
        t = Regex("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])").replace(t) { "<em>" + it.groupValues[1] + "</em>" }
        t = Regex("~~(.+?)~~").replace(t) { "<del>" + it.groupValues[1] + "</del>" }
        return t
    }

    fun page(body: String, dark: Boolean): String {
        val bg = if (dark) "#0d1117" else "#ffffff"
        val fg = if (dark) "#c9d1d9" else "#24292f"
        val code = if (dark) "#161b22" else "#f6f8fa"
        val link = if (dark) "#58a6ff" else "#0969da"
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{background:$bg;color:$fg;font:16px/1.6 -apple-system,Roboto,sans-serif;margin:0;padding:16px;word-wrap:break-word}
a{color:$link} img{max-width:100%}
code{background:$code;padding:2px 5px;border-radius:4px;font-family:monospace;font-size:90%}
pre{background:$code;padding:12px;border-radius:8px;overflow:auto} pre code{padding:0;background:none}
blockquote{border-left:4px solid #8b949e55;margin:0;padding:0 12px;color:#8b949e}
h1,h2{border-bottom:1px solid #8b949e44;padding-bottom:.3em} hr{border:0;border-top:1px solid #8b949e44}
</style></head><body>$body</body></html>"""
    }
}
