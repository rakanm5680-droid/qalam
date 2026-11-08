package com.qalam.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qalam.app.ai.Claude
import com.qalam.app.data.Prefs
import com.qalam.app.ink.renderPage
import kotlinx.coroutines.launch

private const val MAX_CONTEXT_PAGES = 6

private const val SYSTEM = "You are an assistant inside a note-taking app. The attached images are pages of the " +
    "user's notes (handwriting, typed text, or PDF pages with annotations). Answer questions about them. " +
    "Reply in the user's language; if they write Arabic, use simple colloquial Arabic. Be concise. " +
    "If handwriting is unclear, say what you could not read instead of guessing."

/** شات مع ملاحظتك: نبعت صور أول صفحات فيها محتوى، وClaude يقرأ الخط واللي مكتوب. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(st: EditorState, prefs: Prefs, onBack: () -> Unit) {
    val msgs = remember { mutableStateListOf<Claude.Msg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var context by remember { mutableStateOf<List<Bitmap>?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { context = buildContext(st) }
    LaunchedEffect(msgs.size, busy) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        val key = prefs.apiKey
        if (key.isBlank()) {
            error = "حط مفتاح Claude API من الإعدادات أول."
            return
        }
        input = ""
        error = null
        msgs.add(Claude.Msg("user", q))
        busy = true
        scope.launch {
            try {
                val imgs = context ?: buildContext(st).also { context = it }
                val payload = msgs.mapIndexed { i, m -> if (i == 0) m.copy(images = imgs) else m }
                msgs.add(Claude.Msg("assistant", Claude.ask(key, SYSTEM, payload)))
            } catch (e: Exception) {
                error = e.message ?: "صار خطأ"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("شات: ${st.note.title}", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            )
        },
        modifier = Modifier.imePadding(),
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اسأل عن ملاحظتك…") },
                    maxLines = 4,
                )
                IconButton(onClick = ::send, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "الشات يشوف أول $MAX_CONTEXT_PAGES صفحات فيها محتوى (خط يد، نص، أو صفحات PDF).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(msgs) { m ->
                    val mine = m.role == "user"
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(0.88f),
                        ) {
                            SelectionContainer { Text(m.text, Modifier.padding(12.dp)) }
                        }
                    }
                }
                if (busy) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

/** الصفحات اللي فيها خط يد، وإذا PDF نكمل بأول الصفحات، حد أقصى MAX_CONTEXT_PAGES. */
private suspend fun buildContext(st: EditorState): List<Bitmap> {
    val inked = st.pages.indices.filter { st.pages[it].isNotEmpty() }
    val chosen = if (st.pdf != null) (inked + st.pages.indices).distinct() else inked
    return chosen.take(MAX_CONTEXT_PAGES).sorted().map { i ->
        renderPage(st.pdf, i, st.pages[i].toList(), 1000)
    }
}
