package com.komga.android.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komga.android.ui.components.ErrorMessage
import com.komga.android.ui.components.LoadingIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Keep display awake while reading
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        uiState.errorMessage != null -> {
            ErrorMessage(message = uiState.errorMessage ?: "Error loading book")
        }

        uiState.pageUrls.isNotEmpty() -> {
            val scope = rememberCoroutineScope()
            val pageCount = uiState.pageUrls.size
            val pagerState = rememberPagerState(
                initialPage = uiState.currentPage,
                pageCount = { pageCount }
            )

            // Sync pager position when chapter changes (in-place navigation)
            LaunchedEffect(uiState.currentPage, uiState.book?.id) {
                if (pagerState.currentPage != uiState.currentPage) {
                    pagerState.scrollToPage(uiState.currentPage)
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    viewModel.onPageChanged(page)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Main pager - reverseLayout for RTL manga mode
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = uiState.isRtl,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    ZoomablePage(
                        imageUrl = uiState.pageUrls[pageIndex],
                        contentDescription = "Page ${pageIndex + 1}"
                    )
                }

                // LEFT tap zone
                // In LTR: left = previous page
                // In RTL: left = next page (reading direction)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f)
                        .align(Alignment.CenterStart)
                        .pointerInput(pagerState, uiState.isRtl) {
                            detectTapGestures {
                                val target = if (uiState.isRtl) {
                                    pagerState.currentPage + 1
                                } else {
                                    pagerState.currentPage - 1
                                }
                                if (target in 0 until pageCount) {
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                            }
                        }
                )

                // RIGHT tap zone
                // In LTR: right = next page
                // In RTL: right = previous page
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f)
                        .align(Alignment.CenterEnd)
                        .pointerInput(pagerState, uiState.isRtl) {
                            detectTapGestures {
                                val target = if (uiState.isRtl) {
                                    pagerState.currentPage - 1
                                } else {
                                    pagerState.currentPage + 1
                                }
                                if (target in 0 until pageCount) {
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                            }
                        }
                )

                // CENTER tap zone – toggle controls
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.3f)
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectTapGestures { viewModel.toggleControls() }
                        }
                )

                // ── TOP BAR (shown/hidden) ─────────────────────────────────
                AnimatedVisibility(
                    visible = uiState.showControls,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.book?.name ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                Text(
                                    text = "${uiState.currentPage + 1} / $pageCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            // Previous chapter (if available)
                            if (uiState.prevBookId != null) {
                                IconButton(onClick = { viewModel.goToPrevChapter() }) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = "Previous chapter",
                                        tint = Color.White
                                    )
                                }
                            }
                            // Next chapter (if available)
                            if (uiState.nextBookId != null) {
                                IconButton(onClick = { viewModel.goToNextChapter() }) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = "Next chapter",
                                        tint = Color.White
                                    )
                                }
                            }
                            // RTL / LTR reading direction toggle
                            IconButton(onClick = { viewModel.toggleRtl() }) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = if (uiState.isRtl) "Switch to LTR" else "Switch to RTL",
                                    tint = if (uiState.isRtl) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.75f)
                        )
                    )
                }

                // ── BOTTOM BAR (always visible) ────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    // Chapter complete overlay
                    AnimatedVisibility(
                        visible = uiState.isChapterComplete,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        ChapterCompleteBar(
                            hasNext = uiState.nextBookId != null,
                            hasPrev = uiState.prevBookId != null,
                            onNext = { viewModel.goToNextChapter() },
                            onPrev = { viewModel.goToPrevChapter() },
                            onBack = onBack
                        )
                    }

                    // Progress bar + page counter
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (pageCount > 0) (uiState.currentPage + 1f) / pageCount else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.currentPage + 1} of $pageCount pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            if (uiState.isRtl) {
                                Text(
                                    text = "RTL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterCompleteBar(
    hasNext: Boolean,
    hasPrev: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Chapter complete!",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasPrev) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev chapter", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }

            if (hasNext) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Next chapter", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Back to series",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    imageUrl: String,
    contentDescription: String
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        if (scale == 1f) offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
