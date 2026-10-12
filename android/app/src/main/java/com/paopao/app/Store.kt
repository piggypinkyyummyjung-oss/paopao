package com.paopao.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences ก้อนเดียวของแอป: data (ข้อมูลเว็บ), widget (ข้อมูล widget), pending (คิวจาก widget) */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("paopao", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String? = null): String? = p(c).getString(k, d)
    fun put(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()

    @Synchronized fun addPending(c: Context, o: JSONObject) {
        val a = JSONArray(get(c, "pending", "[]") ?: "[]")
        a.put(o)
        put(c, "pending", a.toString())
    }

    @Synchronized fun takePending(c: Context): String {
        val v = get(c, "pending", "[]") ?: "[]"
        put(c, "pending", "[]")
        return v
    }
}
