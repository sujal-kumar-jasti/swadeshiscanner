package com.swadeshiscanner.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView

class MathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        settings.javaScriptEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setFormula(latex: String) {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) "#FFFFFF" else "#000000"
        
        // Use a simpler HTML template with explicit KaTeX font-family control
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js" 
                    onload="renderMathInElement(document.body);"></script>
                <style>
                    body { 
                        margin: 0; 
                        padding: 0 16px;
                        background-color: transparent;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        min-height: 100vh;
                    }
                    #math {
                        color: $textColor;
                        font-size: 28px;
                        text-align: center;
                        white-space: nowrap;
                    }
                </style>
            </head>
            <body>
                <div id="math">
                   \( $latex \)
                </div>
            </body>
            </html>
        """.trimIndent()

        loadDataWithBaseURL("https://cdn.jsdelivr.net/npm/katex@0.16.9/", html, "text/html", "utf-8", null)
    }
}