package com.komga.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komga.android.data.repository.KomgaRepository
import com.komga.android.data.repository.Result
import com.komga.android.domain.model.Book
import com.komga.android.domain.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val seriesResults: List<Pair<Series, String>> = emptyList(),
    val bookResults: List<Pair<Book, String>> = emptyList(),
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: KomgaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(seriesResults = emptyList(), bookResults = emptyList(), hasSearched = false) }
            return
        }

        // Debounce: wait 400ms after the user stops typing before hitting the API
        searchJob = viewModelScope.launch {
            delay(400)
            performSearch(query.trim())
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }

            // Run series and book searches in parallel
            val seriesJob = viewModelScope.launch {
                when (val r = repository.searchSeries(query, size = 30)) {
                    is Result.Success -> {
                        val withUrls = r.data.map { s ->
                            Pair(s, repository.buildThumbnailUrl(s.id))
                        }
                        _uiState.update { it.copy(seriesResults = withUrls) }
                    }
                    is Result.Error -> _uiState.update { it.copy(seriesResults = emptyList()) }
                }
            }

            val bookJob = viewModelScope.launch {
                when (val r = repository.searchBooks(query, size = 20)) {
                    is Result.Success -> {
                        val withUrls = r.data.map { b ->
                            Pair(b, repository.buildBookThumbnailUrl(b.id))
                        }
                        _uiState.update { it.copy(bookResults = withUrls) }
                    }
                    is Result.Error -> _uiState.update { it.copy(bookResults = emptyList()) }
                }
            }

            seriesJob.join()
            bookJob.join()

            _uiState.update { it.copy(isSearching = false, hasSearched = true) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { SearchUiState() }
    }
}
