package com.francode.app

/** Token categories used for syntax colouring. */
enum class Tok { Comment, String, Number, Keyword, Type, Function, Tag, Attr, Meta }

/** A coloured range [start, end) of the source text. */
class Span(val start: Int, val end: Int, val tok: Tok)

/**
 * A language definition: a list of (regex, token) rules combined into one alternation.
 * Rules earlier in the list win. No rule may match the empty string and rules must not
 * contain capturing groups (use `(?:...)`).
 */
class LangDef(
    val id: String,
    val label: String,
    val exts: List<String>,
    specs: List<Pair<String, Tok>>,
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
) {
    private val regex: Regex? =
        if (specs.isEmpty()) null else Regex(specs.joinToString("|") { "(${it.first})" })
    private val toks: List<Tok> = specs.map { it.second }

    fun tokenize(text: String): List<Span> {
        val re = regex ?: return emptyList()
        if (text.length > MAX_HIGHLIGHT) return emptyList()
        val out = ArrayList<Span>()
        for (m in re.findAll(text)) {
            val g = m.groups
            for (i in toks.indices) {
                val grp = g[i + 1]
                if (grp != null) {
                    out.add(Span(grp.range.first, grp.range.last + 1, toks[i]))
                    break
                }
            }
        }
        return out
    }

    companion object {
        const val MAX_HIGHLIGHT = 400_000
    }
}

private const val Q = "\""
private const val Q3 = "\"\"\""

private const val BLOCK_C = """/\*[\s\S]*?(?:\*/|\z)"""
private const val LINE_C = """//[^\n]*"""
private const val LINE_HASH = """#[^\n]*"""
private const val DQ = """$Q(?:\\.|[^$Q\\\n])*$Q?"""
private const val SQ = """'(?:\\.|[^'\\\n])*'?"""
private const val BT = """`(?:\\[\s\S]|[^`\\])*`?"""
private const val TRIPLE = """$Q3[\s\S]*?(?:$Q3|\z)"""
private const val NUM = """\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFlLuUdD]*)\b"""
private const val TYPE = """\b[A-Z][A-Za-z0-9_]*\b"""
private const val FUNC = """\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\()"""
private const val ANNOT = """@[A-Za-z_][\w.]*"""

private fun kw(words: String, ignoreCase: Boolean = false): String {
    val alt = words.trim().split(Regex("\\s+")).joinToString("|")
    return if (ignoreCase) "(?i:\\b(?:$alt)\\b)" else "\\b(?:$alt)\\b"
}

private fun cLike(
    id: String, label: String, exts: List<String>, keywords: String,
    extra: List<Pair<String, Tok>> = emptyList(),
    strings: List<String> = listOf(DQ, SQ),
    lineComment: String? = "//",
    hashComment: Boolean = false,
    types: Boolean = true,
): LangDef {
    val s = ArrayList<Pair<String, Tok>>()
    s += BLOCK_C to Tok.Comment
    if (lineComment == "//") s += LINE_C to Tok.Comment
    if (hashComment) s += LINE_HASH to Tok.Comment
    s += extra
    for (st in strings) s += st to Tok.String
    s += NUM to Tok.Number
    s += ANNOT to Tok.Meta
    s += kw(keywords) to Tok.Keyword
    if (types) s += TYPE to Tok.Type
    s += FUNC to Tok.Function
    return LangDef(id, label, exts, s, lineComment, "/*" to "*/")
}

object Languages {
    private val kotlin = cLike(
        "kotlin", "Kotlin", listOf("kt", "kts", "gradle"),
        "abstract actual annotation as break by catch class companion const constructor continue crossinline data delegate do dynamic else enum expect external false final finally for fun get if import in infix init inline inner interface internal is it lateinit lazy noinline null object open operator out override package private protected public reified return sealed set super suspend tailrec this throw true try typealias typeof val value var vararg when where while",
        extra = listOf(TRIPLE to Tok.String),
    )
    private val java = cLike(
        "java", "Java", listOf("java"),
        "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new null package private protected public return short static strictfp super switch synchronized this throw throws transient true false try var void volatile while record sealed permits yield",
    )
    private val js = cLike(
        "javascript", "JavaScript / TypeScript", listOf("js", "jsx", "mjs", "cjs", "ts", "tsx"),
        "async await break case catch class const continue debugger default delete do else enum export extends false finally for from function if implements import in instanceof interface let new null of package private protected public return static super switch this throw true try typeof undefined var void while with yield type namespace readonly abstract as declare keyof is number string boolean any never unknown get set",
        strings = listOf(DQ, SQ, BT),
    )
    private val python = LangDef("python", "Python", listOf("py", "pyw", "pyi"), pythonSpecs(), "#", null)

