package com.qalam.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qalam.app.ai.Claude
import com.qalam.app.data.Ink
import com.qalam.app.data.PAGE_RATIO
import com.qalam.app.data.PAGE_W
import com.qalam.app.data.Prefs
import com.qalam.app.data.Store
import com.qalam.app.data.Tool
import com.qalam.app.ink.InkCanvas
import com.qalam.app.ink.cropFraction
import com.qalam.app.ink.renderPage
import com.qalam.app.ink.saveToGallery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** لقطة مقصوصة من صفحة: [clean] للترجمة (بدون خط يد لو PDF)، [withInk] للحفظ. */
class ShotState(val clean: Bitmap, val withInk: Bitmap)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    st: EditorState,
    store: Store,
    prefs: Prefs,
    onBack: () -> Unit,
    onChat: () -> Unit,
) {
    var tool by remember { mutableStateOf(Tool.Pen) }
    var color by remember { mutableStateOf(Color(0xFF111111)) }
    var width by remember { mutableFloatStateOf(3f) }
    var smooth by remember { mutableStateOf(true) }
    var finger by remember { mutableStateOf(true) }
    var version by remember { mutableIntStateOf(0) }
    var lastPage by remember { mutableIntStateOf(0) }
    var shot by remember { mutableStateOf<ShotState?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(st.note.title) }
    val scope = rememberCoroutineScope()

    // حفظ تلقائي بعد ما تهدا الكتابة
    LaunchedEffect(version) {
        if (version > 0) {
            delay(1200)
            st.snapshot()
            st.note.title = title
            store.save(st.note)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, modifier = Modifier.clickable { renaming = true }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
                actions = {
                    if (st.pdf == null) {
                        TextButton(onClick = { st.pages.add(mutableStateListOf()); version++ }) { Text("+ صفحة") }
                    }
                    TextButton(onClick = onChat) { Text("شات AI") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ToolBar(
                tool = tool, onTool = { tool = it },
                color = color, onColor = { color = it },
                width = width, onWidth = { width = it },
                smooth = smooth, onSmooth = { smooth = it },
                finger = finger, onFinger = { finger = it },
                onUndo = {
                    st.pages.getOrNull(lastPage)?.let { if (it.isNotEmpty()) { it.removeAt(it.lastIndex); version++ } }
                },
            )
            if (tool == Tool.Select) {
                Text(
                    "اسحب مستطيل على المنطقة اللي بدك تاخد لها لقطة.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().background(Color(0xFFDADADA)),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(st.pages) { i, strokes ->
                    PageView(
                        st = st, index = i, strokes = strokes,
                        tool = tool, color = color, width = width, smooth = smooth, finger = finger,
                        onCommit = { lastPage = i; version++ },
                        onSelect = { r ->
                            scope.launch {
                                val ink = renderPage(st.pdf, i, strokes.toList(), 1600)
                                val clean = if (st.pdf != null) renderPage(st.pdf, i, emptyList(), 1600) else ink
                                shot = ShotState(
                                    cropFraction(clean, r.left, r.top, r.right, r.bottom),
                                    cropFraction(ink, r.left, r.top, r.right, r.bottom),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    shot?.let { ShotDialog(it, prefs) { shot = null } }

    if (renaming) {
        var tmp by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("اسم الملاحظة") },
            text = { OutlinedTextField(tmp, { tmp = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    title = tmp.ifBlank { title }
                    st.note.title = title
                    version++
                    renaming = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun PageView(
    st: EditorState,
    index: Int,
    strokes: SnapshotStateList<Ink>,
    tool: Tool,
    color: Color,
    width: Float,
    smooth: Boolean,
    finger: Boolean,
    onCommit: () -> Unit,
    onSelect: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val bmp by produceState<Bitmap?>(initialValue = null, st.pdf, index) {
        value = st.pdf?.render(index, 1200)
    }
    val ratio = when {
        st.pdf == null -> 1f / PAGE_RATIO
        else -> bmp?.let { it.width.toFloat() / it.height } ?: (1f / PAGE_RATIO)
    }
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        if (st.pdf == null) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / PAGE_W
                var y = 60f
                while (y * scale < size.height) {
                    drawLine(Color(0xFFE3E8EF), Offset(0f, y * scale), Offset(size.width, y * scale), 1.5f)
                    y += 45f
                }
            }
        }
        InkCanvas(
            strokes = strokes, tool = tool, color = color, widthUnits = width,
            smooth = smooth, drawFingers = finger,
            modifier = Modifier.fillMaxSize(),
            onCommit = onCommit, onSelect = onSelect,
        )
    }
}

@Composable
private fun ToolBar(
    tool: Tool, onTool: (Tool) -> Unit,
    color: Color, onColor: (Color) -> Unit,
    width: Float, onWidth: (Float) -> Unit,
    smooth: Boolean, onSmooth: (Boolean) -> Unit,
    finger: Boolean, onFinger: (Boolean) -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Tool.Pen to "قلم", Tool.Highlighter to "ماركر", Tool.Eraser to "ممحاة", Tool.Select to "لقطة")
            .forEach { (t, label) ->
                FilterChip(selected = tool == t, onClick = { onTool(t) }, label = { Text(label) })
            }
        listOf(0xFF111111, 0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFFF9A825).forEach { c ->
            val col = Color(c)
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(col)
                    .border(if (color == col) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onColor(col) },
            )
        }
        listOf(2.5f to "رفيع", 4.5f to "وسط", 8f to "سميك").forEach { (w, label) ->
            FilterChip(selected = width == w, onClick = { onWidth(w) }, label = { Text(label) })
        }
        FilterChip(selected = smooth, onClick = { onSmooth(!smooth) }, label = { Text("تحسين الخط") })
        FilterChip(selected = finger, onClick = { onFinger(!finger) }, label = { Text("رسم بالإصبع") })
        AssistChip(onClick = onUndo, label = { Text("تراجع") })
    }
}

@Composable
private fun ShotDialog(shot: ShotState, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun run(block: suspend (String) -> String) {
        val key = prefs.apiKey
        if (key.isBlank()) {
            error = "حط مفتاح Claude API من الإعدادات أول."
            return
        }
        loading = true
        error = null
        result = null
        scope.launch {
            try {
                result = block(key)
            } catch (e: Exception) {
                error = e.message ?: "صار خطأ"
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("لقطة") },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    shot.withInk.asImageBitmap(), null,
                    Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    contentScale = ContentScale.Fit,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { run { Claude.translate(it, shot.clean, prefs.lang) } },
                        enabled = !loading, modifier = Modifier.weight(1f),
                    ) { Text("ترجمة") }
                    OutlinedButton(
                        onClick = { run { Claude.explain(it, shot.clean, prefs.lang) } },
                        enabled = !loading, modifier = Modifier.weight(1f),
                    ) { Text("اشرح") }
                    OutlinedButton(
                        onClick = { saved = saveToGallery(ctx, shot.withInk) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (saved) "انحفظت ✓" else "حفظ") }
                }
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                result?.let { SelectionContainer { Text(it) } }
            }
        },
    )
}
