package com.qalam.app.data

import android.content.Context

/** إعدادات بسيطة. مفتاح الـ API محفوظ محلياً في مساحة التطبيق الخاصة. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("qalam", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("key", "") ?: ""
        set(v) { sp.edit().putString("key", v.trim()).apply() }

    var lang: String
        get() = sp.getString("lang", "العربية") ?: "العربية"
        set(v) { sp.edit().putString("lang", v.trim().ifEmpty { "العربية" }).apply() }
}
