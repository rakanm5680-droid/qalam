package com.qalam.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qalam.app.ai.Claude
import com.qalam.app.data.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    var key by remember { mutableStateOf(prefs.apiKey) }
    var lang by remember { mutableStateOf(prefs.lang) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = key, onValueChange = { key = it; saved = false },
                label = { Text("مفتاح Claude API") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = lang, onValueChange = { lang = it; saved = false },
                label = { Text("لغة الترجمة والشرح") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { prefs.apiKey = key; prefs.lang = lang; saved = true }) {
                Text(if (saved) "انحفظ ✓" else "حفظ")
            }
            Text(
                "المفتاح ينحفظ على جهازك بس، والطلبات تروح مباشرة لـ Anthropic (النموذج: ${Claude.MODEL}). " +
                    "الصور اللي بتترجمها أو بتسأل عنها بتنبعت لهم. " +
                    "لو بدك تنشر التطبيق للناس، مرّر الطلبات عبر سيرفر خاص فيك بدل ما تحط المفتاح بالتطبيق.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
