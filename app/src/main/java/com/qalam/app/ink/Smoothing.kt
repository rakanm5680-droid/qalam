package com.qalam.app.ink

import com.qalam.app.data.Pt
import kotlin.math.hypot

/**
 * تحسين الخط: نبسّط النقاط (Ramer–Douglas–Peucker) لإزالة الرجفة،
 * بعدين نعمل تنعيم Chaikin عشان الخط يطلع منحني وسلس.
 */
fun refine(raw: List<Pt>): List<Pt> {
    if (raw.size < 3) return raw
    var pts = rdp(raw, 1.2f)
    if (pts.size < 3) return pts
    repeat(2) { pts = chaikin(pts) }
    return pts
}

private fun rdp(p: List<Pt>, eps: Float): List<Pt> {
    val keep = BooleanArray(p.size)
    keep[0] = true
    keep[p.lastIndex] = true
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, p.lastIndex))
    while (stack.isNotEmpty()) {
        val (s, e) = stack.removeLast()
        var maxD = 0f
        var idx = -1
        for (i in s + 1 until e) {
            val d = distSeg(p[i].x, p[i].y, p[s].x, p[s].y, p[e].x, p[e].y)
            if (d > maxD) { maxD = d; idx = i }
        }
        if (idx >= 0 && maxD > eps) {
            keep[idx] = true
            stack.addLast(intArrayOf(s, idx))
            stack.addLast(intArrayOf(idx, e))
        }
    }
    return p.filterIndexed { i, _ -> keep[i] }
}

private fun chaikin(p: List<Pt>): List<Pt> {
    val o = ArrayList<Pt>(p.size * 2)
    o.add(p.first())
    for (i in 0 until p.lastIndex) {
        o.add(lerp(p[i], p[i + 1], 0.25f))
        o.add(lerp(p[i], p[i + 1], 0.75f))
    }
    o.add(p.last())
    return o
}

private fun lerp(a: Pt, b: Pt, t: Float) =
    Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.p + (b.p - a.p) * t)

/** المسافة من النقطة (px,py) للقطعة a-b. */
fun distSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
