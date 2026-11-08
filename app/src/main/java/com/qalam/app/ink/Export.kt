package com.qalam.app.ink

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.provider.MediaStore
import com.qalam.app.data.Ink
import com.qalam.app.data.PAGE_RATIO
import com.qalam.app.data.PAGE_W
import com.qalam.app.data.PdfDoc

/** يرسم صفحة (PDF أو ورقة بيضا) مع الخطوط عليها لصورة، للّقطات وللشات مع الـ AI. */
suspend fun renderPage(pdf: PdfDoc?, index: Int, strokes: List<Ink>, widthPx: Int): Bitmap {
    val base = pdf?.render(index, widthPx)
        ?: Bitmap.createBitmap(widthPx, (widthPx * PAGE_RATIO).toInt(), Bitmap.Config.ARGB_8888)
            .also { it.eraseColor(Color.WHITE) }
    if (strokes.isEmpty()) return base

    val scale = base.width / PAGE_W
    val canvas = Canvas(base)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    for (s in strokes) {
        val pts = s.pts
        if (pts.isEmpty()) continue
        paint.color = s.color
        if (pts.size == 1) {
            paint.style = Paint.Style.FILL
            if (s.highlighter) paint.alpha = 90
            canvas.drawCircle(pts[0].x * scale, pts[0].y * scale, s.width * scale / 2f, paint)
            paint.style = Paint.Style.STROKE
            continue
        }
        if (s.highlighter) {
            paint.alpha = 90
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeWidth = s.width * scale
            val path = Path().apply {
                moveTo(pts[0].x * scale, pts[0].y * scale)
                for (i in 1 until pts.size) lineTo(pts[i].x * scale, pts[i].y * scale)
            }
            canvas.drawPath(path, paint)
        } else {
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                paint.strokeWidth = s.width * scale * (0.6f + 0.8f * ((a.p + b.p) / 2f))
                canvas.drawLine(a.x * scale, a.y * scale, b.x * scale, b.y * scale, paint)
            }
        }
    }
    return base
}

/** يقص جزء من الصورة حسب نسب (0..1). */
fun cropFraction(b: Bitmap, l: Float, t: Float, r: Float, bt: Float): Bitmap {
    val x = (l * b.width).toInt().coerceIn(0, b.width - 1)
    val y = (t * b.height).toInt().coerceIn(0, b.height - 1)
    val w = ((r - l) * b.width).toInt().coerceIn(1, b.width - x)
    val h = ((bt - t) * b.height).toInt().coerceIn(1, b.height - y)
    return Bitmap.createBitmap(b, x, y, w, h)
}

/** يحفظ الصورة بمعرض الصور (Pictures/Qalam). */
fun saveToGallery(ctx: Context, bmp: Bitmap): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "qalam_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Qalam")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return true
}
