package com.mangaread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangaread.core.data.ChapterCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
) {
    val series by viewModel.series.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val nextUnread = chapters.firstOrNull { !it.completed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            series?.author?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            series?.description?.let {
                Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (nextUnread != null) {
                Button(onClick = { onChapterClick(nextUnread.id) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Continue: ${nextUnread.displayName}")
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                chapters.groupBy { it.volume }.toSortedMap(compareBy { it ?: Double.MAX_VALUE }).forEach { (volume, vChapters) ->
                    if (volume != null) {
                        item { Text("Volume ${volume.toInt()}", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleSmall) }
                    }
                    items(vChapters, key = { it.id }) { chapter ->
                        ChapterRow(chapter, onClick = { onChapterClick(chapter.id) }, onToggleRead = { viewModel.toggleRead(chapter) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterCard, onClick: () -> Unit, onToggleRead: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(chapter.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (chapter.lastPageIndex > 0 && !chapter.completed) {
            { Text("Page ${chapter.lastPageIndex + 1}") }
        } else null,
        trailingContent = { ReadBadge(chapter, onToggleRead) },
    )
}

/** Unread → nothing; in progress → filled ring; finished → check disc (PLAN.md §7.2). */
@Composable
private fun ReadBadge(chapter: ChapterCard, onToggleRead: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(50))
            .background(if (chapter.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggleRead),
        contentAlignment = Alignment.Center,
    ) {
        if (chapter.completed) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
    }
}
