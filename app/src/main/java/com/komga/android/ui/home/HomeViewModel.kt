package com.komga.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komga.android.data.repository.KomgaRepository
import com.komga.android.data.repository.Result
import com.komga.android.domain.model.Book
import com.komga.android.domain.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    // Books actively being read (IN_PROGRESS) – mirrors Komga "Keep Reading"
    val keepReadingBooks: List<Pair<Book, String>> = emptyList(),
    // Next unread book in each series you've started – mirrors Komga "On Deck"
    val onDeckBooks: List<Pair<Book, String>> = emptyList(),
    // Newest series added to Komga
    val newSeries: List<Pair<Series, String>> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: KomgaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Fetch all three sources in parallel
            val inProgressDeferred = async { repository.getBooksInProgress(size = 20) }
            val onDeckDeferred = async { repository.getBooksOnDeck(size = 20) }
            val newSeriesDeferred = async { repository.getLatestSeries(size = 20) }

            val inProgressResult = inProgressDeferred.await()
            val onDeckResult = onDeckDeferred.await()
            val newSeriesResult = newSeriesDeferred.await()

            // Keep Reading: books currently in progress
            val keepReading = when (inProgressResult) {
                is Result.Success -> inProgressResult.data.map { book ->
                    Pair(book, repository.buildBookThumbnailUrl(book.id))
                }
                is Result.Error -> emptyList()
            }

            // On Deck: next unread books, excluding anything already in Keep Reading
            val keepReadingIds = keepReading.map { it.first.id }.toSet()
            val onDeck = when (onDeckResult) {
                is Result.Success -> onDeckResult.data
                    .filter { it.id !in keepReadingIds }
                    .map { book -> Pair(book, repository.buildBookThumbnailUrl(book.id)) }
                is Result.Error -> emptyList()
            }

            val newSeries = when (newSeriesResult) {
                is Result.Success -> newSeriesResult.data.map { series ->
                    Pair(series, repository.buildThumbnailUrl(series.id))
                }
                is Result.Error -> emptyList()
            }

            val error = when {
                inProgressResult is Result.Error &&
                onDeckResult is Result.Error &&
                newSeriesResult is Result.Error ->
                    "Failed to load content. Check your connection."
                else -> null
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    keepReadingBooks = keepReading,
                    onDeckBooks = onDeck,
                    newSeries = newSeries,
                    errorMessage = error
                )
            }
        }
    }
}
