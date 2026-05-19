package com.komga.android.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komga.android.data.repository.KomgaRepository
import com.komga.android.data.repository.Result
import com.komga.android.domain.model.Book
import com.komga.android.domain.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailUiState(
    val isLoading: Boolean = false,
    val series: Series? = null,
    val books: List<Pair<Book, String>> = emptyList(),
    val thumbnailUrl: String = "",
    val isFavorite: Boolean = false,
    val errorMessage: String? = null,
    val isMarkingRead: Boolean = false
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KomgaRepository
) : ViewModel() {

    private val seriesId: String = savedStateHandle.get<String>("seriesId") ?: ""

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        loadSeriesDetail()
        observeFavoriteStatus()
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            repository.isFavorite(seriesId).collect { isFavorite ->
                _uiState.update { it.copy(isFavorite = isFavorite) }
            }
        }
    }

    fun loadSeriesDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val seriesResult = repository.getSeriesById(seriesId)
            val booksResult = repository.getBooksBySeries(seriesId)
            val thumbnailUrl = repository.buildThumbnailUrl(seriesId)

            when (seriesResult) {
                is Result.Success -> {
                    val books = when (booksResult) {
                        is Result.Success -> booksResult.data.map { book ->
                            Pair(book, repository.buildBookThumbnailUrl(book.id))
                        }
                        is Result.Error -> emptyList()
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            series = seriesResult.data,
                            books = books,
                            thumbnailUrl = thumbnailUrl
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = seriesResult.message
                        )
                    }
                }
            }
        }
    }

    fun toggleFavorite() {
        val series = _uiState.value.series ?: return
        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                repository.removeFavorite(series.id)
            } else {
                repository.addFavorite(series)
            }
        }
    }

    fun markSeriesAsRead() {
        val seriesId = _uiState.value.series?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isMarkingRead = true) }
            repository.markSeriesRead(seriesId)
            // Reload to reflect updated read counts
            loadSeriesDetail()
        }
    }
}
