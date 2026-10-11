package com.paopao.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    // ============ ข้อมูลหลัก ============
    @JavascriptInterface
    fun load(): String? = prefs.getString("data", null)

    @JavascriptInterface
    fun save(json: String) {
        prefs.edit().putString("data", json).apply()
    }

    // ============ ล็อกแอป ============
    @JavascriptInterface
    fun getLock(): Boolean = prefs.getBoolean("lock", false)

    @JavascriptInterface
    fun setLock(enabled: Boolean) {
        prefs.edit().putBoolean("lock", enabled).apply()
    }

    // ============ สำรองไฟล์ ============
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

    // ============ ลายนิ้วมือ ============
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

    // ============ ราคาหุ้น ============
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

    // ============ 🆕 Widget ============
    @JavascriptInterface
    fun setWidget(json: String) {
        prefs.edit().putString("widget_data", json).apply()
        mainHandler.post {
            try {
                val mgr = AppWidgetManager.getInstance(activity)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(activity, PaopaoWidget::class.java)
                )
                if (ids.isNotEmpty()) {
                    val intent = Intent(activity, PaopaoWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    activity.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ============ 🆕 การแจ้งเตือน ============
    @JavascriptInterface
    fun setAlerts(json: String) {
        prefs.edit().putString("alerts", json).apply()
    }

    @JavascriptInterface
    fun getAlerts(): String? = prefs.getString("alerts", null)
}