    private fun pythonSpecs(): List<Pair<String, Tok>> = listOf(
        LINE_HASH to Tok.Comment,
        TRIPLE to Tok.String,
        "'''[\\s\\S]*?(?:'''|\\z)" to Tok.String,
        DQ to Tok.String,
        SQ to Tok.String,
        NUM to Tok.Number,
        """@[A-Za-z_][\w.]*""" to Tok.Meta,
        kw("False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case self cls") to Tok.Keyword,
        TYPE to Tok.Type,
        FUNC to Tok.Function,
    )

    private val c = cLike(
        "c", "C / C++", listOf("c", "h", "cpp", "cc", "cxx", "hpp", "hh", "ino"),
        "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while bool true false NULL nullptr class namespace template typename public private protected virtual override new delete this using try catch throw constexpr noexcept operator final explicit friend mutable static_cast dynamic_cast reinterpret_cast const_cast",
        extra = listOf("""(?m:^[ \t]*#[ \t]*[A-Za-z_]+)""" to Tok.Meta),
        types = false,
    )
    private val csharp = cLike(
        "csharp", "C#", listOf("cs"),
        "abstract as base bool break byte case catch char checked class const continue decimal default delegate do double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using var virtual void volatile while async await record init get set yield",
    )
    private val go = cLike(
        "go", "Go", listOf("go"),
        "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var true false nil iota",
        strings = listOf(DQ, SQ, BT),
    )
    private val rust = cLike(
        "rust", "Rust", listOf("rs"),
        "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while",
        strings = listOf(DQ, """'(?:\\.[^'\n]*|[^'\\\n])'"""),
    )
    private val dart = cLike(
        "dart", "Dart", listOf("dart"),
        "abstract as assert async await break case catch class const continue covariant default deferred do dynamic else enum export extends extension external factory false final finally for Function get hide if implements import in interface is late library mixin new null on operator part required rethrow return set show static super switch sync this throw true try typedef var void while with yield",
    )
    private val swift = cLike(
        "swift", "Swift", listOf("swift"),
        "associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private protocol public rethrows static struct subscript typealias var break case continue default defer do else fallthrough for guard if in repeat return switch where while as Any catch false is nil super self Self throw throws true try async await actor some any",
    )
    private val php = cLike(
        "php", "PHP", listOf("php", "phtml"),
        "abstract and array as break callable case catch class clone const continue declare default do echo else elseif empty extends final finally fn for foreach function global goto if implements include include_once instanceof insteadof interface isset list match namespace new null or print private protected public readonly require require_once return static switch throw trait true false try unset use var while xor yield",
        extra = listOf(LINE_HASH to Tok.Comment, """\$[A-Za-z_]\w*""" to Tok.Attr, """<\?php|\?>""" to Tok.Meta),
    )
    private val ruby = LangDef(
        "ruby", "Ruby", listOf("rb", "rake", "gemspec"),
        listOf(
            LINE_HASH to Tok.Comment, DQ to Tok.String, SQ to Tok.String,
            NUM to Tok.Number,
            """@{1,2}[A-Za-z_]\w*""" to Tok.Attr,
            """:[A-Za-z_]\w*""" to Tok.Attr,
            kw("alias and begin break case class def do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield puts require") to Tok.Keyword,
            TYPE to Tok.Type, FUNC to Tok.Function,
        ), "#", null,
    )
    private val shell = LangDef(
        "shell", "Shell", listOf("sh", "bash", "zsh", "fish", "env"),
        listOf(
            LINE_HASH to Tok.Comment, DQ to Tok.String, SQ to Tok.String,
            """\$\{[^}\n]*\}|\$[A-Za-z_]\w*|\$[0-9?#@*!$]""" to Tok.Attr,
            NUM to Tok.Number,
            kw("if then else elif fi for while until do done case esac in function select time return exit break continue local export readonly declare unset source alias echo cd set shift trap") to Tok.Keyword,
            FUNC to Tok.Function,
        ), "#", null,
    )
    private val sql = LangDef(
        "sql", "SQL", listOf("sql"),
        listOf(
            """--[^\n]*""" to Tok.Comment, BLOCK_C to Tok.Comment, SQ to Tok.String, DQ to Tok.String,
            NUM to Tok.Number,
            kw("select from where insert into values update set delete create table alter drop index view join inner left right outer full cross on as and or not null is in like between group by order having limit offset union all distinct case when then else end primary key foreign references default unique check constraint exists asc desc with begin commit rollback int integer varchar text boolean date timestamp", ignoreCase = true) to Tok.Keyword,
            FUNC to Tok.Function,
        ), "--", "/*" to "*/",
    )
    private val html = LangDef(
        "html", "HTML / XML", listOf("html", "htm", "xhtml", "xml", "svg", "vue", "plist", "xaml", "csproj", "iml"),
        listOf(
            """<!--[\s\S]*?(?:-->|\z)""" to Tok.Comment,
            """<![A-Za-z][^>]*>|<\?[\s\S]*?(?:\?>|\z)""" to Tok.Meta,
            """</?[A-Za-z][\w:.-]*""" to Tok.Tag,
            """/?>""" to Tok.Tag,
            """[A-Za-z_:@#][\w:.-]*(?=\s*=)""" to Tok.Attr,
            """$Q[^$Q]*$Q?|'[^']*'?""" to Tok.String,
            """&[A-Za-z0-9#]+;""" to Tok.Number,
        ), null, "<!--" to "-->",
    )
    private val css = LangDef(
        "css", "CSS", listOf("css", "scss", "sass", "less"),
        listOf(
            BLOCK_C to Tok.Comment, LINE_C to Tok.Comment, DQ to Tok.String, SQ to Tok.String,
            """#[0-9a-fA-F]{3,8}\b""" to Tok.Number,
            """-?(?:\d+\.?\d*|\.\d+)(?:px|em|rem|%|vh|vw|vmin|vmax|pt|cm|mm|in|s|ms|deg|fr)?\b""" to Tok.Number,
            """@[A-Za-z-]+""" to Tok.Keyword,
            """--?[A-Za-z][\w-]*(?=\s*:)""" to Tok.Attr,
            """[A-Za-z-]+(?=\s*:\s*[^\s{])""" to Tok.Attr,
            """[.#][A-Za-z_][\w-]*""" to Tok.Type,
            FUNC to Tok.Function,
        ), "//", "/*" to "*/",
    )
    private val json = LangDef(
        "json", "JSON", listOf("json", "jsonc", "json5", "webmanifest", "ipynb", "geojson"),
        listOf(
            """$Q(?:\\.|[^$Q\\\n])*$Q(?=\s*:)""" to Tok.Attr,
            DQ to Tok.String,
            NUM to Tok.Number,
            """-\d[\d.eE+-]*""" to Tok.Number,
            """\b(?:true|false|null)\b""" to Tok.Keyword,
            LINE_C to Tok.Comment, BLOCK_C to Tok.Comment,
        ), null, null,
    )
    private val yaml = LangDef(
        "yaml", "YAML", listOf("yml", "yaml"),
        listOf(
            LINE_HASH to Tok.Comment, DQ to Tok.String, SQ to Tok.String,
            """[\w.\-/]+(?=:(?:\s|\z))""" to Tok.Attr,
            NUM to Tok.Number,
            """\b(?:true|false|null|yes|no|on|off)\b""" to Tok.Keyword,
            """[&*!][\w-]+""" to Tok.Meta,
        ), "#", null,
    )
    private val toml = LangDef(
        "toml", "TOML / INI", listOf("toml", "ini", "cfg", "conf", "properties"),
        listOf(
            """[#;][^\n]*""" to Tok.Comment,
            """(?m:^[ \t]*\[[^\n\]]+\])""" to Tok.Type,
            DQ to Tok.String, SQ to Tok.String,
            """(?m:^[ \t]*[\w.\-]+(?=[ \t]*[=:]))""" to Tok.Attr,
            NUM to Tok.Number,
            """\b(?:true|false)\b""" to Tok.Keyword,
        ), "#", null,
    )
    private val markdown = LangDef(
        "markdown", "Markdown", listOf("md", "markdown", "mdx"),
        listOf(
            """(?m:^[ \t]*```[^\n]*\n[\s\S]*?(?:^[ \t]*```|\z))""" to Tok.String,
            """<!--[\s\S]*?(?:-->|\z)""" to Tok.Comment,
            """(?m:^#{1,6}[ \t][^\n]*)""" to Tok.Keyword,
            """`[^`\n]+`""" to Tok.String,
            """\*\*[^*\n]+\*\*|__[^_\n]+__""" to Tok.Type,
            """!?\[[^\]\n]*\]\([^)\n]*\)""" to Tok.Function,
            """(?m:^>[^\n]*)""" to Tok.Comment,
            """(?m:^[ \t]*(?:[-*+]|\d+\.)[ \t])""" to Tok.Attr,
        ), null, "<!--" to "-->",
    )
    private val plain = LangDef("plaintext", "Plain Text", emptyList(), emptyList(), null, null)

    val all: List<LangDef> = listOf(
        kotlin, java, js, python, c, csharp, go, rust, dart, swift, php, ruby, shell, sql,
        html, css, json, yaml, toml, markdown, plain,
    )

    private val byExt: Map<String, LangDef> =
        all.flatMap { l -> l.exts.map { it to l } }.toMap()

    fun forName(fileName: String): LangDef {
        val lower = fileName.lowercase()
        if (lower == "dockerfile" || lower == "makefile" || lower == "cmakelists.txt") return plain
        if (lower == ".gitignore" || lower == ".env") return shell
        val ext = lower.substringAfterLast('.', "")
        return byExt[ext] ?: plain
    }

    fun ofId(id: String): LangDef = all.firstOrNull { it.id == id } ?: plain
}
