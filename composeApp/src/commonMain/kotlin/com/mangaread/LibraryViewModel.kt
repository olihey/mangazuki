package com.mangaread

import com.mangaread.core.data.LibraryRepository
import com.mangaread.core.domain.Series
import com.mangaread.core.domain.nowEpochMillis
import com.mangaread.core.scanner.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 1 spine: observe the library reactively from the DB, and run a scan that upserts
 * into it. Plain class (not androidx.ViewModel) so it stays in commonMain; the SAF folder
 * pick is supplied by the platform layer.
 */
class LibraryViewModel(
    private val repository: LibraryRepository,
    private val scanner: LibraryScanner,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val series: StateFlow<List<Series>> =
        repository.observeSeries().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    fun scan(rootLocator: String) {
        scope.launch {
            _scanning.value = true
            try {
                val result = scanner.scan(rootLocator, nowEpochMillis())
                repository.persistScan(result.series, result.chapters)
            } finally {
                _scanning.value = false
            }
        }
    }
}
