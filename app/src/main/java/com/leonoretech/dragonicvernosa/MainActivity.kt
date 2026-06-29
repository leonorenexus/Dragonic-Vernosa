package com.leonoretech.dragonicvernosa

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var ctxPtr: Long = 0L
    @Volatile private var generating = false

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            thread {
                val result = ModelManager.importFromUri(this, uri)
                runOnUiThread {
                    result.onSuccess {
                        runOnWebView("window.onImportDone(true, null)")
                        loadModelInBackground()
                    }.onFailure { e ->
                        runOnWebView("window.onImportDone(false, ${WebBridge.jsString(e.message ?: "error")})")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(WebBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/web/index.html")

        if (ModelManager.hasLocalModel(this)) {
            loadModelInBackground()
        }
    }

    fun runOnWebView(js: String) {
        webView.post { webView.evaluateJavascript(js, null) }
    }

    fun isModelReady(): Boolean = ctxPtr != 0L

    fun launchFilePicker() {
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    fun startModelDownload(url: String) {
        thread {
            ModelManager.downloadFromUrl(
                this, url,
                onProgress = { pct -> runOnWebView("window.onDownloadProgress($pct)") },
                onDone = {
                    runOnUiThread {
                        runOnWebView("window.onImportDone(true, null)")
                        loadModelInBackground()
                    }
                },
                onError = { msg ->
                    runOnUiThread { runOnWebView("window.onImportDone(false, ${WebBridge.jsString(msg)})") }
                }
            )
        }
    }

    fun deleteModelAndReset() {
        if (ctxPtr != 0L) {
            LlamaEngine.nativeFree(ctxPtr)
            ctxPtr = 0L
        }
        ModelManager.deleteModel(this)
        runOnWebView("window.onModelStatus(false)")
    }

    private fun loadModelInBackground() {
        thread {
            runOnWebView("window.onModelLoading(true)")
            val path = ModelManager.modelFile(this).absolutePath
            val nThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val ptr = LlamaEngine.nativeLoadModel(path, nThreads, 2048)
            ctxPtr = ptr
            runOnUiThread {
                runOnWebView("window.onModelLoading(false)")
                runOnWebView("window.onModelStatus(${ptr != 0L})")
            }
        }
    }

    fun generate(prompt: String, maxTokens: Int, temperature: Float) {
        if (ctxPtr == 0L) {
            runOnUiThread { runOnWebView("window.onGenError(${WebBridge.jsString("Model belum di-load")})") }
            return
        }
        generating = true
        val full = StringBuilder()
        LlamaEngine.nativeGenerate(ctxPtr, prompt, maxTokens, temperature, object : LlamaEngine.TokenListener {
            override fun onToken(token: String) {
                full.append(token)
                runOnWebView("window.onToken(${WebBridge.jsString(token)})")
            }

            override fun onDone(fullText: String) {
                generating = false
                runOnWebView("window.onGenDone()")
            }

            override fun onError(message: String) {
                generating = false
                runOnWebView("window.onGenError(${WebBridge.jsString(message)})")
            }
        })
    }

    fun stopGeneration() {
        if (ctxPtr != 0L && generating) {
            LlamaEngine.nativeStop(ctxPtr)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ctxPtr != 0L) LlamaEngine.nativeFree(ctxPtr)
    }
}
