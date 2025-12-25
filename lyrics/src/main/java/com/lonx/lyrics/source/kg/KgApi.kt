package com.lonx.lyrics.source.kg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap

// --- 通用响应 (给 searchSong 用) ---
@Serializable
data class KgBaseResponse<T>(
    @SerialName("status") val status: Int = 0,
    @SerialName("error_code") val errorCode: Int = 0,
    @SerialName("data") val data: T? = null
)

// --- 修复：歌词搜索专用响应 (结构扁平，没有 data 层) ---
@Serializable
data class KgLyricSearchResponse(
    @SerialName("status") val status: Int = 0,
    @SerialName("error_code") val errorCode: Int = 0,
    @SerialName("candidates") val candidates: List<KgCandidate>? = emptyList()
)

// --- 其他模型保持不变 ---
@Serializable
data class RegisterDevData(val dfid: String)

@Serializable
data class KgSearchWrapper(val lists: List<KgSongItem>?, val total: Int)

@Serializable
data class KgSongItem(
    @SerialName("ID") val id: String? = null,
    @SerialName("FileHash") val fileHash: String,
    @SerialName("SongName") val songName: String,
    @SerialName("Singers") val singers: List<KgSinger>,
    @SerialName("AlbumName") val albumName: String? = null,
    @SerialName("Duration") val duration: Int
)

@Serializable
data class KgSinger(val name: String)

@Serializable
data class KgCandidate(
    val id: String,
    val accesskey: String,
    val duration: Int
)

@Serializable
data class KgLyricContent(
    val content: String,
    val fmt: String,
    val contenttype: Int
)

interface KgApi {
    @POST("https://userservice.kugou.com/risk/v1/r_register_dev")
    suspend fun registerDev(
        @QueryMap params: Map<String, String>,
        @Body body: RequestBody
    ): KgBaseResponse<RegisterDevData>

    @GET("http://complexsearch.kugou.com/v2/search/song")
    @Headers("x-router: complexsearch.kugou.com")
    suspend fun searchSong(
        @QueryMap params: Map<String, String>
    ): KgBaseResponse<KgSearchWrapper>

    @GET("https://lyrics.kugou.com/v1/search")
    suspend fun searchLyrics(
        @QueryMap params: Map<String, String>
    ): KgLyricSearchResponse

    @GET("http://lyrics.kugou.com/download")
    suspend fun downloadLyrics(
        @QueryMap params: Map<String, String>
    ): KgLyricContent
}