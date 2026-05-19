package com.komga.android.data.remote

import com.komga.android.data.remote.dto.BookDto
import com.komga.android.data.remote.dto.LibraryDto
import com.komga.android.data.remote.dto.PagedBookDto
import com.komga.android.data.remote.dto.PagedSeriesDto
import com.komga.android.data.remote.dto.ReadProgressUpdateDto
import com.komga.android.data.remote.dto.SeriesDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface KomgaApiService {

    // Auth check
    @GET("api/v1/libraries")
    suspend fun getLibraries(): Response<List<LibraryDto>>

    // Series
    @GET("api/v1/series")
    suspend fun getSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "metadata.titleSort,asc",
        @Query("search") search: String? = null
    ): Response<PagedSeriesDto>

    @GET("api/v1/series")
    suspend fun searchSeries(
        @Query("search") query: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PagedSeriesDto>

    @GET("api/v1/series/latest")
    suspend fun getLatestSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PagedSeriesDto>

    @GET("api/v1/series/new")
    suspend fun getNewSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PagedSeriesDto>

    @GET("api/v1/series/{seriesId}")
    suspend fun getSeriesById(
        @Path("seriesId") seriesId: String
    ): Response<SeriesDto>

    @GET("api/v1/series/{seriesId}/books")
    suspend fun getBooksBySeries(
        @Path("seriesId") seriesId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
        @Query("sort") sort: String = "metadata.numberSort,asc"
    ): Response<PagedBookDto>

    // Books
    @GET("api/v1/books/ondeck")
    suspend fun getBooksOnDeck(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PagedBookDto>

    @GET("api/v1/books")
    suspend fun searchBooks(
        @Query("search") query: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PagedBookDto>

    // Books currently being read (IN_PROGRESS), sorted by most recently read
    @GET("api/v1/books")
    suspend fun getBooksInProgress(
        @Query("read_status") readStatus: String = "IN_PROGRESS",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String = "readProgress.readDate,desc"
    ): Response<PagedBookDto>

    @GET("api/v1/books/latest")
    suspend fun getLatestBooks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PagedBookDto>

    @GET("api/v1/books/{bookId}")
    suspend fun getBookById(
        @Path("bookId") bookId: String
    ): Response<BookDto>

    // Read progress
    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(
        @Path("bookId") bookId: String,
        @Body progress: ReadProgressUpdateDto
    ): Response<Unit>

    @DELETE("api/v1/books/{bookId}/read-progress")
    suspend fun deleteReadProgress(
        @Path("bookId") bookId: String
    ): Response<Unit>

    // Mark entire series as read via Tachiyomi-compatible endpoint
    @PUT("api/v2/series/{seriesId}/read-progress/tachiyomi")
    suspend fun markSeriesRead(
        @Path("seriesId") seriesId: String
    ): Response<Unit>
}
