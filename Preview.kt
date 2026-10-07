package com.francode.app

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Full-screen live preview for HTML, SVG and Markdown files. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewPane(tab: Tab, theme: EditorTheme, onClose: () -> Unit) {
    val text = tab.value.text
    val name = tab.name
    val html = remember(text, name, theme) {
        when {
            tab.lang.id == "markdown" -> Markdown.page(Markdown.toHtml(text), theme.dark)
            name.endsWith(".svg", true) ->
                "<!doctype html><html><body style=\"margin:0;background:#fff;display:flex;justify-content:center\">$text</body></html>"
            else -> text
        }
    }
    Column(Modifier.fillMaxSize().background(theme.bg)) {
        Row(
            Modifier.fillMaxWidth().background(theme.panel).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Preview · $name", fontSize = 14.sp, color = theme.fg, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, "Close preview", tint = theme.fg)
            }
        }
        HorizontalDivider(color = theme.border)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    setBackgroundColor(theme.bg.toArgb())
                    webViewClient = WebViewClient()
                }
            },
            update = { w ->
                if (w.tag != html) {
                    w.tag = html
                    w.loadDataWithBaseURL("https://preview.francode.local/", html, "text/html", "utf-8", null)
                }
            },
        )
    }
}
