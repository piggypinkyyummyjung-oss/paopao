package com.paopao.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Widget 4×2: ซ้าย = ยอดรวม + "วันนี้เหลือใช้" (เปลี่ยนสีเมื่อใกล้/เกินงบ) · ขวา = หมวด x3 + ช่องจำนวนเงิน + ปุ่ม − +
 * หมวด 3 อันตั้งเองในแอป (ตั้งค่า → จัดการรายรับ-รายจ่าย) · widget พิมพ์ตรง ๆ ไม่ได้ แตะช่องแล้วเด้งหน้าต่างกรอก
 */
class PaopaoWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, build(c)) }
    }

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == ACT_SEL) {
            Store.put(c, "wsel", i.getIntExtra("i", 0).toString())
            updateAll(c)
        }
    }

    companion object {
        const val ACT_SEL = "com.paopao.app.SEL"
        private const val WARN = 0.8 // ใช้ถึง 80% ของงบวัน → สีเหลือง, ≥100% → สีแดง
        private val FL = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        fun widgetJson(c: Context): JSONObject =
            try { JSONObject(Store.get(c, "widget", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

        fun strs(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun wcats(c: Context): List<String> {
            val j = widgetJson(c)
            val l = strs(j.optJSONArray("wcats")).ifEmpty { strs(j.optJSONArray("cats")) }
            return l.ifEmpty { listOf("ค่าอาหาร", "ค่าช้อปปิ้ง", "ค่าเดินทาง") }.take(3)
        }

        fun selIdx(c: Context) = (Store.get(c, "wsel", "0")?.toIntOrNull() ?: 0).coerceIn(0, wcats(c).size - 1)
        fun selCat(c: Context) = wcats(c)[selIdx(c)]
        fun incomeCat(c: Context): String {
            val l = strs(widgetJson(c).optJSONArray("catsIn"))
            return if (l.contains("อื่นๆ")) "อื่นๆ" else l.firstOrNull() ?: "อื่นๆ"
        }

        fun amt(c: Context): Double = Store.get(c, "wamt", "0")?.toDoubleOrNull() ?: 0.0
        fun setAmt(c: Context, v: Double) { Store.put(c, "wamt", v.toString()); updateAll(c) }

        /** บันทึกรายการจากยอดที่กรอกไว้ + หมวดที่เลือก */
        fun apply(c: Context, income: Boolean) {
            val a = amt(c)
            if (a <= 0) return
            val cat = if (income) incomeCat(c) else selCat(c)
            val o = JSONObject().put("id", UUID.randomUUID().toString()).put("a", a)
                .put("kind", if (income) "in" else "out").put("c", cat).put("d", LocalDate.now().toString())
            Store.addPending(c, o)
            optimistic(c, !income, a)
            Store.put(c, "wamt", "0")
            updateAll(c)
            MainActivity.drainNow()
            Toast.makeText(c, (if (income) "💵 +" else "💸 −") + fmt(a) + " ฿ · " + cat, Toast.LENGTH_SHORT).show()
        }

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, PaopaoWidget::class.java)).forEach { m.updateAppWidget(it, build(c)) }
        }

        /** อัปเดตตัวเลขทันที (เว็บจะส่งค่าจริงทับให้ตอนดึงคิว) */
        fun optimistic(c: Context, out: Boolean, a: Double) {
            val j = widgetJson(c)
            val today = LocalDate.now().toString()
            if (j.optString("d") != today) {
                val av = j.optDouble("limit", 0.0)
                j.put("left", av); j.put("avail", av); j.put("spent", 0.0); j.put("d", today)
            }
            if (out) {
                j.put("spent", j.optDouble("spent") + a); j.put("left", j.optDouble("left") - a)
                j.put("bal", j.optDouble("bal") - a)
            } else {
                j.put("left", j.optDouble("left") + a); j.put("avail", j.optDouble("avail") + a)
                j.put("bal", j.optDouble("bal") + a)
            }
            Store.put(c, "widget", j.toString())
        }

        private val ICONS = mapOf("อาหาร" to "🍜", "น้ำ" to "💧", "ช้อปปิ้ง" to "🛍️", "เดินทาง" to "🚌", "โทรศัพท์" to "📱",
            "กาแฟ" to "☕", "ของใช้" to "🧴", "บันเทิง" to "🎮", "สุขภาพ" to "💊", "อื่นๆ" to "✨")

        /** "ค่าอาหาร" → "🍜 อาหาร" (หมวดที่ตั้งเองไม่มีไอคอนก็แสดงแค่ชื่อ) */
        fun chipLabel(name: String): String {
            val s = name.removePrefix("ค่า").ifBlank { name }
            val ic = ICONS[s]
            return if (ic != null) "$ic $s" else s
        }

        fun fmt(x: Double): String = NumberFormat.getIntegerInstance(Locale.US).format(Math.round(x))

        private fun accent(n: String) = Color.parseColor(
            when (n) { "blue" -> "#5BB8F5"; "green" -> "#3FCFA0"; "orange" -> "#FF9A76"; "red" -> "#FF7FB0"; "gold" -> "#F5B93F"; else -> "#A07CFF" })

        fun build(c: Context): RemoteViews {
            val j = widgetJson(c)
            val dark = j.optBoolean("dark")
            val rv = RemoteViews(c.packageName, R.layout.widget)
            val ink = if (dark) Color.WHITE else Color.parseColor("#2F2A3A")
            val mute = if (dark) Color.parseColor("#B0AEC0") else Color.parseColor("#8E8A99")
            rv.setInt(R.id.root, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg)

            // 1) หมวด
            val wc = wcats(c); val sel = selIdx(c)
            val ids = intArrayOf(R.id.chip0, R.id.chip1, R.id.chip2)
            for (k in 0..2) {
                if (k >= wc.size) { rv.setViewVisibility(ids[k], View.GONE); continue }
                val on = k == sel
                rv.setViewVisibility(ids[k], View.VISIBLE)
                rv.setTextViewText(ids[k], chipLabel(wc[k]))
                rv.setInt(ids[k], "setBackgroundResource", if (on) R.drawable.chip_on else R.drawable.chip_cat)
                rv.setTextColor(ids[k], if (on) Color.WHITE else Color.parseColor("#7A5AE0"))
                rv.setOnClickPendingIntent(ids[k], PendingIntent.getBroadcast(c, 10 + k,
                    Intent(c, PaopaoWidget::class.java).setAction(ACT_SEL).putExtra("i", k), FL))
            }

            // 2) ช่องจำนวนเงิน
            val a = amt(c)
            rv.setInt(R.id.amt, "setBackgroundResource", if (dark) R.drawable.chip_amt_dark else R.drawable.chip_amt)
            rv.setTextViewText(R.id.amt, if (a > 0) "฿ " + fmt(a) else "฿ แตะใส่เงิน")
            rv.setTextColor(R.id.amt, if (a > 0) ink else mute)
            rv.setOnClickPendingIntent(R.id.amt, PendingIntent.getActivity(c, 1, Intent(c, AmountActivity::class.java), FL))

            // 3) ปุ่ม − / +
            rv.setOnClickPendingIntent(R.id.btn_out, PendingIntent.getActivity(c, 2,
                Intent(c, AmountActivity::class.java).putExtra("kind", "out"), FL))
            rv.setOnClickPendingIntent(R.id.btn_in, PendingIntent.getActivity(c, 3,
                Intent(c, AmountActivity::class.java).putExtra("kind", "in"), FL))

            // 4) ยอดเงินรวม (ตัวใหญ่ เปลี่ยนสีตามการใช้ของวันนี้) + วันนี้เหลือใช้ = ...
            val has = j.has("left")
            val fresh = j.optString("d") == LocalDate.now().toString()
            val limit = j.optDouble("limit", 0.0)
            val avail = if (fresh && j.has("avail")) j.optDouble("avail") else limit
            val left = if (fresh) j.optDouble("left") else limit
            val spent = if (fresh) j.optDouble("spent") else 0.0
            val frac = if (avail > 0) spent / avail else if (spent > 0) 2.0 else 0.0
            val col = when {
                !has -> mute
                left < 0 || frac >= 1.0 -> Color.parseColor("#E5484D")
                frac >= WARN -> Color.parseColor("#F5A623")
                else -> accent(j.optString("color"))
            }
            val noJar = !has && j.has("d")
            rv.setTextViewText(R.id.left, if (has) fmt(j.optDouble("bal")) + " ฿" else if (noJar) "—" else "เปิดแอปก่อน")
            rv.setTextColor(R.id.left, col)
            rv.setTextViewText(R.id.sub, if (has) "วันนี้เหลือใช้ = ${fmt(left)} ฿" else if (noJar) "ยังไม่มีกระปุกรายรับ-รายจ่าย" else "")
            rv.setTextColor(R.id.sub, ink)
            rv.setOnClickPendingIntent(R.id.left, PendingIntent.getActivity(c, 4, Intent(c, MainActivity::class.java), FL))
            rv.setOnClickPendingIntent(R.id.sub, PendingIntent.getActivity(c, 4, Intent(c, MainActivity::class.java), FL))
            return rv
        }
    }
}
