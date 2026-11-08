package com.qalam.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.qalam.app.data.NoteDoc
import com.qalam.app.data.PdfDoc
import com.qalam.app.data.Prefs
import com.qalam.app.data.Store
import com.qalam.app.ui.ChatScreen
import com.qalam.app.ui.EditorScreen
import com.qalam.app.ui.EditorState
import com.qalam.app.ui.HomeScreen
import com.qalam.app.ui.SettingsScreen
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        val prefs = Prefs(applicationContext)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(store, prefs)
                }
            }
        }
    }
}

private enum class Screen { Home, Editor, Chat, Settings }

@Composable
private fun App(store: Store, prefs: Prefs) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var notes by remember { mutableStateOf(store.list()) }
    var editor by remember { mutableStateOf<EditorState?>(null) }

    fun openNote(n: NoteDoc) {
        val pdf = if (n.pdfPath != null) {
            runCatching { PdfDoc(File(n.pdfPath!!)) }.getOrNull()
        } else null
        if (n.pdfPath != null && pdf == null) {
            Toast.makeText(ctx, "ما قدرت أفتح الـ PDF (يمكن محمي بكلمة سر أو تالف)", Toast.LENGTH_LONG).show()
            return
        }
        val count = pdf?.pageCount ?: maxOf(1, n.pages.size)
        n.pages = List(count) { n.pages.getOrNull(it) ?: emptyList() }
        editor = EditorState(n, pdf)
        screen = Screen.Editor
    }

    fun closeEditor() {
        editor?.let {
            it.snapshot()
            store.save(it.note)
            it.pdf?.close()
        }
        editor = null
        notes = store.list()
        screen = Screen.Home
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { store.importPdf(uri) }
                .onSuccess { store.save(it); openNote(it) }
                .onFailure { Toast.makeText(ctx, "فشل استيراد الملف", Toast.LENGTH_LONG).show() }
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        when (screen) {
            Screen.Chat -> screen = Screen.Editor
            Screen.Editor -> closeEditor()
            else -> screen = Screen.Home
        }
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            notes = notes,
            onNew = { val n = store.newBlank(); store.save(n); openNote(n) },
            onImportPdf = { picker.launch(arrayOf("application/pdf")) },
            onOpen = { openNote(it) },
            onDelete = { store.delete(it); notes = store.list() },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Editor -> editor?.let {
            EditorScreen(it, store, prefs, onBack = { closeEditor() }, onChat = { screen = Screen.Chat })
        }
        Screen.Chat -> editor?.let { ChatScreen(it, prefs, onBack = { screen = Screen.Editor }) }
        Screen.Settings -> SettingsScreen(prefs, onBack = { screen = Screen.Home })
    }
}
