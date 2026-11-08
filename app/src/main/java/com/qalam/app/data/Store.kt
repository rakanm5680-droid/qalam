package com.qalam.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** تخزين الملاحظات كملفات JSON داخل مجلد التطبيق الخاص (بدون قاعدة بيانات). */
class Store(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "notes").apply { mkdirs() }
    private val pdfDir = File(ctx.filesDir, "pdfs").apply { mkdirs() }

    fun list(): List<NoteDoc> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> runCatching { parse(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.updated }

    fun save(n: NoteDoc) {
        n.updated = System.currentTimeMillis()
        val tmp = File(dir, "${n.id}.tmp")
        tmp.writeText(toJson(n).toString())
        tmp.renameTo(File(dir, "${n.id}.json"))
    }

    fun delete(n: NoteDoc) {
        File(dir, "${n.id}.json").delete()
        n.pdfPath?.let { File(it).delete() }
    }

    fun newBlank(): NoteDoc =
        NoteDoc(UUID.randomUUID().toString(), "ملاحظة جديدة", null, listOf(emptyList()), System.currentTimeMillis())

    /** ينسخ الـ PDF لمجلد التطبيق ويرجع ملاحظة جديدة مربوطة فيه. */
    fun importPdf(uri: Uri): NoteDoc {
        val id = UUID.randomUUID().toString()
        val f = File(pdfDir, "$id.pdf")
        val input = ctx.contentResolver.openInputStream(uri) ?: error("ما قدرت أقرأ الملف")
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        var name = "PDF"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx).removeSuffix(".pdf")
        }
        return NoteDoc(id, name, f.absolutePath, emptyList(), System.currentTimeMillis())
    }

    // ---------- JSON ----------
    private fun toJson(n: NoteDoc): JSONObject {
        val pages = JSONArray()
        n.pages.forEach { page ->
            val arr = JSONArray()
            page.forEach { arr.put(inkToJson(it)) }
            pages.put(arr)
        }
        return JSONObject()
            .put("id", n.id).put("title", n.title)
            .put("pdf", n.pdfPath ?: JSONObject.NULL)
            .put("updated", n.updated).put("pages", pages)
    }

    private fun parse(o: JSONObject): NoteDoc {
        val pa = o.getJSONArray("pages")
        val pages = List(pa.length()) { i ->
            val arr = pa.getJSONArray(i)
            List(arr.length()) { j -> jsonToInk(arr.getJSONObject(j)) }
        }
        return NoteDoc(
            o.getString("id"), o.getString("title"),
            if (o.isNull("pdf")) null else o.getString("pdf"),
            pages, o.getLong("updated"),
        )
    }

    private fun inkToJson(s: Ink): JSONObject {
        val a = JSONArray()
        s.pts.forEach { a.put(it.x.toDouble()); a.put(it.y.toDouble()); a.put(it.p.toDouble()) }
        return JSONObject().put("c", s.color).put("w", s.width.toDouble()).put("h", s.highlighter).put("p", a)
    }

    private fun jsonToInk(o: JSONObject): Ink {
        val a = o.getJSONArray("p")
        val pts = ArrayList<Pt>(a.length() / 3)
        var i = 0
        while (i + 2 < a.length()) {
            pts.add(Pt(a.getDouble(i).toFloat(), a.getDouble(i + 1).toFloat(), a.getDouble(i + 2).toFloat()))
            i += 3
        }
        return Ink(pts, o.getInt("c"), o.getDouble("w").toFloat(), o.optBoolean("h"))
    }
}
