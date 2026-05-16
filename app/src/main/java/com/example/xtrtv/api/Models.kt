package com.example.xtrtv.api

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String,
    val status: String,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("auth") val auth: Int
)

data class ServerInfo(
    val url: String?,
    val port: String?,
    @SerializedName("server_protocol") val protocol: String?
)

data class Category(
    @SerializedName("category_id") val id: String,
    @SerializedName("category_name") val name: String,
    val type: String = "live" // default for safety
)

data class LiveStream(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("num") val num: Int?,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null
)

data class VodMovie(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null
)

data class Series(
    @SerializedName("series_id") val seriesId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("category_id") val categoryId: String?,
    val lastModified: String? = null
)

data class SeriesDetailsResponse(
    val info: SeriesInfo?,
    val episodes: Map<String, List<Episode>>?
)

data class VodDetailsResponse(
    val info: VodInfo?
)

data class VodInfo(
    val name: String?,
    @SerializedName("movie_image") val movieImage: String?,
    val genre: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val rating: String?
)

data class SeriesInfo(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?
)

data class Episode(
    val id: String?,
    @SerializedName("episode_num") val episodeNum: String?,
    val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: EpisodeInfo?
)

data class EpisodeInfo(
    val duration: String?,
    @SerializedName("movie_image") val movieImage: String?,
    val plot: String?
)
