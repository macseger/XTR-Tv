package com.example.xtrtv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [androidx.room.Index("type")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "live" // "live", "vod", "series"
)

@Entity(tableName = "vod_movies", indices = [androidx.room.Index("categoryId")])
data class VodEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val rating: String?,
    val containerExtension: String?,
    val added: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null
)

@Entity(tableName = "series", indices = [androidx.room.Index("categoryId")])
data class SeriesEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String?,
    val rating: String?,
    val plot: String?,
    val genre: String? = null,
    val releaseDate: String? = null,
    val lastModified: String? = null
)

@Entity(tableName = "streams", indices = [androidx.room.Index("categoryId")])
data class StreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val num: Int?,
    val epgChannelId: String? = null
)

@Entity(tableName = "epg_data", 
    primaryKeys = ["channelId", "start"],
    indices = [androidx.room.Index("channelId"), androidx.room.Index("stop")]
)
data class EpgEntity(
    val channelId: String,
    val title: String,
    val start: Long,
    val stop: Long,
    val description: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "channel_mappings")
data class ChannelMappingEntity(
    @PrimaryKey val displayName: String,
    val channelId: String
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val type: String, // "vod" or "series"
    val position: Long,
    val duration: Long,
    val containerExtension: String? = null,
    val seriesId: Int? = null,
    val lastWatched: Long = System.currentTimeMillis(),
    val plot: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsed: Long = System.currentTimeMillis()
)

data class EpgProgram(
    val title: String,
    val start: Long,
    val stop: Long,
    val description: String?,
    val channelId: String
)
