package com.qalam.app.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * عميل بسيط لـ Claude Messages API.
 * ملاحظة أمان: المفتاح محفوظ على جهاز المستخدم. لنسخة منشورة للناس الأحسن تمرّ الطلبات عبر سيرفرك.
 */
object Claude {
    const val MODEL = "claude-sonnet-5-5"
    private const val MAX_SIDE = 1568

    data class Msg(val role: String, val text: String, val images: List<Bitmap> = emptyList())

    suspend fun ask(apiKey: String, system: String, msgs: List<Msg>): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        for (m in msgs) {
            val content = JSONArray()
            for (img in m.images) {
                content.put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", toBase64(img)),
                    ),
                )
            }
            content.put(JSONObject().put("type", "text").put("text", m.text))
            messages.put(JSONObject().put("role", m.role).put("content", content))
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 2048)
            .put("system", system)
            .put("messages", messages)

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrDefault("HTTP $code")
                throw IOException(msg)
            }
            val arr = JSONObject(text).getJSONArray("content")
            buildString {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("type") == "text") append(o.getString("text"))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun translate(apiKey: String, image: Bitmap, lang: String): String = ask(
        apiKey,
        "You are a precise translator inside a note-taking app. Output only the translation.",
        listOf(
            Msg(
                "user",
                "ترجم كل النص اللي بالصورة إلى $lang بدقة. حافظ على الترتيب والعناوين والقوائم. " +
                    "لا تضيف شرح ولا مقدمة. إذا ما في نص واضح قول: ما لقيت نص.",
                listOf(image),
            ),
        ),
    )

    suspend fun explain(apiKey: String, image: Bitmap, lang: String): String = ask(
        apiKey,
        "You are a helpful study assistant inside a note-taking app.",
        listOf(Msg("user", "اشرح محتوى هالصورة بشكل بسيط ومختصر باللغة: $lang.", listOf(image))),
    )

    private fun toBase64(src: Bitmap): String {
        val longest = maxOf(src.width, src.height)
        val bmp = if (longest > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
