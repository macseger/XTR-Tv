package com.example.xtrtv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "live" // "live", "vod", "series"
)

@Entity(tableName = "vod_movies")
data class VodEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String,
    val rating: String?,
    val containerExtension: String?,
    val added: String? = null
)

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val rating: String?,
    val plot: String?,
    val lastModified: String? = null
)

@Entity(tableName = "streams")
data class StreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String,
    val num: Int?,
    val epgChannelId: String? = null
)

@Entity(tableName = "epg_data", primaryKeys = ["channelId", "start"])
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
    val categoryId: String,
    val type: String, // "vod" or "series"
    val position: Long,
    val duration: Long,
    val seriesId: Int? = null,
    val lastWatched: Long = System.currentTimeMillis()
)

data class EpgProgram(
    val title: String,
    val start: Long,
    val stop: Long,
    val description: String?,
    val channelId: String
)
