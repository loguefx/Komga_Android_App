package com.komga.android.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komga.android.domain.model.Collection
import com.komga.android.domain.model.ReadList
import com.komga.android.ui.collections.CollectionsViewModel
import com.komga.android.ui.components.ErrorMessage
import com.komga.android.ui.components.LoadingIndicator
import com.komga.android.ui.components.SeriesCard
import com.komga.android.ui.readlists.ReadListsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onSeriesClick: (String) -> Unit,
    onCollectionClick: (String, String) -> Unit,
    onReadListClick: (String, String) -> Unit,
    seriesViewModel: LibraryViewModel = hiltViewModel(),
    collectionsViewModel: CollectionsViewModel = hiltViewModel(),
    readListsViewModel: ReadListsViewModel = hiltViewModel()
) {
    val seriesState by seriesViewModel.uiState.collectAsStateWithLifecycle()
    val collectionsState by collectionsViewModel.uiState.collectAsStateWithLifecycle()
    val readListsState by readListsViewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Series", "Collections", "Reading Lists")

    Column(modifier = Modifier.fillMaxSize()) {
        // ── TOP BAR ───────────────────────────────────────────────────
        TopAppBar(
            title = { Text("Browse", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (selectedTab == 0) {
                    Box {
                        IconButton(onClick = seriesViewModel::toggleSortMenu) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = seriesState.showSortMenu,
                            onDismissRequest = seriesViewModel::toggleSortMenu
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            color = if (seriesState.sortOption == option)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = { seriesViewModel.onSortSelected(option) }
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // ── TABS ──────────────────────────────────────────────────────
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        // ── TAB CONTENT ───────────────────────────────────────────────
        when (selectedTab) {
            0 -> SeriesTab(
                state = seriesState,
                viewModel = seriesViewModel,
                onSeriesClick = onSeriesClick
            )
            1 -> CollectionsTab(
                state = collectionsState,
                onRefresh = collectionsViewModel::loadCollections,
                onCollectionClick = onCollectionClick
            )
            2 -> ReadListsTab(
                state = readListsState,
                onRefresh = readListsViewModel::loadReadLists,
                onReadListClick = onReadListClick
            )
        }
    }
}

// ── SERIES TAB ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesTab(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onSeriesClick: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            lastVisible >= total - 6 && state.hasMore && !state.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 16.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search series…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (state.isLoading && state.seriesList.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingIndicator() }
            } else if (state.errorMessage != null && state.seriesList.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorMessage(message = state.errorMessage ?: "Something went wrong")
                }
            } else {
                val filtered = if (state.searchQuery.isBlank()) state.seriesList
                else state.seriesList.filter { (s, _) ->
                    s.title.contains(state.searchQuery, ignoreCase = true)
                }

                items(filtered, key = { it.first.id }) { (series, url) ->
                    val isFavorite = state.favoriteIds.contains(series.id)

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd ||
                                value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.toggleFavorite(series)
                            }
                            false // Always snap back — we don't dismiss, we just toggle
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val targetColor = if (isFavorite)
                                Color(0xFFD32F2F).copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            val bgColor by animateColorAsState(targetColor, label = "swipe-bg")
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(bgColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd)
                                    Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Outlined.StarBorder else Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    ) {
                        SeriesCard(
                            series = series,
                            thumbnailUrl = url,
                            onClick = { onSeriesClick(series.id) }
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── COLLECTIONS TAB ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionsTab(
    state: com.komga.android.ui.collections.CollectionsUiState,
    onRefresh: () -> Unit,
    onCollectionClick: (String, String) -> Unit
) {
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.collections.isEmpty() -> LoadingIndicator()
            state.errorMessage != null && state.collections.isEmpty() ->
                ErrorMessage(message = state.errorMessage ?: "Something went wrong")
            state.collections.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No collections found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.collections, key = { it.id }) { collection ->
                    CollectionCard(collection = collection, onClick = { onCollectionClick(collection.id, collection.name) })
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: Collection, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (collection.seriesCount > 0) {
                    Text(
                        "${collection.seriesCount} series",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ── READING LISTS TAB ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadListsTab(
    state: com.komga.android.ui.readlists.ReadListsUiState,
    onRefresh: () -> Unit,
    onReadListClick: (String, String) -> Unit
) {
    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.readLists.isEmpty() -> LoadingIndicator()
            state.errorMessage != null && state.readLists.isEmpty() ->
                ErrorMessage(message = state.errorMessage ?: "Something went wrong")
            state.readLists.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No reading lists found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.readLists, key = { it.id }) { readList ->
                    ReadListCard(readList = readList, onClick = { onReadListClick(readList.id, readList.name) })
                }
            }
        }
    }
}

@Composable
private fun ReadListCard(readList: ReadList, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(readList.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (readList.bookCount > 0) {
                    Text(
                        "${readList.bookCount} chapters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
