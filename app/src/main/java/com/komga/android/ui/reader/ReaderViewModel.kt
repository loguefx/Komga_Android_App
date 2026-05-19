package com.komga.android.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komga.android.data.repository.KomgaRepository
import com.komga.android.data.repository.Result
import com.komga.android.domain.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val isLoading: Boolean = false,
    val book: Book? = null,
    val pageUrls: List<String> = emptyList(),
    val currentPage: Int = 0,
    val errorMessage: String? = null,
    val showControls: Boolean = true,
    // RTL: right-to-left pager direction (common manga preference)
    val isRtl: Boolean = false,
    // Adjacent chapter navigation
    val prevBookId: String? = null,
    val nextBookId: String? = null,
    // Shown when the user reaches the last page
    val isChapterComplete: Boolean = false
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KomgaRepository
) : ViewModel() {

    // Mutable so we can navigate to next/prev chapter in-place
    private var currentBookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var progressSaveJob: Job? = null

    init {
        loadBook()
    }

    fun loadBook() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    pageUrls = emptyList(),
                    isChapterComplete = false,
                    prevBookId = null,
                    nextBookId = null
                )
            }

            when (val result = repository.getBookById(currentBookId)) {
                is Result.Success -> {
                    val book = result.data
                    // Resume from last saved page, or start from beginning if completed
                    val startPage = if (book.completed) 0 else maxOf(0, book.currentPage - 1)
                    val pageUrls = (1..book.pagesCount).map { page ->
                        repository.buildPageUrl(currentBookId, page)
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            book = book,
                            pageUrls = pageUrls,
                            currentPage = startPage
                        )
                    }

                    // Load sibling chapters so user can navigate without going back
                    loadAdjacentChapters(book.seriesId)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    private fun loadAdjacentChapters(seriesId: String) {
        viewModelScope.launch {
            val result = repository.getBooksBySeries(seriesId, size = 500)
            if (result is Result.Success) {
                val sorted = result.data.sortedBy { it.number }
                val idx = sorted.indexOfFirst { it.id == currentBookId }
                if (idx >= 0) {
                    _uiState.update {
                        it.copy(
                            prevBookId = if (idx > 0) sorted[idx - 1].id else null,
                            nextBookId = if (idx < sorted.size - 1) sorted[idx + 1].id else null
                        )
                    }
                }
            }
        }
    }

    fun onPageChanged(page: Int) {
        val totalPages = _uiState.value.book?.pagesCount ?: return
        val isLast = page >= totalPages - 1

        _uiState.update { it.copy(currentPage = page, isChapterComplete = isLast) }

        // Debounce saves: cancel pending save, wait 700ms, then persist
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(700)
            saveProgress(page, isLast)
        }
    }

    private suspend fun saveProgress(page: Int, completed: Boolean) {
        repository.updateReadProgress(currentBookId, page + 1, completed)
    }

    fun goToNextChapter() {
        val nextId = _uiState.value.nextBookId ?: return
        flushProgress()
        currentBookId = nextId
        loadBook()
    }

    fun goToPrevChapter() {
        val prevId = _uiState.value.prevBookId ?: return
        flushProgress()
        currentBookId = prevId
        loadBook()
    }

    fun toggleRtl() {
        _uiState.update { it.copy(isRtl = !it.isRtl) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    /** Immediately flush any pending progress save (called on back/chapter switch). */
    private fun flushProgress() {
        progressSaveJob?.cancel()
        val state = _uiState.value
        val page = state.currentPage
        val totalPages = state.book?.pagesCount ?: return
        viewModelScope.launch {
            saveProgress(page, page >= totalPages - 1)
        }
    }

    override fun onCleared() {
        super.onCleared()
        flushProgress()
    }
}
