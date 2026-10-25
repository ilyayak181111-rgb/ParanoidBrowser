package com.paranoid.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private enum class Engine(val label: String, val tpl: String) {
        GOOGLE("Google", "https://www.google.com/search?q=%s"),
        YANDEX("Yandex", "https://yandex.ru/search/?text=%s"),
        DDG("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        BRAVE("Brave", "https://search.brave.com/search?q=%s"),
        STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s")
    }
    private var current = Engine.DDG
    private lateinit var web: WebView
    private lateinit var urlBar: EditText

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.urlBar)
        configureWebView()
        bindQuickButtons()
        bindUrlBar()
        bindMenu()
        if (savedInstanceState == null) loadQuery("")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = false
        s.setGeolocationEnabled(false)
        s.saveFormData = false
        s.savePassword = false
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.setSupportMultipleWindows(false)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.userAgentString = "Mozilla/5.0 (Linux; Android 13) ParanoidBrowser/1.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host ?: return null
                if (TrackerBlocker.isBlocked(host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme ?: return false
                if (scheme != "http" && scheme != "https" && scheme != "data") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Throwable) {}
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                urlBar.setText(url ?: "")
            }
        }
        web.webChromeClient = WebChromeClient()
    }

    private fun bindUrlBar() {
        urlBar.setOnEditorActionListener { _, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (go) { navigate(urlBar.text.toString()); hideKeyboard(); true } else false
        }
    }

    private fun bindQuickButtons() {
        fun btn(id: Int, action: () -> Unit) { findViewById<View>(id).setOnClickListener { action() } }
        btn(R.id.qGoogle) { current = Engine.GOOGLE; loadQuery("") }
        btn(R.id.qYandex) { current = Engine.YANDEX; loadQuery("") }
        btn(R.id.qDDG) { current = Engine.DDG; loadQuery("") }
        btn(R.id.qBrave) { current = Engine.BRAVE; loadQuery("") }
        btn(R.id.qStartpage) { current = Engine.STARTPAGE; loadQuery("") }
        btn(R.id.qGpt) { web.loadUrl("https://chatgpt.com") }
        btn(R.id.qWiki) { web.loadUrl("https://www.wikipedia.org") }
        btn(R.id.qPerplexity) { web.loadUrl("https://www.perplexity.ai") }
    }

    private fun bindMenu() {
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { openOptionsMenu() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mWipe -> { wipe(); Toast.makeText(this, R.string.wipe_done, Toast.LENGTH_SHORT).show(); true }
        R.id.mEngine -> { cycleEngine(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun cycleEngine() {
        val values = Engine.values()
        current = values[(current.ordinal + 1) % values.size]
        Toast.makeText(this, "движок: ${current.label}", Toast.LENGTH_SHORT).show()
    }

    private fun navigate(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) { loadQuery(""); return }
        val looksLikeUrl = q.contains(".") && !q.contains(" ") &&
            (q.startsWith("http://") || q.startsWith("https://") || q.startsWith("localhost") || q.matches(Regex("^[\\w\\-]+\\.[\\w\\-.]+$")))
        if (looksLikeUrl) {
            val withScheme = if (q.startsWith("http://") || q.startsWith("https://")) q else "https://$q"
            web.loadUrl(withScheme)
        } else loadQuery(q)
    }

    private fun loadQuery(q: String) {
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        web.loadUrl(String.format(current.tpl, enc))
    }

    private fun wipe() {
        web.clearCache(true); web.clearHistory(); web.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        cacheDir.deleteRecursively()
        filesDir.listFiles()?.forEach { if (it.name != "db") it.deleteRecursively() }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); intent?.data?.let { web.loadUrl(it.toString()) } }
    override fun onPause() { super.onPause(); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    override fun onResume() { super.onResume(); window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
}
