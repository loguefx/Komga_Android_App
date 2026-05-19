package com.komga.android.data.repository

import com.komga.android.data.local.FavoriteDao
import com.komga.android.data.local.FavoriteEntity
import com.komga.android.data.local.PreferencesDataStore
import com.komga.android.data.remote.KomgaApiService
import com.komga.android.data.remote.dto.LibraryDto
import com.komga.android.data.remote.dto.ReadProgressUpdateDto
import com.komga.android.data.remote.dto.SeriesDto
import com.komga.android.data.remote.dto.BookDto
import com.komga.android.domain.model.Book
import com.komga.android.domain.model.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
}

@Singleton
class KomgaRepository @Inject constructor(
    private val api: KomgaApiService,
    private val favoriteDao: FavoriteDao,
    private val preferencesDataStore: PreferencesDataStore
) {

    // Auth - uses a temporary one-shot client to validate before saving creds
    suspend fun validateConnection(serverUrl: String, email: String, password: String): Result<List<LibraryDto>> {
        return try {
            val testClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", Credentials.basic(email, password))
                            .build()
                    )
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val testApi = Retrofit.Builder()
                .baseUrl(serverUrl.trimEnd('/') + "/")
                .client(testClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(KomgaApiService::class.java)

            val response = testApi.getLibraries()
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error(
                    when (response.code()) {
                        401 -> "Invalid email or password"
                        403 -> "Access denied"
                        404 -> "Server not found at this URL"
                        else -> "Server error: ${response.code()}"
                    },
                    response.code()
                )
            }
        } catch (e: java.net.ConnectException) {
            Result.Error("Cannot connect to server. Check the URL and ensure the server is running.")
        } catch (e: java.net.SocketTimeoutException) {
            Result.Error("Connection timed out. Check the server URL.")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun saveLoginInfo(serverUrl: String, email: String, password: String) {
        preferencesDataStore.saveLoginInfo(serverUrl, email, password)
    }

    suspend fun logout() {
        preferencesDataStore.clearLoginInfo()
    }

    fun isLoggedIn(): Flow<Boolean> = preferencesDataStore.isLoggedIn()

    fun getServerUrl(): Flow<String> = preferencesDataStore.getServerUrl()

    // Series
    suspend fun getSeries(page: Int = 0, size: Int = 20): Result<Pair<List<Series>, Boolean>> {
        return try {
            val response = api.getSeries(page, size)
            if (response.isSuccessful) {
                val paged = response.body()!!
                Result.Success(Pair(paged.content.map { it.toDomain() }, !paged.last))
            } else {
                Result.Error("Failed to load series: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load series")
        }
    }

    suspend fun getLatestSeries(page: Int = 0, size: Int = 20): Result<List<Series>> {
        return try {
            val response = api.getLatestSeries(page, size)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.content.map { it.toDomain() })
            } else {
                Result.Error("Failed to load latest series: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load latest series")
        }
    }

    suspend fun getNewSeries(page: Int = 0, size: Int = 20): Result<List<Series>> {
        return try {
            val response = api.getNewSeries(page, size)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.content.map { it.toDomain() })
            } else {
                Result.Error("Failed to load new series", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load new series")
        }
    }

    suspend fun getSeriesById(seriesId: String): Result<Series> {
        return try {
            val response = api.getSeriesById(seriesId)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.toDomain())
            } else {
                Result.Error("Failed to load series: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // Books
    suspend fun getBooksBySeries(seriesId: String, page: Int = 0, size: Int = 50): Result<List<Book>> {
        return try {
            val response = api.getBooksBySeries(seriesId, page, size)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.content.map { it.toDomain() })
            } else {
                Result.Error("Failed to load books: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load books")
        }
    }

    suspend fun getBooksInProgress(page: Int = 0, size: Int = 20): Result<List<Book>> {
        return try {
            val response = api.getBooksInProgress(page = page, size = size)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.content.map { it.toDomain() })
            } else {
                Result.Error("Failed to load in-progress books: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load in-progress books")
        }
    }

    suspend fun getBooksOnDeck(page: Int = 0, size: Int = 20): Result<List<Book>> {
        return try {
            val response = api.getBooksOnDeck(page, size)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.content.map { it.toDomain() })
            } else {
                Result.Error("Failed to load on-deck books: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load on-deck books")
        }
    }

    suspend fun getLatestBooks(page: Int = 0, size: Int = 20): Result<List<Book>> {
        return try {
            val response = api.getLatestBooks(page, size)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.content.map { it.toDomain() })
            } else {
                Result.Error("Failed to load latest books: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load latest books")
        }
    }

    suspend fun getBookById(bookId: String): Result<Book> {
        return try {
            val response = api.getBookById(bookId)
            if (response.isSuccessful) {
                Result.Success(response.body()!!.toDomain())
            } else {
                Result.Error("Failed to load book: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun markSeriesRead(seriesId: String): Result<Unit> {
        return try {
            val response = api.markSeriesRead(seriesId)
            if (response.isSuccessful) Result.Success(Unit)
            else Result.Error("Failed to mark as read: ${response.code()}", response.code())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateReadProgress(bookId: String, page: Int, completed: Boolean): Result<Unit> {
        return try {
            val response = api.updateReadProgress(bookId, ReadProgressUpdateDto(page, completed))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Failed to update progress: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    // Favorites
    fun getFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    fun isFavorite(seriesId: String): Flow<Boolean> = favoriteDao.isFavorite(seriesId)

    suspend fun addFavorite(series: Series) {
        favoriteDao.insertFavorite(
            FavoriteEntity(
                seriesId = series.id,
                title = series.title,
                libraryId = series.libraryId,
                booksCount = series.booksCount,
                booksReadCount = series.booksReadCount
            )
        )
    }

    suspend fun removeFavorite(seriesId: String) {
        favoriteDao.deleteFavoriteById(seriesId)
    }

    // URL helpers
    suspend fun buildThumbnailUrl(seriesId: String): String {
        val serverUrl = preferencesDataStore.getServerUrl().first()
        return "${serverUrl.trimEnd('/')}/api/v1/series/$seriesId/thumbnail"
    }

    suspend fun buildBookThumbnailUrl(bookId: String): String {
        val serverUrl = preferencesDataStore.getServerUrl().first()
        return "${serverUrl.trimEnd('/')}/api/v1/books/$bookId/thumbnail"
    }

    suspend fun buildPageUrl(bookId: String, page: Int): String {
        val serverUrl = preferencesDataStore.getServerUrl().first()
        return "${serverUrl.trimEnd('/')}/api/v1/books/$bookId/pages/$page"
    }
}

private fun SeriesDto.toDomain(): Series = Series(
    id = id,
    libraryId = libraryId,
    title = metadata.title.ifBlank { name },
    booksCount = booksCount,
    booksReadCount = booksReadCount,
    booksUnreadCount = booksUnreadCount,
    booksInProgressCount = booksInProgressCount,
    status = metadata.status,
    summary = metadata.summary,
    genres = metadata.genres,
    publisher = metadata.publisher,
    language = metadata.language
)

private fun BookDto.toDomain(): Book = Book(
    id = id,
    seriesId = seriesId,
    seriesTitle = seriesTitle,
    name = metadata.title.ifBlank { name },
    number = number,
    pagesCount = media.pagesCount,
    currentPage = readProgress?.page ?: 0,
    completed = readProgress?.completed ?: false,
    mediaType = media.mediaType,
    size = size
)
