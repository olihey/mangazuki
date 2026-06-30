package com.mangaread

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun App(viewModel: LibraryViewModel, onPickFolder: () -> Unit) {
    MaterialTheme {
        val series by viewModel.series.collectAsState()
        val scanning by viewModel.scanning.collectAsState()
        LibraryScreen(series = series, scanning = scanning, onPickFolder = onPickFolder)
    }
}
