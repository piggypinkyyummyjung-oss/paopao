package com.paopao.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaopaoWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_ADD = "com.paopao.app.WIDGET_ADD"
        const val ACTION_WIDGET_MINUS = "com.paopao.app.WIDGET_MINUS"
        const val ACTION_WIDGET_EVENT = "com.paopao.app.WIDGET_EVENT"
        const val EXTRA_CAT = "cat"
        const val EXTRA_TYPE = "type"
        const val PREFS = "paopao_widget"
        const val KEY_DATA = "widget_data"
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_ADD, ACTION_WIDGET_MINUS -> {
                val cat = intent.getStringExtra(EXTRA_CAT) ?: "ค่าอาหาร"
                val type = if (intent.action == ACTION_WIDGET_ADD) "add" else "minus"
                val b = Intent(ACTION_WIDGET_EVENT)
                b.putExtra(EXTRA_CAT, cat)
                b.putExtra(EXTRA_TYPE, type)
                context.sendBroadcast(b)
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, PaopaoWidget::class.java)
                )
                for (id in ids) updateWidget(context, mgr, id)
            }
        }
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_paopao)
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, "") ?: ""

        try {
            val json = if (raw.isNotEmpty()) JSONObject(raw) else JSONObject()
            val spent = json.optDouble("spent", 0.0)
            val limit = json.optDouble("limit", 0.0)
            val over = json.optBoolean("over", false)
            val pct = json.optInt("pct", 0)

            views.setTextViewText(R.id.wSpent, "%,.0f ฿".format(spent))
            if (limit > 0) {
                views.setTextViewText(R.id.wLimit, "จาก %,.0f ฿".format(limit))
            } else {
                views.setTextViewText(R.id.wLimit, "ยังไม่กำหนดงบ")
            }

            val spentColor = if (over) Color.parseColor("#FCA5A5") else Color.parseColor("#FFFFFF")
            views.setTextColor(R.id.wSpent, spentColor)
            views.setProgressBar(R.id.wProgress, 100, pct.coerceIn(0, 100), false)

            val cats = json.optJSONArray("cats")
            if (cats != null) {
                if (cats.length() >= 1) {
                    val c1 = cats.getJSONObject(0)
                    views.setTextViewText(R.id.wCat1Name, catEmoji(c1.optString("name")))
                    views.setTextViewText(R.id.wCat1Amt, "%,.0f".format(c1.optDouble("spent", 0.0)))
                    bindCatClick(context, views, R.id.wCat1Box, c1.optString("name"))
                }
                if (cats.length() >= 2) {
                    val c2 = cats.getJSONObject(1)
                    views.setTextViewText(R.id.wCat2Name, catEmoji(c2.optString("name")))
                    views.setTextViewText(R.id.wCat2Amt, "%,.0f".format(c2.optDouble("spent", 0.0)))
                    bindCatClick(context, views, R.id.wCat2Box, c2.optString("name"))
                }
                if (cats.length() >= 3) {
                    val c3 = cats.getJSONObject(2)
                    views.setTextViewText(R.id.wCat3Name, catEmoji(c3.optString("name")))
                    views.setTextViewText(R.id.wCat3Amt, "%,.0f".format(c3.optDouble("spent", 0.0)))
                    bindCatClick(context, views, R.id.wCat3Box, c3.optString("name"))
                }
            }

            val firstCat = if (cats != null && cats.length() > 0)
                cats.getJSONObject(0).optString("name") else "ค่าอาหาร"

            val addIntent = Intent(context, PaopaoWidget::class.java).apply {
                action = ACTION_WIDGET_ADD
                putExtra(EXTRA_CAT, firstCat)
            }
            val addPi = PendingIntent.getBroadcast(
                context, 100, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.wBtnPlus, addPi)

            val minIntent = Intent(context, PaopaoWidget::class.java).apply {
                action = ACTION_WIDGET_MINUS
                putExtra(EXTRA_CAT, firstCat)
            }
            val minPi = PendingIntent.getBroadcast(
                context, 101, minIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.wBtnMinus, minPi)

            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.wUpdate, t)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.wSpent, openPi)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        mgr.updateAppWidget(id, views)
    }

    private fun bindCatClick(context: Context, views: RemoteViews, boxId: Int, catName: String) {
        val i = Intent(context, PaopaoWidget::class.java).apply {
            action = ACTION_WIDGET_ADD
            putExtra(EXTRA_CAT, catName)
        }
        val pi = PendingIntent.getBroadcast(
            context, catName.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(boxId, pi)
    }

    private fun catEmoji(name: String): String = when {
        name.contains("อาหาร") -> "🍜"
        name.contains("ช้อป") -> "🛍️"
        name.contains("เดินทาง") -> "🚗"
        name.contains("น้ำ") -> "💧"
        name.contains("โทร") -> "📱"
        else -> "📌"
    }
}
