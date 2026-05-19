package com.komga.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komga.android.ui.components.BookCard
import com.komga.android.ui.components.ErrorMessage
import com.komga.android.ui.components.LoadingIndicator
import com.komga.android.ui.components.SectionHeader
import com.komga.android.ui.components.SeriesCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSeriesClick: (String) -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh whenever the screen is resumed (returns from reader, series detail, etc.)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadHome()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.loadHome() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                TopAppBar(
                    title = {
                        Text(
                            text = "Komga",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            val hasAnything = uiState.keepReadingBooks.isNotEmpty() ||
                              uiState.onDeckBooks.isNotEmpty() ||
                              uiState.newSeries.isNotEmpty()

            if (uiState.isLoading && !hasAnything) {
                item { LoadingIndicator() }
                return@LazyColumn
            }

            if (uiState.errorMessage != null && !hasAnything) {
                item { ErrorMessage(message = uiState.errorMessage ?: "Something went wrong") }
                return@LazyColumn
            }

            // ── KEEP READING ─────────────────────────────────────────────
            // Books currently in progress (same as Komga's "Keep Reading")
            if (uiState.keepReadingBooks.isNotEmpty()) {
                item { SectionHeader(title = "Keep Reading") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.keepReadingBooks, key = { it.first.id }) { (book, url) ->
                            BookCard(
                                title = if (book.seriesTitle.isNotBlank())
                                    "${book.seriesTitle} #${book.number.let { n ->
                                        if (n == n.toLong().toFloat()) n.toLong().toString() else n.toString()
                                    }}"
                                else book.name,
                                thumbnailUrl = url,
                                currentPage = book.currentPage,
                                totalPages = book.pagesCount,
                                onClick = { onBookClick(book.id) },
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── ON DECK ───────────────────────────────────────────────────
            // Next unread book in each series the user has started
            if (uiState.onDeckBooks.isNotEmpty()) {
                item { SectionHeader(title = "On Deck") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.onDeckBooks, key = { it.first.id }) { (book, url) ->
                            BookCard(
                                title = if (book.seriesTitle.isNotBlank())
                                    "${book.seriesTitle} #${book.number.let { n ->
                                        if (n == n.toLong().toFloat()) n.toLong().toString() else n.toString()
                                    }}"
                                else book.name,
                                thumbnailUrl = url,
                                currentPage = book.currentPage,
                                totalPages = book.pagesCount,
                                onClick = { onBookClick(book.id) },
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── RECENTLY ADDED ────────────────────────────────────────────
            if (uiState.newSeries.isNotEmpty()) {
                item { SectionHeader(title = "Recently Added") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.newSeries, key = { it.first.id }) { (series, url) ->
                            SeriesCard(
                                series = series,
                                thumbnailUrl = url,
                                onClick = { onSeriesClick(series.id) },
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (!hasAnything && !uiState.isLoading) {
                item {
                    ErrorMessage(message = "No content yet.\nStart reading to see your progress here!")
                }
            }
        }
    }
}
