package com.paopao.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

/**
 * เปิดจาก widget
 *  - แตะช่องจำนวนเงิน (ไม่มี kind)  → กรอกยอดไว้
 *  - แตะ − / + (kind = out|in)      → ถ้ามียอดแล้วบันทึกทันที ถ้ายังไม่มีให้กรอกก่อนแล้วบันทึก
 */
class AmountActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val kind = intent.getStringExtra("kind")
        // กดรับเข้าและตั้งหมวดรับไว้หลายหมวด → ต้องเลือกหมวดก่อน จึงเปิดหน้าต่างเสมอ
        val inc = if (kind == "in") PaopaoWidget.incomeCats(this) else emptyList()
        val needPick = inc.size > 1
        if (kind != null && PaopaoWidget.amt(this) > 0 && !needPick) {
            PaopaoWidget.apply(this, kind == "in")
            finish(); overridePendingTransition(0, 0); return
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        root.addView(TextView(this).apply {
            text = when (kind) { "in" -> "💵 รับเข้า"; "out" -> "💸 ใช้จ่าย · " + PaopaoWidget.selCat(this@AmountActivity); else -> "ใส่จำนวนเงิน" }
            textSize = 18f
        })
        val spinner = if (needPick) Spinner(this).apply {
            adapter = ArrayAdapter(this@AmountActivity, android.R.layout.simple_spinner_dropdown_item, inc)
        } else null
        spinner?.let { root.addView(it) }
        val et = EditText(this).apply {
            hint = "จำนวนเงิน (บาท)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            PaopaoWidget.amt(this@AmountActivity).takeIf { it > 0 }?.let { setText(it.toString().removeSuffix(".0")) }
        }
        val quick = LinearLayout(this)
        for (n in intArrayOf(10, 50, 100, 1000)) quick.addView(Button(this).apply {
            text = "+$n"; textSize = 12f
            setOnClickListener {
                val cur = et.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                et.setText((cur + n).toString().removeSuffix(".0")); et.setSelection(et.text.length)
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val row = LinearLayout(this).apply { gravity = Gravity.END }
        row.addView(Button(this).apply { text = "ยกเลิก"; setOnClickListener { finish() } })
        row.addView(Button(this).apply {
            text = if (kind == null) "ตกลง" else "บันทึก"
            setOnClickListener {
                val v = et.text.toString().replace(",", "").toDoubleOrNull()
                if (v == null || v <= 0) { et.error = "ใส่จำนวนเงิน"; return@setOnClickListener }
                PaopaoWidget.setAmt(this@AmountActivity, v)
                if (kind != null) PaopaoWidget.apply(this@AmountActivity, kind == "in", spinner?.selectedItem?.toString())
                finish()
            }
        })
        root.addView(et); root.addView(quick); root.addView(row)
        setContentView(root)
    }
}
