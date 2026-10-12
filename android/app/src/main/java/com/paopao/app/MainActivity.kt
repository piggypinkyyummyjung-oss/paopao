package com.paopao.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {
    lateinit var web: WebView
    private var fcb: ValueCallback<Array<Uri>>? = null
    private var hiddenAt = 0L

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fcb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fcb = null
    }

    private var pendingExport: String? = null
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val t = pendingExport; pendingExport = null
        if (uri != null && t != null) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(t.toByteArray()) }
                web.evaluateJavascript("window.__exported&&__exported(true)", null)
            } catch (e: Exception) { Toast.makeText(this, "สำรองไม่สำเร็จ", Toast.LENGTH_LONG).show() }
        }
    }

    /** เปิดหน้าต่างเลือกที่เก็บของระบบ (Google Drive, ไฟล์ในเครื่อง ฯลฯ) */
    fun exportFile(name: String, text: String) { pendingExport = text; createDoc.launch(name) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        setContentView(web)
        ref = WeakReference(web)
        WebView.setWebContentsDebuggingEnabled(true)
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true }
        web.addJavascriptInterface(Bridge(this), "Android")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fcb?.onReceiveValue(null); fcb = cb
                return try { pick.launch(p.createIntent()); true } catch (e: Exception) { fcb = null; false }
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onStart() {
        super.onStart()
        val away = System.currentTimeMillis() - hiddenAt
        if (Store.get(this, "lock") == "1" && (hiddenAt == 0L || away > 30000)) {
            web.visibility = View.INVISIBLE
            showBio(true)
        }
    }

    override fun onStop() { super.onStop(); hiddenAt = System.currentTimeMillis() }
    override fun onResume() { super.onResume(); web.evaluateJavascript("window.__drain&&__drain()", null) }

    /** gate=true: ล็อกตอนเปิดแอป (ยกเลิก = ออก) · gate=false: เรียกจากหน้า PIN ของเว็บ */
    fun showBio(gate: Boolean) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("เปิด Paopao")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                web.visibility = View.VISIBLE
                web.evaluateJavascript("window.__bio&&__bio(true)", null)
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) { if (gate) finish() }
        }).authenticate(info)
    }

    companion object {
        private var ref: WeakReference<WebView>? = null
        /** ให้ widget สั่งดึงคิวเข้าเว็บทันทีถ้าแอปเปิดอยู่ */
        fun drainNow() { ref?.get()?.post { ref?.get()?.evaluateJavascript("window.__drain&&__drain()", null) } }
    }
}
