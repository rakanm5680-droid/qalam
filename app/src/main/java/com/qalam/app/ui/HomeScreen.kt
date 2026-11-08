package com.qalam.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qalam.app.data.NoteDoc
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    notes: List<NoteDoc>,
    onNew: () -> Unit,
    onImportPdf: () -> Unit,
    onOpen: (NoteDoc) -> Unit,
    onDelete: (NoteDoc) -> Unit,
    onSettings: () -> Unit,
) {
    var toDelete by remember { mutableStateOf<NoteDoc?>(null) }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قلم") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "الإعدادات") } },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onNew, modifier = Modifier.weight(1f)) { Text("ملاحظة جديدة") }
                OutlinedButton(onClick = onImportPdf, modifier = Modifier.weight(1f)) { Text("استيراد PDF") }
            }
        },
    ) { pad ->
        if (notes.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ما عندك ملاحظات لسا", style = MaterialTheme.typography.titleMedium)
                Text("ابدأ بملاحظة جديدة أو استورد ملف PDF وعلّق عليه.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id }) { n ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(n) }) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    (if (n.pdfPath != null) "PDF · " else "") + df.format(Date(n.updated)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { toDelete = n }) { Icon(Icons.Default.Delete, "حذف") }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف \"${n.title}\"؟") },
            text = { Text("ما في رجعة بعد الحذف.") },
            confirmButton = { TextButton(onClick = { onDelete(n); toDelete = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء") } },
        )
    }
}
