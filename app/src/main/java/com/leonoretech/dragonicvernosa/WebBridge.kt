package com.leonoretech.dragonicvernosa

import android.webkit.JavascriptInterface
import androidx.core.os.bundleOf
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Dipanggil dari JS lewat window.AndroidBridge.xxx(...)
 * Semua hasil balik ke JS lewat MainActivity.runOnWebView(jsCode).
 */
class WebBridge(private val activity: MainActivity) {

    private var genThread: Thread? = null

    @JavascriptInterface
    fun checkModelStatus() {
        activity.runOnUiThread {
            val ready = activity.isModelReady()
            activity.runOnWebView("window.onModelStatus(${ready})")
        }
    }

    @JavascriptInterface
    fun pickModelFile() {
        activity.launchFilePicker()
    }

    @JavascriptInterface
    fun downloadModel(url: String) {
        activity.startModelDownload(url)
    }

    @JavascriptInterface
    fun deleteModel() {
        activity.deleteModelAndReset()
    }

    @JavascriptInterface
    fun sendPrompt(promptJson: String) {
        // promptJson: {"prompt": "...", "maxTokens": 256, "temperature": 0.8}
        genThread = thread(name = "gen-thread") {
            try {
                val obj = JSONObject(promptJson)
                val prompt = obj.getString("prompt")
                val maxTokens = obj.optInt("maxTokens", 256)
                val temperature = obj.optDouble("temperature", 0.8).toFloat()

                activity.generate(prompt, maxTokens, temperature)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    activity.runOnWebView("window.onGenError(${jsString(e.message ?: "error")})")
                }
            }
        }
    }

    @JavascriptInterface
    fun stopGeneration() {
        activity.stopGeneration()
    }

    companion object {
        fun jsString(raw: String): String {
            // escape buat aman dimasukin ke evaluateJavascript
            return JSONObject().put("v", raw).toString().substringAfter(":").trimEnd('}')
        }
    }
}
