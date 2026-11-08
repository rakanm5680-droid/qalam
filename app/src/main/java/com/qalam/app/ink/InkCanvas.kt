package com.qalam.app.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.qalam.app.data.Ink
import com.qalam.app.data.PAGE_W
import com.qalam.app.data.Pt
import com.qalam.app.data.Tool
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val HL_ALPHA = 0.35f
private const val HL_MULT = 4f
private const val ERASE_RADIUS = 16f

/**
 * لوحة الكتابة. الإحداثيات المخزّنة بوحدات الصفحة (عرض 1000) فتبقى صح على أي حجم شاشة.
 * - القلم (Stylus) يرسم دايماً. الإصبع يرسم فقط لو [drawFingers] مفعّل، وإلا يتمرّر الصفحة.
 * - [onSelect] يرجّع مستطيل اللقطة كنسب (0..1) من عرض/ارتفاع الصفحة.
 */
@Composable
fun InkCanvas(
    strokes: SnapshotStateList<Ink>,
    tool: Tool,
    color: Color,
    widthUnits: Float,
    smooth: Boolean,
    drawFingers: Boolean,
    modifier: Modifier = Modifier,
    onCommit: () -> Unit,
    onSelect: (Rect) -> Unit = {},
) {
    val live = remember { ArrayList<Pt>() }
    var tick by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Rect?>(null) }

    Canvas(
        modifier.pointerInput(tool, color, widthUnits, smooth, drawFingers) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val stylus = down.type == PointerType.Stylus
                if (!stylus && !drawFingers) return@awaitEachGesture
                val scale = size.width / PAGE_W
                down.consume()

                fun pt(pos: Offset, pressure: Float) =
                    Pt(pos.x / scale, pos.y / scale, if (stylus) pressure.coerceIn(0.05f, 1f) else 0.5f)

                fun erase(pos: Offset) {
                    val ux = pos.x / scale
                    val uy = pos.y / scale
                    strokes.removeAll { hit(it, ux, uy, ERASE_RADIUS) }
                }

                when (tool) {
                    Tool.Select -> {
                        val start = down.position
                        sel = Rect(start, start)
                        drag(down.id) { ch ->
                            val p = ch.position
                            sel = Rect(min(start.x, p.x), min(start.y, p.y), max(start.x, p.x), max(start.y, p.y))
                            ch.consume()
                        }
                        val r = sel
                        sel = null
                        if (r != null && r.width > 24f && r.height > 24f) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            onSelect(
                                Rect(
                                    (r.left / w).coerceIn(0f, 1f), (r.top / h).coerceIn(0f, 1f),
                                    (r.right / w).coerceIn(0f, 1f), (r.bottom / h).coerceIn(0f, 1f),
                                ),
                            )
                        }
                    }

                    Tool.Eraser -> {
                        erase(down.position)
                        drag(down.id) { ch -> erase(ch.position); ch.consume() }
                        onCommit()
                    }

                    else -> {
                        live.clear()
                        live.add(pt(down.position, down.pressure))
                        tick++
                        drag(down.id) { ch ->
                            live.add(pt(ch.position, ch.pressure))
                            tick++
                            ch.consume()
                        }
                        val raw = live.toList()
                        live.clear()
                        tick++
                        if (raw.isNotEmpty()) {
                            val hl = tool == Tool.Highlighter
                            val pts = if (smooth && !hl) refine(raw) else raw
                            strokes.add(Ink(pts, color.toArgb(), if (hl) widthUnits * HL_MULT else widthUnits, hl))
                            onCommit()
                        }
                    }
                }
            }
        },
    ) {
        @Suppress("UNUSED_VARIABLE") val subscribe = tick
        val scale = size.width / PAGE_W
        strokes.forEach { drawInk(it, scale) }
        if (live.isNotEmpty()) {
            val hl = tool == Tool.Highlighter
            drawInk(Ink(live.toList(), color.toArgb(), if (hl) widthUnits * HL_MULT else widthUnits, hl), scale)
        }
        sel?.let {
            drawRect(Color(0x332962FF), it.topLeft, it.size)
            drawRect(Color(0xFF2962FF), it.topLeft, it.size, style = Stroke(width = 3f))
        }
    }
}

fun DrawScope.drawInk(s: Ink, scale: Float) {
    val pts = s.pts
    if (pts.isEmpty()) return
    val col = Color(s.color)
    if (pts.size == 1) {
        drawCircle(
            if (s.highlighter) col.copy(alpha = HL_ALPHA) else col,
            s.width * scale / 2f,
            Offset(pts[0].x * scale, pts[0].y * scale),
        )
        return
    }
    if (s.highlighter) {
        val path = Path().apply {
            moveTo(pts[0].x * scale, pts[0].y * scale)
            for (i in 1 until pts.size) lineTo(pts[i].x * scale, pts[i].y * scale)
        }
        drawPath(
            path, col.copy(alpha = HL_ALPHA),
            style = Stroke(width = s.width * scale, cap = StrokeCap.Butt, join = StrokeJoin.Round),
        )
    } else {
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val w = s.width * scale * (0.6f + 0.8f * ((a.p + b.p) / 2f))
            drawLine(
                col, Offset(a.x * scale, a.y * scale), Offset(b.x * scale, b.y * scale),
                strokeWidth = w, cap = StrokeCap.Round,
            )
        }
    }
}

private fun hit(s: Ink, x: Float, y: Float, r: Float): Boolean {
    val pts = s.pts
    val reach = r + s.width / 2f
    if (pts.size == 1) return hypot(pts[0].x - x, pts[0].y - y) < reach
    for (i in 1 until pts.size) {
        if (distSeg(x, y, pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y) < reach) return true
    }
    return false
}
