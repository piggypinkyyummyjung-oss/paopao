package com.paopao.app

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/** ชื่อเมธอดตรงกับที่ js/app.js เรียกผ่าน window.Android */
class Bridge(private val a: MainActivity) {
    private fun js(code: String) = a.runOnUiThread { a.web.evaluateJavascript(code, null) }

    @JavascriptInterface fun load(): String? = Store.get(a, "data")
    @JavascriptInterface fun save(j: String) = Store.put(a, "data", j)
    @JavascriptInterface fun setWidget(j: String) { Store.put(a, "widget", j); PaopaoWidget.updateAll(a) }
    @JavascriptInterface fun takePending(): String = Store.takePending(a)
    @JavascriptInterface fun setQuick(j: String) = Store.put(a, "quick", j)
    @JavascriptInterface fun setAlerts(j: String) = Store.put(a, "alerts", j) // TODO: แจ้งเตือนจริง
    @JavascriptInterface fun getLock(): Boolean = Store.get(a, "lock") == "1"
    @JavascriptInterface fun setLock(b: Boolean) = Store.put(a, "lock", if (b) "1" else "0")
    @JavascriptInterface fun exportFile(name: String, mime: String, text: String) = a.runOnUiThread { a.exportFile(name, text) }
    @JavascriptInterface fun bio() = a.runOnUiThread { a.showBio(false) }

    @JavascriptInterface fun price(sym: String) {
        thread {
            try {
                val u = URL("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8"))
                val c = u.openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                c.connectTimeout = 7000; c.readTimeout = 7000
                val p = JSONObject(c.inputStream.bufferedReader().readText())
                    .getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                    .getJSONObject("meta").getDouble("regularMarketPrice")
                js("__price(${JSONObject.quote(sym)},$p)")
            } catch (e: Exception) {
                js("__perr(${JSONObject.quote(sym)})")
            }
        }
    }

    @JavascriptInterface fun saveFile(name: String, mime: String, text: String) {
        try {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = a.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            uri?.let { a.contentResolver.openOutputStream(it)?.use { o -> o.write(text.toByteArray()) } }
            a.runOnUiThread { Toast.makeText(a, "บันทึกใน Downloads: $name", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            a.runOnUiThread { Toast.makeText(a, "บันทึกไฟล์ไม่สำเร็จ", Toast.LENGTH_LONG).show() }
        }
    }
}
