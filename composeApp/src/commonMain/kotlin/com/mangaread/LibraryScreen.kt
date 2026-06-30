package com.mangaread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mangaread.core.domain.Series

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(series: List<Series>, scanning: Boolean, onPickFolder: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onPickFolder) { Text("+", Modifier.padding(4.dp)) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            if (series.isEmpty()) {
                Text(
                    "No series yet — tap + to pick your manga folder.",
                    Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(series, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title) },
                            supportingContent = { Text(s.sortTitle) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
