package com.qalam.app.data

/** كل الصفحات بعرض 1000 وحدة، والارتفاع = العرض × PAGE_RATIO للملاحظات الفاضية. */
const val PAGE_W = 1000f
const val PAGE_RATIO = 1.414f

/** نقطة بخط اليد: x,y بوحدات الصفحة، p = الضغط (0..1). */
data class Pt(val x: Float, val y: Float, val p: Float)

/** خط واحد مرسوم. */
data class Ink(
    val pts: List<Pt>,
    val color: Int,
    val width: Float,
    val highlighter: Boolean,
)

class NoteDoc(
    val id: String,
    var title: String,
    var pdfPath: String?,
    var pages: List<List<Ink>>,
    var updated: Long,
)

enum class Tool { Pen, Highlighter, Eraser, Select }
