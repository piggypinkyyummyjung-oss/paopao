package com.paopao.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File

class AndroidBridge(
    private val activity: Activity,
    private val webView: WebView
) {

    private val prefs = activity.getSharedPreferences("paopao", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun load(): String? = prefs.getString("data", null)

    @JavascriptInterface
    fun save(json: String) {
        prefs.edit().putString("data", json).apply()
    }

    @JavascriptInterface
    fun getLock(): Boolean = prefs.getBoolean("lock", false)

    @JavascriptInterface
    fun setLock(enabled: Boolean) {
        prefs.edit().putBoolean("lock", enabled).apply()
    }

    @JavascriptInterface
    fun saveFile(name: String, mime: String, text: String) {
        try {
            val dir = File(activity.getExternalFilesDir(null), "backup")
            if (!dir.exists()) dir.mkdirs()
            File(dir, name).writeText(text)
            mainHandler.post {
                webView.evaluateJavascript(
                    "javascript:toast('บันทึกไฟล์ที่ ${dir.absolutePath}/$name')",
                    null
                )
            }
        } catch (e: Exception) {
            mainHandler.post {
                webView.evaluateJavascript(
                    "javascript:toast('บันทึกไฟล์ไม่สำเร็จ')",
                    null
                )
            }
        }
    }

    @JavascriptInterface
    fun bio() {
        val act = activity as? FragmentActivity ?: return
        mainHandler.post {
            val executor = ContextCompat.getMainExecutor(act)
            val prompt = BiometricPrompt(act, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        webView.evaluateJavascript("window.__bio(true)", null)
                    }

                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        super.onAuthenticationError(code, msg)
                        webView.evaluateJavascript("window.__bio(false)", null)
                    }
                })

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("ปลดล็อก Paopao")
                .setSubtitle("ใช้ลายนิ้วมือหรือใบหน้า")
                .setNegativeButtonText("ยกเลิก")
                .build()

            prompt.authenticate(info)
        }
    }

    // ดึงราคาหุ้น — ตัวอย่างจำลอง (ต่อ API จริงภายหลังได้)
    @JavascriptInterface
    fun price(symbol: String) {
        mainHandler.post {
            try {
                val fakePrice = 34.0
                webView.evaluateJavascript(
                    "window.__price('$symbol', $fakePrice)", null
                )
            } catch (e: Exception) {
                webView.evaluateJavascript("window.__perr('$symbol')", null)
            }
        }
    }
}
