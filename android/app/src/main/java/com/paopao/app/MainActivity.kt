package com.paopao.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: AndroidBridge

    // รับ event จาก Widget
    private val widgetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cat = intent.getStringExtra(PaopaoWidget.EXTRA_CAT) ?: return
            val type = intent.getStringExtra(PaopaoWidget.EXTRA_TYPE) ?: "add"
            val amt = intent.getDoubleExtra("amt", 100.0)
            runOnUiThread {
                val js = "javascript:if(window.__widgetAdd){window.__widgetAdd('$cat',$amt,'$type')}"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        bridge = AndroidBridge(this, webView)
        webView.addJavascriptInterface(bridge, "Android")

        // Status bar สีดำ
        window.statusBarColor = android.graphics.Color.parseColor("#000000")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = 0
        }

        webView.webViewClient = WebViewClient()
        webView.loadUrl("file:///android_asset/index.html")
    }

    // ลงทะเบียนรับ event จาก Widget ตอนเปิดแอป
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PaopaoWidget.ACTION_WIDGET_EVENT)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(widgetReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(widgetReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ยกเลิกตอนปิดแอป
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(widgetReceiver)
        } catch (e: Exception) {
            // ไม่ได้ register ไว้
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
