package com.example.xtrtv.data.db

import androidx.room.*

@Dao
interface AppDao {
    @Query("SELECT * FROM categories WHERE type = :type")
    suspend fun getCategoriesByType(type: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM streams WHERE categoryId = :categoryId ORDER BY num ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): StreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<StreamEntity>)

    @Query("SELECT * FROM vod_movies WHERE categoryId = :categoryId ORDER BY streamId DESC")
    suspend fun getVodByCategory(categoryId: String): List<VodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVod(movies: List<VodEntity>)

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY seriesId DESC")
    suspend fun getSeriesByCategory(categoryId: String): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: List<SeriesEntity>)

    @Query("SELECT * FROM epg_data WHERE channelId = :channelId AND stop > :currentTime ORDER BY start ASC")
    suspend fun getEpgForChannel(channelId: String, currentTime: Long): List<EpgEntity>

    @Query("SELECT * FROM epg_data WHERE channelId IN (:channelIds) AND stop > :currentTime ORDER BY start ASC")
    suspend fun getUpcomingEpgForChannels(channelIds: List<String>, currentTime: Long): List<EpgEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgData(epgList: List<EpgEntity>)

    @Query("DELETE FROM epg_data WHERE stop < :currentTime")
    suspend fun deleteOldEpg(currentTime: Long)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM streams")
    suspend fun clearStreams()

    @Query("DELETE FROM vod_movies")
    suspend fun clearVod()

    @Query("DELETE FROM series")
    suspend fun clearSeries()

    @Query("SELECT COUNT(*) FROM epg_data WHERE stop > :currentTime")
    suspend fun getValidEpgCount(currentTime: Long): Int

    @Query("SELECT COUNT(*) FROM vod_movies")
    suspend fun getVodCount(): Int

    @Query("SELECT COUNT(*) FROM series")
    suspend fun getSeriesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<ChannelMappingEntity>)

    @Query("SELECT * FROM channel_mappings")
    suspend fun getAllMappings(): List<ChannelMappingEntity>

    @Query("SELECT MAX(lastUpdated) FROM epg_data")
    suspend fun getLastUpdatedTime(): Long?

    // Search
    @Query("SELECT * FROM vod_movies WHERE name LIKE :query ORDER BY streamId DESC")
    suspend fun searchVod(query: String): List<VodEntity>

    @Query("SELECT * FROM series WHERE name LIKE :query ORDER BY seriesId DESC")
    suspend fun searchSeries(query: String): List<SeriesEntity>

    // Playback History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE type = :type ORDER BY lastWatched DESC")
    suspend fun getHistoryByType(type: String): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playback_history WHERE streamId = :streamId")
    suspend fun getHistoryById(streamId: Int): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history WHERE seriesId = :seriesId ORDER BY lastWatched DESC LIMIT 1")
    suspend fun getLastHistoryBySeriesId(seriesId: Int): PlaybackHistoryEntity?

    @Query("DELETE FROM playback_history WHERE streamId = :streamId")
    suspend fun deleteHistory(streamId: Int)

    // Search History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY lastUsed DESC LIMIT 10")
    suspend fun getRecentSearches(): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)
}
