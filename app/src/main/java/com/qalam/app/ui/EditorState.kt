package com.qalam.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.qalam.app.data.Ink
import com.qalam.app.data.NoteDoc
import com.qalam.app.data.PdfDoc

/** حالة الملاحظة المفتوحة (تتشارك بين المحرر وشاشة الشات). */
class EditorState(val note: NoteDoc, val pdf: PdfDoc?) {
    val pages: SnapshotStateList<SnapshotStateList<Ink>> = mutableStateListOf<SnapshotStateList<Ink>>().also { list ->
        note.pages.forEach { list.add(it.toMutableStateList()) }
    }

    fun snapshot() {
        note.pages = pages.map { it.toList() }
    }
}
