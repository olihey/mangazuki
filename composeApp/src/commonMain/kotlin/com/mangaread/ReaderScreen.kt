package com.mangaread

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class TapZone { BACKWARD, FORWARD, MENU }

/**
 * Pure UI over "page N of a chapter" (PLAN.md §8), plus the gesture/polish bundle (§8.1):
 * RTL-aware tap zones, double-tap zoom, keep-screen-on, volume-key paging, and a one-time
 * gesture-help overlay. Double-page spread detection is deferred past this slice.
 */
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val pageCount by viewModel.pageCount.collectAsState()

    if (pageCount <= 0) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    KeepScreenOn(enabled = true)

    val pagerState = rememberPagerState(initialPage = viewModel.currentPage.value.coerceIn(0, pageCount - 1)) { pageCount }
    val scope = rememberCoroutineScope()
    var showChrome by remember { mutableStateOf(true) }
    val showGestureHelp by viewModel.showGestureHelp.collectAsState()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { viewModel.onPageChanged(it) }
    }

    DisposableEffect(viewModel.readingDirectionRtl) {
        VolumeKeyBus.onVolumeKey = { down ->
            val forward = if (viewModel.readingDirectionRtl) !down else down
            scrollBy(pagerState, scope, if (forward) 1 else -1, pageCount)
            true
        }
        onDispose { VolumeKeyBus.onVolumeKey = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = viewModel.readingDirectionRtl,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            ReaderPage(
                pageModel = viewModel.pageModel,
                index = index,
                isRtl = viewModel.readingDirectionRtl,
                onZoneTap = { zone ->
                    when (zone) {
                        TapZone.FORWARD -> scrollBy(pagerState, scope, 1, pageCount)
                        TapZone.BACKWARD -> scrollBy(pagerState, scope, -1, pageCount)
                        TapZone.MENU -> showChrome = !showChrome
                    }
                },
            )
        }
        if (showChrome) {
            Text(
                "${pagerState.currentPage + 1} / $pageCount",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        if (showGestureHelp) {
            GestureHelpOverlay(isRtl = viewModel.readingDirectionRtl, onDismiss = viewModel::dismissGestureHelp)
        }
    }
}

private fun scrollBy(pagerState: PagerState, scope: CoroutineScope, delta: Int, pageCount: Int) {
    val target = (pagerState.currentPage + delta).coerceIn(0, pageCount - 1)
    scope.launch { pagerState.animateScrollToPage(target) }
}

@Composable
private fun ReaderPage(pageModel: String, index: Int, isRtl: Boolean, onZoneTap: (TapZone) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(isRtl) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 1f) {
                            scale = 1f
                            origin = TransformOrigin.Center
                        } else {
                            scale = 2.5f
                            origin = TransformOrigin(pos.x / size.width, pos.y / size.height)
                        }
                    },
                    onTap = { pos ->
                        val third = size.width / 3f
                        val zone = when {
                            pos.x < third -> if (isRtl) TapZone.FORWARD else TapZone.BACKWARD
                            pos.x > third * 2 -> if (isRtl) TapZone.BACKWARD else TapZone.FORWARD
                            else -> TapZone.MENU
                        }
                        onZoneTap(zone)
                    },
                )
            },
    ) {
        AsyncImage(
            model = MangaPage(pageModel, index),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = origin),
        )
    }
}

@Composable
private fun GestureHelpOverlay(isRtl: Boolean, onDismiss: () -> Unit) {
    val forwardSide = if (isRtl) "left" else "right"
    val backSide = if (isRtl) "right" else "left"
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Tap $forwardSide to go forward, $backSide to go back, center for menu.\n" +
                "Double-tap to zoom. Volume keys turn pages.\n\nTap anywhere to start reading.",
            color = Color.White,
            modifier = Modifier.padding(32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
