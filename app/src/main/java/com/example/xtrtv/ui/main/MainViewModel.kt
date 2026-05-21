package com.example.xtrtv.ui.main

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.example.xtrtv.R
import com.example.xtrtv.api.*
import com.example.xtrtv.data.Prefs
import com.example.xtrtv.data.UserData
import com.example.xtrtv.data.db.*
import com.example.xtrtv.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    
    private val PREFIX_REGEX = Regex("^(se|se:|sweden|sweden:)\\s*")
    private val BRACKET_REGEX = Regex("[\\[(].*?[\\])]")
    private val SUFFIX_REGEX = Regex("\\s+(fhd|hd|sd|hevc|4k|se|s)$")
    private val CLEAN_REGEX = Regex("[^a-z0-9åäö]")
    private val CAT_PREFIX_REGEX = Regex("^(Movies|Series|Filmer|Serier)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)

    enum class AppMode { LIVE, VOD, SERIES }
    var currentMode by mutableStateOf(AppMode.LIVE)
        private set
    var activePlaybackMode by mutableStateOf(AppMode.LIVE) // Tracks what is actually playing

    var categories by mutableStateOf<List<Category>>(emptyList())
    var channels by mutableStateOf<List<LiveStream>>(emptyList())
    var vodMovies by mutableStateOf<List<VodMovie>>(emptyList())
    var seriesList by mutableStateOf<List<Series>>(emptyList())
    
    // Series details
    var selectedSeries by mutableStateOf<Series?>(null)
    var seriesDetails by mutableStateOf<SeriesDetailsResponse?>(null)
    var lastWatchedEpisode by mutableStateOf<Episode?>(null)
    var isSeriesLoading by mutableStateOf(false)
    
    // Movie details
    var selectedMovieHistory by mutableStateOf<PlaybackHistoryEntity?>(null)
    
    var epgMap by mutableStateOf<Map<Int, EpgEntity>>(emptyMap())
        private set
    var nextEpgMap by mutableStateOf<Map<Int, EpgEntity>>(emptyMap())
        private set
    var lastEpgUpdate by mutableStateOf<Long?>(null)
        private set

    @Volatile private var channelIdMap = emptyMap<String, String>()
    @Volatile private var normalizedChannelIdMap = emptyMap<String, String>()
    private val epgMappingMutex = Mutex()

    private fun updateChannelMappings(newMap: Map<String, String>) {
        channelIdMap = newMap
        normalizedChannelIdMap = newMap.entries.associate { (displayName, id) ->
            normalizeName(displayName) to id
        }
    }

    fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(BRACKET_REGEX, "") // Ta bort allt inom [] och () (t.ex. [Multi-Sub])
            .replace(PREFIX_REGEX, "") // Ta bort vanliga prefix
            .replace(SUFFIX_REGEX, "") // Ta bort vanliga suffix
            .replace(CLEAN_REGEX, "") // Behåll bara bokstäver och siffror
    }

    private fun cleanCategoryName(name: String): String {
        return name.replace(CAT_PREFIX_REGEX, "").trim()
    }
    var selectedCategory by mutableStateOf<Category?>(null)
    var currentChannel by mutableStateOf<LiveStream?>(null)
    
    var recentChannels by mutableStateOf<List<LiveStream>>(emptyList())
    
    // Resume dialog state
    var pendingMovie by mutableStateOf<VodMovie?>(null)
    var pendingEpisode by mutableStateOf<Episode?>(null)
    var currentSeriesId by mutableStateOf<Int?>(null)
    var savedPosition by mutableLongStateOf(0L)
    var showResumeDialog by mutableStateOf(false)

    var isLoading by mutableStateOf(false)
    var isEpgUpdating by mutableStateOf(false)
    var backgroundStatus by mutableStateOf<String?>(null)
    private var isMappingEpg = false
    var isBackgroundSyncing by mutableStateOf(false)
        private set
    var backgroundSyncMessage by mutableStateOf<String?>(null)
        private set
        
    var isTunnelingEnabled by mutableStateOf(false)
    var isFrameRateMatchingEnabled by mutableStateOf(false)
    var customEpgUrl by mutableStateOf<String?>(null)
    var useInternalSwedishEpg by mutableStateOf(false)
    var subtitleTracks by mutableStateOf<List<TrackInfo>>(emptyList())
    var selectedSubtitleId by mutableStateOf<String?>(null)
    var audioTracks by mutableStateOf<List<TrackInfo>>(emptyList())
    var selectedAudioId by mutableStateOf<String?>(null)
        private set

    var currentVodPlot by mutableStateOf<String?>(null)
    var currentVodGenre by mutableStateOf<String?>(null)
    var currentVodRating by mutableStateOf<String?>(null)
    var currentVodReleaseDate by mutableStateOf<String?>(null)
    private var vodInfoJob: kotlinx.coroutines.Job? = null

    var channelEpgList by mutableStateOf<List<EpgEntity>>(emptyList())
    var isFetchingChannelEpg by mutableStateOf(false)

    // Playback state
    var isPlaying by mutableStateOf(false)
    var playbackPosition by mutableLongStateOf(0L)
    var playbackDuration by mutableLongStateOf(0L)
    private var playbackProgressJob: kotlinx.coroutines.Job? = null

    // Centralized time state for UI components
    var currentTime by mutableLongStateOf(System.currentTimeMillis())
        private set

    // Search state
    var searchQuery by mutableStateOf("")
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var filteredVod by mutableStateOf<List<VodMovie>>(emptyList())
    var filteredSeries by mutableStateOf<List<Series>>(emptyList())

    var updateStatus by mutableStateOf<String?>(null)
    var isCheckingUpdate by mutableStateOf(false)
    var latestRelease by mutableStateOf<GithubRelease?>(null)

    var showNextEpisodeDialog by mutableStateOf(false)
    var nextEpisode: Episode? = null

    var vodCategoryMap by mutableStateOf<Map<String, String>>(emptyMap())
    var seriesCategoryMap by mutableStateOf<Map<String, String>>(emptyMap())

    private var categoryLoadJob: kotlinx.coroutines.Job? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    data class TrackInfo(val id: String, val name: String, val groupIndex: Int, val trackIndex: Int)

    private var player: ExoPlayer? = null
    private var userData: UserData? = null
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.appDao()
    private val prefs = Prefs(application)

    fun initialSync(onChannelsReady: () -> Unit) {
        val data = userData ?: return
        viewModelScope.launch {
            try {
                // 1. Priority: Get minimal UI data immediately from DB or API
                val hasCategories = withContext(Dispatchers.IO) { dao.getCategoriesByType("live").isNotEmpty() }
                
                if (!hasCategories) {
                    // Very first start - get essentials fast
                    isLoading = true
                    backgroundStatus = getApplication<Application>().getString(R.string.loading)
                    
                    val apiService = ApiClient.createService(data.url)
                    val catResp = apiService.getLiveCategories(data.username, data.password)
                    if (catResp.isSuccessful) {
                        val apiCategories = catResp.body() ?: emptyList()
                        val mappedCategories = withContext(Dispatchers.Default) {
                            apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                        }
                        withContext(Dispatchers.IO) {
                            dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                        }
                        categories = mappedCategories
                        
                        if (categories.isNotEmpty()) {
                            val firstCat = categories.first()
                            selectedCategory = firstCat
                            val streamResp = apiService.getLiveStreams(data.username, data.password, categoryId = firstCat.id)
                            if (streamResp.isSuccessful) {
                                val apiStreams = streamResp.body() ?: emptyList()
                                withContext(Dispatchers.IO) {
                                    dao.insertStreams(apiStreams.map { 
                                        StreamEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.num, it.epgChannelId)
                                    })
                                }
                                val sortedStreams = withContext(Dispatchers.Default) {
                                    apiStreams.sortedBy { it.num ?: it.streamId }
                                }
                                channels = sortedStreams
                                if (currentChannel == null && channels.isNotEmpty()) playChannel(channels.first())
                            }
                        }
                    }
                }
                
                // Let the UI show up
                onChannelsReady()
                isLoading = false
                backgroundStatus = null
                
                // 2. Background Sync: Start a "gentle" sync if needed
                if (isSyncNeeded()) {
                    startGentleBackgroundSync()
                } else {
                    // Even if not full sync needed, always do a fast channel sync on start
                    fastSyncChannels()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during initial sync", e)
                onChannelsReady()
            } finally {
                isLoading = false
                backgroundStatus = null
            }
        }
    }

    private fun fastSyncChannels() {
        val data = userData ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.createService(data.url)
                
                // 1. Sync Live Categories
                val catResp = apiService.getLiveCategories(data.username, data.password)
                if (catResp.isSuccessful) {
                    val apiCategories = catResp.body() ?: emptyList()
                    dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                    
                    if (currentMode == AppMode.LIVE) {
                        val updatedCats = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                        withContext(Dispatchers.Main) {
                            categories = updatedCats
                        }
                    }

                    // 2. Sync Live Streams for the currently selected category if in LIVE mode
                    val targetCatId = selectedCategory?.id
                    if (currentMode == AppMode.LIVE && targetCatId != null && targetCatId != "history") {
                        val streamResp = apiService.getLiveStreams(data.username, data.password, categoryId = targetCatId)
                        if (streamResp.isSuccessful) {
                            val apiStreams = streamResp.body() ?: emptyList()
                            dao.insertStreams(apiStreams.map { 
                                StreamEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.num, it.epgChannelId)
                            })
                            val sortedStreams = apiStreams.sortedBy { it.num ?: it.streamId }
                            withContext(Dispatchers.Main) {
                                channels = sortedStreams
                                refreshEpgMap()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fast sync failed", e)
            }
        }
    }

    private fun startGentleBackgroundSync() {
        if (isBackgroundSyncing) return
        
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                isBackgroundSyncing = true
                val data = userData ?: return@launch
                val apiService = ApiClient.createService(data.url)
                
                backgroundSyncMessage = getApplication<Application>().getString(R.string.loading) + "..."

                // 1. Sync Live Categories and Channels first (Fast)
                val liveCatResp = apiService.getLiveCategories(data.username, data.password)
                if (liveCatResp.isSuccessful) {
                    val apiCategories = liveCatResp.body() ?: emptyList()
                    dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                    if (currentMode == AppMode.LIVE) {
                        val updatedCats = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                        withContext(Dispatchers.Main) { categories = updatedCats }
                    }
                }

                // 2. IMPORTANT: Sync EPG BEFORE VOD/Series
                backgroundSyncMessage = getApplication<Application>().getString(R.string.updating_epg)
                fetchEpgFromApi() // Already has batching and internal refreshEpgMap()

                // 3. Sync VOD/Series Categories
                backgroundSyncMessage = "Syncing Categories..."
                val vCatResp = apiService.getVodCategories(data.username, data.password)
                if (vCatResp.isSuccessful) {
                    dao.insertCategories(vCatResp.body()?.map { CategoryEntity(it.id, it.name, "vod") } ?: emptyList())
                }
                
                val sCatResp = apiService.getSeriesCategories(data.username, data.password)
                if (sCatResp.isSuccessful) {
                    dao.insertCategories(sCatResp.body()?.map { CategoryEntity(it.id, it.name, "series") } ?: emptyList())
                }

                // 4. Sync Content in small chunks with delays
                
                // Throttled VOD Sync
                val vodResp = apiService.getVodStreams(data.username, data.password)
                if (vodResp.isSuccessful) {
                    val movies = vodResp.body() ?: emptyList()
                    if (movies.isNotEmpty()) {
                        val chunks = movies.chunked(100)
                        chunks.forEachIndexed { index, chunk ->
                            val progress = ((index + 1) * 100 / chunks.size).coerceAtMost(100)
                            backgroundSyncMessage = "Syncing VOD: $progress%"
                            dao.insertVod(chunk.map { 
                                VodEntity(
                                    it.streamId, it.name, it.streamIcon, it.categoryId ?: "0", 
                                    it.rating, it.containerExtension, it.added,
                                    it.plot, it.cast, it.director, it.genre, it.releaseDate
                                )
                            })
                            kotlinx.coroutines.delay(200)
                        }
                    }
                }

                // Throttled Series Sync
                val seriesResp = apiService.getSeries(data.username, data.password)
                if (seriesResp.isSuccessful) {
                    val apiSeriesList = seriesResp.body() ?: emptyList()
                    if (apiSeriesList.isNotEmpty()) {
                        val chunks = apiSeriesList.chunked(100)
                        chunks.forEachIndexed { index, chunk ->
                            val progress = ((index + 1) * 100 / chunks.size).coerceAtMost(100)
                            backgroundSyncMessage = "Syncing Series: $progress%"
                            dao.insertSeries(chunk.map { 
                                SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId ?: "0", it.rating, it.plot, it.genre, it.releaseDate)
                            })
                            kotlinx.coroutines.delay(200)
                        }
                    }
                }

                prefs.lastFullSync = System.currentTimeMillis()
                refreshCategoryMaps()
                backgroundSyncMessage = "Update Complete!"
                kotlinx.coroutines.delay(3000)
                backgroundSyncMessage = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Sync cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed", e)
            } finally {
                isBackgroundSyncing = false
                backgroundSyncMessage = null
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun init(data: UserData) {
        if (this.userData != null) return
        this.userData = data
        
        val appContext = getApplication<Application>().applicationContext
        isTunnelingEnabled = prefs.isTunnelingEnabled
        isFrameRateMatchingEnabled = prefs.isFrameRateMatchingEnabled
        customEpgUrl = prefs.customEpgUrl
        useInternalSwedishEpg = prefs.useInternalSwedishEpg
        
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min buffer
                60_000, // Max buffer
                5_000,  // Buffer for playback
                10_000   // Buffer for rebuffering
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TiviMate") // Samma som i ApiClient för att undvika blockeringar
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(appContext, extractorsFactory)
                .setDataSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build().apply {
            
            trackSelectionParameters = DefaultTrackSelector.Parameters.Builder(appContext)
                .setTunnelingEnabled(isTunnelingEnabled)
                .build()
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setVideoChangeFrameRateStrategy(
                    if (isFrameRateMatchingEnabled) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                    else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                )
            }

            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MainViewModel.isPlaying = playing
                    if (playing) {
                        startPlaybackProgressLoop()
                    } else {
                        stopPlaybackProgressLoop()
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        playbackDuration = duration.coerceAtLeast(0L)
                    } else if (state == androidx.media3.common.Player.STATE_ENDED) {
                        if (activePlaybackMode == AppMode.SERIES) {
                            handleSeriesEnded()
                        }
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val subTracks = mutableListOf<TrackInfo>()
                    val aTracks = mutableListOf<TrackInfo>()
                    var currentSubId: String? = null
                    var currentAudId: String? = null
                    
                    tracks.groups.forEachIndexed { groupIndex, group ->
                        when (group.type) {
                            C.TRACK_TYPE_TEXT -> {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val isSelected = group.isTrackSelected(i)
                                    val label = format.label ?: format.language ?: getApplication<Application>().getString(R.string.track_label, subTracks.size + 1)
                                    val id = format.id ?: "sub-$groupIndex-$i"
                                    subTracks.add(TrackInfo(id, label, groupIndex, i))
                                    if (isSelected) currentSubId = id
                                }
                            }
                            C.TRACK_TYPE_AUDIO -> {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val isSelected = group.isTrackSelected(i)
                                    val label = format.label ?: format.language ?: getApplication<Application>().getString(R.string.audio_track_label, aTracks.size + 1)
                                    val id = format.id ?: "audio-$groupIndex-$i"
                                    aTracks.add(TrackInfo(id, label, groupIndex, i))
                                    if (isSelected) currentAudId = id
                                }
                            }
                        }
                    }
                    subtitleTracks = subTracks
                    selectedSubtitleId = currentSubId
                    audioTracks = aTracks
                    selectedAudioId = currentAudId
                }
            })
        }
        
        // 1. Load initial data IMMEDIATELY to show UI
        loadInitialData()

        // 2. Load mappings in background without blocking initial data
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val savedMappings = dao.getAllMappings()
                val mappingMap = savedMappings.associate { it.displayName to it.channelId }
                
                // Do the heavy normalization on a Default dispatcher to not block IO or UI
                val normalizedMap = withContext(Dispatchers.Default) {
                    mappingMap.entries.associate { (displayName, id) ->
                        normalizeName(displayName) to id
                    }
                }
                
                withContext(Dispatchers.Main) {
                    channelIdMap = mappingMap
                    normalizedChannelIdMap = normalizedMap
                    lastEpgUpdate = dao.getLastUpdatedTime()
                    // Re-trigger EPG map once mappings are ready
                    refreshEpgMap()
                }
            }
        }
        
        // 3. Centralized time update loop
        viewModelScope.launch {
            while (true) {
                currentTime = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
        
        refreshCategoryMaps()
    }

    fun refreshCategoryMaps() {
        viewModelScope.launch(Dispatchers.IO) {
            val vod = dao.getCategoriesByType("vod").associate { it.id to cleanCategoryName(it.name) }
            val series = dao.getCategoriesByType("series").associate { it.id to cleanCategoryName(it.name) }
            withContext(Dispatchers.Main) {
                vodCategoryMap = vod
                seriesCategoryMap = series
            }
        }
    }

    fun refreshEpg() {
        if (userData == null) return
        viewModelScope.launch {
            isEpgUpdating = true
            val currentTime = System.currentTimeMillis()
            // Don't delete everything, just the old stuff to keep the DB size manageable
            withContext(Dispatchers.IO) {
                dao.deleteOldEpg(currentTime - (24 * 3600 * 1000)) // Keep last 24h
            }
            fetchEpgFromApi()
        }
    }

    fun refreshVodAndSeries() {
        if (userData == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                // We use the background sync logic but force it to run now
                syncJob?.cancel()
                startGentleBackgroundSync()
                
                // Wait for sync to finish or just let it run? 
                // The user expects a "refresh" so let's just make sure categories are updated
                val data = userData!!
                val apiService = ApiClient.createService(data.url)
                
                val vodCatResp = apiService.getVodCategories(data.username, data.password)
                if (vodCatResp.isSuccessful) {
                    val apiCategories = vodCatResp.body() ?: emptyList()
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "vod") })
                    }
                }

                val seriesCatResp = apiService.getSeriesCategories(data.username, data.password)
                if (seriesCatResp.isSuccessful) {
                    val apiCategories = seriesCatResp.body() ?: emptyList()
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "series") })
                    }
                }
                
                loadInitialData()
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing VOD/Series", e)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun syncAllContent(force: Boolean = false) {
        // Future implementation: Full sync of all content for offline search
    }

    fun forceUpdateChannels() {
        if (userData == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                // Now just does the fast sync logic but with UI loading indicator
                val data = userData!!
                val apiService = ApiClient.createService(data.url)
                
                val catResp = apiService.getLiveCategories(data.username, data.password)
                if (catResp.isSuccessful) {
                    val apiCategories = catResp.body() ?: emptyList()
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                    }
                    
                    if (currentMode == AppMode.LIVE) {
                        categories = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                    }

                    val targetCatId = selectedCategory?.id
                    if (currentMode == AppMode.LIVE && targetCatId != null && targetCatId != "history") {
                        val streamResp = apiService.getLiveStreams(data.username, data.password, categoryId = targetCatId)
                        if (streamResp.isSuccessful) {
                            val apiStreams = streamResp.body() ?: emptyList()
                            withContext(Dispatchers.IO) {
                                dao.insertStreams(apiStreams.map { 
                                    StreamEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.num, it.epgChannelId)
                                })
                            }
                            channels = apiStreams.sortedBy { it.num ?: it.streamId }
                            refreshEpgMap()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating channels", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun fetchEpgFromApi() {
        val data = userData ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Determine EPG URL
                val epgUrl = when {
                    useInternalSwedishEpg -> "https://iptv-epg.org/files/epg-se.xml"
                    !customEpgUrl.isNullOrBlank() -> customEpgUrl!!
                    else -> {
                        val baseUrl = data.url.removeSuffix("/")
                        "$baseUrl/xmltv.php?username=${data.username}&password=${data.password}"
                    }
                }
                
                Log.d(TAG, "Fetching EPG from: $epgUrl")
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getXmlEpg(epgUrl)
                
                if (response.isSuccessful) {
                    Log.d(TAG, "EPG Download successful, parsing...")
                    response.body()?.byteStream()?.use { inputStream ->
                        val batch = mutableListOf<EpgEntity>()
                        var totalParsed = 0
                        val result = XmlEpgParser.parse(inputStream) { program ->
                            batch.add(program)
                            totalParsed++
                            if (batch.size >= 2000) { // Larger batch size for better performance
                                val currentBatch = batch.toList()
                                batch.clear()
                                dao.insertEpgData(currentBatch)
                            }
                        }
                        
                        // Insert remaining
                        if (batch.isNotEmpty()) {
                            dao.insertEpgData(batch)
                        }

                        withContext(Dispatchers.Main) {
                            updateChannelMappings(result.channelMap)
                            // Save mappings to DB for next restart
                            val mappingEntities = result.channelMap.map { 
                                ChannelMappingEntity(it.key, it.value) 
                            }
                            withContext(Dispatchers.IO) {
                                mappingEntities.chunked(500).forEach { dao.insertMappings(it) }
                            }
                            lastEpgUpdate = System.currentTimeMillis()
                            refreshEpgMap()
                        }
                    }
                } else {
                    Log.e(TAG, "EPG Download failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching EPG", e)
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isEpgUpdating = false
                }
            }
        }
    }

    private suspend fun refreshEpgMap() = withContext(Dispatchers.Default) {
        if (isMappingEpg) return@withContext
        val currentTimeMs = System.currentTimeMillis()
        val currentChannels = channels
        if (currentChannels.isEmpty()) return@withContext
        
        epgMappingMutex.withLock {
            isMappingEpg = true
            try {
                // 1. Collect all candidate IDs for all channels to fetch in bulk
                val allCandidateIds = mutableSetOf<String>()
                val channelCandidatesMap = mutableMapOf<Int, List<String>>()

                currentChannels.forEach { stream ->
                    val candidates = mutableListOf<String>()
                    // Priority 1: Direct ID from stream
                    if (!stream.epgChannelId.isNullOrBlank() && stream.epgChannelId != "null") {
                        candidates.add(stream.epgChannelId)
                    }
                    // Priority 2: Exact name match
                    channelIdMap[stream.name]?.let { candidates.add(it) }
                    // Priority 3: Normalized name match
                    normalizedChannelIdMap[normalizeName(stream.name)]?.let { candidates.add(it) }
                    
                    val uniqueCandidates = candidates.distinct()
                    channelCandidatesMap[stream.streamId] = uniqueCandidates
                    allCandidateIds.addAll(uniqueCandidates)
                }

                // 2. Fetch all upcoming programs for these IDs in chunks to avoid SQLite parameter limit
                val programs = if (allCandidateIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        allCandidateIds.toList().chunked(500).flatMap { chunk ->
                            dao.getUpcomingEpgForChannels(chunk, currentTimeMs)
                        }
                    }
                } else {
                    emptyList()
                }
                
                // Group by channelId and take Current and Next
                val groupedPrograms = programs.groupBy { it.channelId }
                val currentProgramLookup = mutableMapOf<String, EpgEntity>()
                val nextProgramLookup = mutableMapOf<String, EpgEntity>()

                groupedPrograms.forEach { (channelId, channelPrograms) ->
                    val current = channelPrograms.find { it.start <= currentTimeMs && it.stop >= currentTimeMs }
                    val next = if (current != null) {
                        channelPrograms.find { it.start >= current.stop }
                    } else {
                        channelPrograms.firstOrNull()
                    }
                    
                    if (current != null) currentProgramLookup[channelId] = current
                    if (next != null) nextProgramLookup[channelId] = next
                }

                // 3. Map programs back to channels based on priority
                val newEpgMap = mutableMapOf<Int, EpgEntity>()
                val newNextEpgMap = mutableMapOf<Int, EpgEntity>()
                currentChannels.forEach { stream ->
                    val candidates = channelCandidatesMap[stream.streamId] ?: emptyList()
                    for (id in candidates) {
                        val currentMatch = currentProgramLookup[id]
                        if (currentMatch != null) {
                            newEpgMap[stream.streamId] = currentMatch
                        }
                        val nextMatch = nextProgramLookup[id]
                        if (nextMatch != null) {
                            newNextEpgMap[stream.streamId] = nextMatch
                        }
                        if (currentMatch != null || nextMatch != null) break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    epgMap = newEpgMap
                    nextEpgMap = newNextEpgMap
                    Log.i(TAG, "EPG Map updated: ${newEpgMap.size} current, ${newNextEpgMap.size} next matches found")
                }
            } finally {
                isMappingEpg = false
            }
        }
    }

    fun changeMode(mode: AppMode) {
        val oldMode = currentMode
        
        // If switching to LIVE from a VOD/Series playback, stop player and restart last channel
        if (mode == AppMode.LIVE && activePlaybackMode != AppMode.LIVE) {
            player?.stop()
            player?.clearMediaItems()
            activePlaybackMode = AppMode.LIVE
            playLastChannel()
        }
        
        if (oldMode == mode && categories.isNotEmpty()) return
        currentMode = mode
        loadInitialData()
    }

    fun prepareUiForCurrentPlayback() {
        val targetMode = activePlaybackMode
        currentMode = targetMode
        
        viewModelScope.launch {
            val type = when(targetMode) {
                AppMode.LIVE -> "live"
                AppMode.VOD -> "vod"
                AppMode.SERIES -> "series"
            }
            
            val cachedCategories = withContext(Dispatchers.IO) {
                dao.getCategoriesByType(type).map { Category(it.id, cleanCategoryName(it.name), it.type) }
            }.toMutableList()

            if (targetMode == AppMode.VOD || targetMode == AppMode.SERIES) {
                val hasHistory = withContext(Dispatchers.IO) {
                    dao.getHistoryByType(type).isNotEmpty()
                }
                if (hasHistory) {
                    cachedCategories.add(0, Category("history", getApplication<Application>().getString(R.string.history), type))
                }
            }

            if (cachedCategories.isNotEmpty()) {
                categories = cachedCategories
                
                val targetCatId = when(targetMode) {
                    AppMode.LIVE -> currentChannel?.categoryId
                    AppMode.VOD -> pendingMovie?.categoryId
                    AppMode.SERIES -> if (currentSeriesId != null) selectedSeries?.categoryId else pendingMovie?.categoryId
                }
                
                val cat = categories.find { it.id == targetCatId } ?: categories.first()
                selectCategory(cat, forceRefresh = false)
            }
        }
    }

    fun playLastChannel() {
        val lastId = prefs.lastChannelId
        Log.d(TAG, "playLastChannel called, lastId: $lastId")
        if (lastId == -1) return
        
        // Ensure we are in LIVE mode state
        activePlaybackMode = AppMode.LIVE

        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getStreamById(lastId)
            withContext(Dispatchers.Main) {
                if (entity != null) {
                    val stream = LiveStream(
                        entity.streamId, entity.name, entity.streamIcon, entity.categoryId, entity.num, entity.epgChannelId
                    )
                    playChannel(stream)
                } else {
                    Log.d(TAG, "Last channel not found, falling back to first available")
                    if (channels.isNotEmpty()) {
                        playChannel(channels.first())
                    }
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            isLoading = true
            val type = when(currentMode) {
                AppMode.LIVE -> "live"
                AppMode.VOD -> "vod"
                AppMode.SERIES -> "series"
            }
            
            // 1. Try to load categories from DB
            var cachedCategories = withContext(Dispatchers.IO) {
                dao.getCategoriesByType(type).map { Category(it.id, cleanCategoryName(it.name), it.type) }
            }.toMutableList()

            // Add History category for VOD/Series
            if (currentMode == AppMode.VOD || currentMode == AppMode.SERIES) {
                val hasHistory = withContext(Dispatchers.IO) {
                    dao.getHistoryByType(type).isNotEmpty()
                }
                if (hasHistory) {
                    cachedCategories.add(0, Category("history", getApplication<Application>().getString(R.string.history), type))
                }
            }

            if (cachedCategories.isNotEmpty()) {
                categories = cachedCategories
                selectCategory(categories.first(), forceRefresh = false)
            } else {
                // 2. If empty, fetch from API
                fetchCategories()
            }
            isLoading = false
        }
    }

    private fun fetchCategories() {
        val data = userData ?: return
        isLoading = true
        viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = when(currentMode) {
                    AppMode.LIVE -> apiService.getLiveCategories(data.username, data.password)
                    AppMode.VOD -> apiService.getVodCategories(data.username, data.password)
                    AppMode.SERIES -> apiService.getSeriesCategories(data.username, data.password)
                }
                
                if (response.isSuccessful) {
                    val type = when(currentMode) {
                        AppMode.LIVE -> "live"
                        AppMode.VOD -> "vod"
                        AppMode.SERIES -> "series"
                    }
                    val apiCategories = response.body() ?: emptyList()
                    var finalCategories = apiCategories.map { it.copy(type = type, name = cleanCategoryName(it.name)) }.toMutableList()
                    
                    // Add History category
                    if (currentMode == AppMode.VOD || currentMode == AppMode.SERIES) {
                        val hasHistory = withContext(Dispatchers.IO) {
                            dao.getHistoryByType(type).isNotEmpty()
                        }
                        if (hasHistory) {
                            finalCategories.add(0, Category("history", getApplication<Application>().getString(R.string.history), type))
                        }
                    }
                    
                    categories = finalCategories
                    
                    // Save to DB
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, type) })
                    }
                    refreshCategoryMaps()

                    if (categories.isNotEmpty()) {
                        selectCategory(categories.first(), forceRefresh = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching categories", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun selectCategory(category: Category, forceRefresh: Boolean = false) {
        if (selectedCategory?.id == category.id && !forceRefresh && 
            ((currentMode == AppMode.VOD && vodMovies.isNotEmpty()) || 
             (currentMode == AppMode.SERIES && seriesList.isNotEmpty()) ||
             (currentMode == AppMode.LIVE && channels.isNotEmpty()))) {
            return
        }
        
        selectedCategory = category
        
        // Immediate feedback: Clear lists for VOD/Series to prevent heavy diffing of old vs new large lists
        if (currentMode != AppMode.LIVE) {
            vodMovies = emptyList()
            seriesList = emptyList()
        }

        categoryLoadJob?.cancel()
        categoryLoadJob = viewModelScope.launch {
            try {
                when(currentMode) {
                    AppMode.LIVE -> {
                        val cachedStreams = withContext(Dispatchers.IO) {
                            dao.getStreamsByCategory(category.id).map { 
                                LiveStream(it.streamId, it.name, it.streamIcon, it.categoryId, it.num, it.epgChannelId)
                            }
                        }
                        if (cachedStreams.isNotEmpty() && !forceRefresh) {
                            channels = withContext(Dispatchers.Default) {
                                cachedStreams.sortedBy { it.num ?: it.streamId }
                            }
                            refreshEpgMap()
                            
                            if (currentChannel == null && channels.isNotEmpty()) {
                                val lastId = prefs.lastChannelId
                                val lastChannel = channels.find { it.streamId == lastId }
                                if (lastChannel != null) {
                                    playChannel(lastChannel)
                                } else if (category.id != "history") { 
                                    playChannel(channels.first())
                                }
                            }
                        } else {
                            fetchLiveStreams(category.id)
                        }
                    }
                    AppMode.VOD -> {
                        if (category.id == "history") {
                            vodMovies = withContext(Dispatchers.IO) {
                                dao.getHistoryByType("vod").map {
                                    VodMovie(
                                        it.streamId, it.name, it.streamIcon, it.categoryId, 
                                        it.rating, null, null, it.plot, null, null, it.genre, it.releaseDate
                                    )
                                }
                            }
                        } else {
                            val cachedVod = withContext(Dispatchers.IO) {
                                dao.getVodByCategory(category.id)
                            }
                            
                            if (cachedVod.isNotEmpty() && !forceRefresh) {
                                val mapped = withContext(Dispatchers.Default) {
                                    cachedVod.map {
                                        VodMovie(
                                            it.streamId, it.name, it.streamIcon, it.categoryId, 
                                            it.rating, it.added, it.containerExtension,
                                            it.plot, it.cast, it.director, it.genre, it.releaseDate
                                        )
                                    }.sortedByDescending { it.streamId }
                                }
                                vodMovies = mapped
                            } else {
                                fetchVodMovies(category.id)
                            }
                        }
                    }
                    AppMode.SERIES -> {
                        if (category.id == "history") {
                            seriesList = withContext(Dispatchers.IO) {
                                dao.getHistoryByType("series")
                                    .distinctBy { it.seriesId ?: it.streamId }
                                    .map {
                                        Series(
                                            it.seriesId ?: it.streamId, it.name, it.streamIcon, 
                                            it.plot, null, null, it.genre, it.releaseDate, it.rating, it.categoryId
                                        )
                                    }
                            }
                        } else {
                            val cachedSeries = withContext(Dispatchers.IO) {
                                dao.getSeriesByCategory(category.id)
                            }
                            if (cachedSeries.isNotEmpty() && !forceRefresh) {
                                val mapped = withContext(Dispatchers.Default) {
                                    cachedSeries.map {
                                        Series(it.seriesId, it.name, it.cover, it.plot, null, null, it.genre, it.releaseDate, it.rating, it.categoryId)
                                    }.sortedByDescending { it.seriesId }
                                }
                                seriesList = mapped
                            } else {
                                fetchSeries(category.id)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting category", e)
            }
        }
    }

    private fun fetchLiveStreams(categoryId: String) {
        val data = userData ?: return
        isLoading = true
        viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getLiveStreams(data.username, data.password, categoryId = categoryId)
                if (response.isSuccessful) {
                    val apiStreams = response.body() ?: emptyList()
                    channels = withContext(Dispatchers.Default) {
                        apiStreams.sortedBy { it.num ?: it.streamId }
                    }
                    withContext(Dispatchers.IO) {
                        dao.insertStreams(apiStreams.map { 
                            StreamEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.num, it.epgChannelId)
                        })
                    }
                    refreshEpgMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching live streams", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun fetchVodMovies(categoryId: String) {
        val data = userData ?: return
        isLoading = true
        viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getVodStreams(data.username, data.password, categoryId = categoryId)
                if (response.isSuccessful) {
                    val movies = response.body() ?: emptyList()
                    vodMovies = withContext(Dispatchers.Default) {
                        movies.sortedByDescending { it.streamId }
                    }
                    withContext(Dispatchers.IO) {
                        dao.insertVod(movies.map { 
                            VodEntity(
                                it.streamId, it.name, it.streamIcon, it.categoryId, 
                                it.rating, it.containerExtension, it.added,
                                it.plot, it.cast, it.director, it.genre, it.releaseDate
                            )
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching VOD", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun fetchSeries(categoryId: String) {
        val data = userData ?: return
        isLoading = true
        viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getSeries(data.username, data.password, categoryId = categoryId)
                if (response.isSuccessful) {
                    val apiSeries = response.body() ?: emptyList()
                    seriesList = withContext(Dispatchers.Default) {
                        apiSeries.sortedByDescending { it.seriesId }
                    }
                    withContext(Dispatchers.IO) {
                        dao.insertSeries(apiSeries.map { 
                            SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId, it.rating, it.plot, it.genre, it.releaseDate)
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching series", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun loadVodInfo(movie: VodMovie) {
        currentVodPlot = movie.plot
        currentVodGenre = movie.genre
        currentVodRating = movie.rating
        currentVodReleaseDate = movie.releaseDate
        
        viewModelScope.launch {
            selectedMovieHistory = withContext(Dispatchers.IO) {
                dao.getHistoryById(movie.streamId)
            }
        }

        if (movie.plot != null && movie.genre != null) return
        
        val data = userData ?: return
        vodInfoJob?.cancel()
        vodInfoJob = viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getVodInfo(data.username, data.password, movie.streamId)
                if (response.isSuccessful) {
                    val info = response.body()?.info
                    if (info != null) {
                        currentVodPlot = info.plot ?: currentVodPlot
                        currentVodGenre = info.genre ?: currentVodGenre
                        currentVodRating = info.rating ?: currentVodRating
                        currentVodReleaseDate = info.releaseDate ?: currentVodReleaseDate
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playVod(movie: VodMovie, fromStart: Boolean = false, skipResumeDialog: Boolean = false) {
        val data = userData ?: return
        pendingMovie = movie
        activePlaybackMode = AppMode.VOD // Set active playback mode
        currentSeriesId = null
        currentChannel = null // Clear current channel when playing VOD
        
        viewModelScope.launch {
            // Stop current playback to release resources
            player?.stop()
            player?.clearMediaItems()
            
            val history = withContext(Dispatchers.IO) { dao.getHistoryById(movie.streamId) }
            
            if (!skipResumeDialog && history != null && history.position > 15_000 && !fromStart && !showResumeDialog) {
                savedPosition = history.position
                showResumeDialog = true
                return@launch
            }
            
            showResumeDialog = false
            val baseUrl = data.url.removeSuffix("/")
            val movieUrl = "$baseUrl/movie/${data.username}/${data.password}/${movie.streamId}.${movie.containerExtension ?: "mp4"}"
            
            val mediaItem = MediaItem.Builder()
                .setUri(movieUrl)
                .setMediaId(movie.streamId.toString())
                .build()

            player?.setMediaItem(mediaItem)
            player?.prepare()
            if (fromStart) {
                player?.seekTo(0)
            } else if (history != null) {
                player?.seekTo(history.position)
            }
            player?.playWhenReady = true
        }
    }

    fun loadSeriesDetails(series: Series) {
        val data = userData ?: return
        selectedSeries = series
        isSeriesLoading = true
        lastWatchedEpisode = null
        viewModelScope.launch {
            try {
                // Fetch details from API
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getSeriesInfo(data.username, data.password, series.seriesId)
                if (response.isSuccessful) {
                    val details = response.body()
                    seriesDetails = details
                    
                    // Look for last watched episode in history
                    val history = withContext(Dispatchers.IO) {
                        dao.getLastHistoryBySeriesId(series.seriesId)
                    }
                    
                    if (history != null && details?.episodes != null) {
                        // Find matching episode in the episodes map in background
                        val foundEpisode = withContext(Dispatchers.Default) {
                            details.episodes.values.flatten().find { 
                                it.id?.toIntOrNull() == history.streamId 
                            }
                        }
                        lastWatchedEpisode = foundEpisode
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching series info", e)
            } finally {
                isSeriesLoading = false
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playEpisode(episode: Episode, fromStart: Boolean = false) {
        val data = userData ?: return
        val streamId = episode.id?.toIntOrNull() ?: return
        activePlaybackMode = AppMode.SERIES // Set active playback mode
        currentSeriesId = selectedSeries?.seriesId
        pendingEpisode = episode
        lastWatchedEpisode = episode // Mark as last watched for UI focus
        currentChannel = null // Clear current channel when playing series
        
        // Stop current playback
        player?.stop()
        player?.clearMediaItems()
        
        // Convert Episode to VodMovie format for history/resume system
        val seriesName = selectedSeries?.name ?: ""
        val episodeTitle = episode.title ?: "Avsnitt"
        
        val movieRepresentation = VodMovie(
            streamId = streamId,
            name = if (seriesName.isNotEmpty()) "$seriesName - $episodeTitle" else episodeTitle,
            streamIcon = episode.info?.movieImage ?: selectedSeries?.cover,
            categoryId = selectedSeries?.categoryId ?: "series",
            rating = selectedSeries?.rating,
            added = null,
            containerExtension = episode.containerExtension,
            plot = episode.info?.plot ?: selectedSeries?.plot
        )
        pendingMovie = movieRepresentation

        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { dao.getHistoryById(streamId) }
                
                if (history != null && history.position > 15_000 && !fromStart && !showResumeDialog) {
                    savedPosition = history.position
                    showResumeDialog = true
                    return@launch
                }
                
                showResumeDialog = false
                val baseUrl = data.url.removeSuffix("/")
                val extension = episode.containerExtension ?: "mp4"
                val episodeUrl = "$baseUrl/series/${data.username}/${data.password}/$streamId.$extension"
                
                val mediaItem = MediaItem.Builder()
                    .setUri(episodeUrl)
                    .setMediaId(streamId.toString())
                    .build()

                player?.setMediaItem(mediaItem)
                player?.prepare()
                if (fromStart) {
                    player?.seekTo(0)
                } else if (history != null) {
                    player?.seekTo(history.position)
                }
                player?.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Error playing episode: ${e.message}", e)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playChannel(stream: LiveStream) {
        if (currentChannel?.streamId == stream.streamId && player?.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            return // Already playing or preparing this channel
        }
        val data = userData ?: return
        currentChannel = stream
        activePlaybackMode = AppMode.LIVE // Set active playback mode
        currentSeriesId = null
        
        // Save as last played channel
        prefs.lastChannelId = stream.streamId

        // Save to history for "Recent Channels"
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertHistory(
                PlaybackHistoryEntity(
                    streamId = stream.streamId,
                    name = stream.name,
                    streamIcon = stream.streamIcon,
                    categoryId = stream.categoryId,
                    type = "live",
                    position = 0,
                    duration = 0,
                    lastWatched = System.currentTimeMillis(),
                    plot = null,
                    genre = null,
                    releaseDate = null,
                    rating = null
                )
            )
            loadRecentChannels()
        }
        
        // Stop current playback
        player?.stop()
        player?.clearMediaItems()
        
        val baseUrl = data.url.removeSuffix("/")
        // Många servrar kräver .ts för live-strömmar, ändrat från .m3u8 pga 403-fel i logcat
        val streamUrl = "$baseUrl/live/${data.username}/${data.password}/${stream.streamId}.ts"
        
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()
        
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    fun selectSubtitle(track: TrackInfo?) {
        player?.let { p ->
            if (track == null) {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                selectedSubtitleId = null
            } else {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        androidx.media3.common.TrackSelectionOverride(
                            p.currentTracks.groups[track.groupIndex].mediaTrackGroup,
                            track.trackIndex
                        )
                    )
                    .build()
                selectedSubtitleId = track.id
            }
        }
    }

    fun selectAudio(track: TrackInfo) {
        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(
                        p.currentTracks.groups[track.groupIndex].mediaTrackGroup,
                        track.trackIndex
                    )
                )
                .build()
            selectedAudioId = track.id
        }
    }

    fun getPlayer(): ExoPlayer? = player

    private fun startPlaybackProgressLoop() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (isPlaying) {
                player?.let { p ->
                    playbackPosition = p.currentPosition.coerceAtLeast(0L)
                    playbackDuration = p.duration.coerceAtLeast(0L)
                    
                    // Save history every 5 seconds or on significant progress
                    if ((p.currentPosition / 1000) % 5 == 0L) {
                        if (activePlaybackMode == AppMode.VOD || activePlaybackMode == AppMode.SERIES) {
                            saveProgress(p.currentPosition, p.duration)
                        }
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopPlaybackProgressLoop() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    private fun saveProgress(position: Long, duration: Long) {
        val movie = pendingMovie ?: return
        if (duration <= 0) return
        
        viewModelScope.launch(Dispatchers.IO) {
            // Logic:
            // 1. If near the absolute end (97%+), we consider it "finished" and remove from history
            // 2. If at least 10 seconds in, we save progress
            // 3. We use a 10s margin from the end to keep it in history
            
            val isNearEnd = position > (duration - 10_000) || position > (duration * 0.97)
            
            if (isNearEnd) {
                Log.d(TAG, "Content finished (97%+), removing from history: ${movie.name}")
                dao.deleteHistory(movie.streamId)
            } else if (position > 10_000) {
                dao.insertHistory(
                    PlaybackHistoryEntity(
                        streamId = movie.streamId,
                        name = movie.name,
                        streamIcon = movie.streamIcon,
                        categoryId = movie.categoryId,
                        type = if (activePlaybackMode == AppMode.VOD) "vod" else "series",
                        position = position,
                        duration = duration,
                        seriesId = if (activePlaybackMode == AppMode.SERIES) currentSeriesId else null,
                        plot = movie.plot,
                        genre = movie.genre,
                        releaseDate = movie.releaseDate,
                        rating = movie.rating
                    )
                )
            }
        }
    }

    fun loadChannelEpg(stream: LiveStream) {
        viewModelScope.launch(Dispatchers.IO) {
            isFetchingChannelEpg = true
            try {
                val candidates = mutableListOf<String>()
                if (!stream.epgChannelId.isNullOrBlank() && stream.epgChannelId != "null") {
                    candidates.add(stream.epgChannelId)
                }
                channelIdMap[stream.name]?.let { candidates.add(it) }
                normalizedChannelIdMap[normalizeName(stream.name)]?.let { candidates.add(it) }
                
                val epgId = candidates.distinct().firstOrNull()

                if (epgId != null) {
                    val now = System.currentTimeMillis()
                    val epg = dao.getEpgForChannel(epgId, now - 3600_000) // Start from 1h ago to show current
                        .filter { it.start < now + (12 * 3600 * 1000) }
                    
                    withContext(Dispatchers.Main) {
                        channelEpgList = epg
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        channelEpgList = emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading channel EPG", e)
            } finally {
                isFetchingChannelEpg = false
            }
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    @OptIn(UnstableApi::class)
    fun seekTo(position: Long, smooth: Boolean = false) {
        player?.let {
            val targetPosition = position.coerceIn(0L, it.duration)
            if (smooth) {
                // CLOSEST_SYNC is faster for continuous seeking
                it.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            } else {
                // EXACT for final snap
                it.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            }
            it.seekTo(targetPosition)
            // Update immediately so UI reflects the target position during seeking
            playbackPosition = targetPosition
        }
    }

    fun skipForward() {
        player?.let {
            it.seekTo(it.currentPosition + 10_000)
        }
    }

    fun skipBackward() {
        player?.let {
            it.seekTo(it.currentPosition - 10_000)
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null
    fun performSearch(query: String) {
        searchQuery = query
        if (query.length < 2) {
            filteredVod = emptyList()
            filteredSeries = emptyList()
            loadSearchHistory()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300) // Debounce search
            val lowerQuery = query.lowercase()
            
            // Search in VOD
            val vodResults = dao.searchVod("%$lowerQuery%")
            val seriesResults = dao.searchSeries("%$lowerQuery%")
            
            withContext(Dispatchers.Main) {
                filteredVod = vodResults.map { 
                    VodMovie(
                        it.streamId, it.name, it.streamIcon, it.categoryId, 
                        it.rating, it.added, it.containerExtension,
                        it.plot, it.cast, it.director, it.genre, it.releaseDate
                    )
                }
                filteredSeries = seriesResults.map {
                    Series(it.seriesId, it.name, it.cover, it.plot, null, null, null, null, it.rating, it.categoryId)
                }
            }
        }
    }

    fun saveSearchQuery(query: String) {
        if (query.isBlank() || query.length < 2) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertSearchQuery(SearchHistoryEntity(query.trim()))
            loadSearchHistory()
        }
    }

    fun loadSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = dao.getRecentSearches().map { it.query }
            withContext(Dispatchers.Main) {
                searchHistory = history
            }
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSearchQuery(query)
            loadSearchHistory()
        }
    }

    fun checkForUpdates() {
        if (isCheckingUpdate) return
        
        viewModelScope.launch {
            isCheckingUpdate = true
            updateStatus = null
            try {
                Log.d(TAG, "Checking for updates at GitHub: macseger/XTR-Tv")
                val service = ApiClient.createGithubService()
                val response = service.getLatestRelease("macseger", "XTR-Tv")
                
                Log.d(TAG, "GitHub response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val release = response.body()
                    Log.d(TAG, "GitHub release found: ${release?.tag_name}")
                    if (release != null) {
                        val pInfo = getApplication<Application>()
                            .packageManager
                            .getPackageInfo(getApplication<Application>().packageName, 0)
                        val currentVersion = (pInfo.versionName ?: "0.0.0").removePrefix("v")
                        val githubVersion = (release.tag_name ?: "0.0.0").removePrefix("v")
                        
                        Log.d(TAG, "Current app version: $currentVersion, GitHub version: $githubVersion")
                            
                        if (githubVersion != currentVersion) {
                            latestRelease = release
                            updateStatus = getApplication<Application>().getString(R.string.update_available)
                        } else {
                            updateStatus = getApplication<Application>().getString(R.string.update_not_available)
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "GitHub error response: $errorBody")
                    updateStatus = "GitHub Fel: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed with exception: ${e.message}", e)
                updateStatus = "Error: ${e.message}"
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    private fun handleSeriesEnded() {
        val current = pendingEpisode ?: return
        val details = seriesDetails ?: return
        
        // Find next episode
        var foundNext = false
        details.episodes?.values?.flatten()?.let { allEpisodes ->
            val currentIndex = allEpisodes.indexOfFirst { it.id == current.id }
            if (currentIndex != -1 && currentIndex < allEpisodes.size - 1) {
                nextEpisode = allEpisodes[currentIndex + 1]
                foundNext = true
            }
        }
        
        if (foundNext) {
            showNextEpisodeDialog = true
        } else {
            // End of series, just go back
            viewModelScope.launch(Dispatchers.Main) {
                changeMode(activePlaybackMode)
            }
        }
    }

    fun playNextEpisode() {
        nextEpisode?.let { 
            showNextEpisodeDialog = false
            playEpisode(it, fromStart = true)
            nextEpisode = null
        }
    }

    @OptIn(UnstableApi::class)
    fun toggleTunneling() {
        val context = getApplication<Application>().applicationContext
        isTunnelingEnabled = !isTunnelingEnabled
        prefs.isTunnelingEnabled = isTunnelingEnabled
        
        player?.let { p ->
            val currentParams = p.trackSelectionParameters
            val newParams = if (currentParams is DefaultTrackSelector.Parameters) {
                currentParams.buildUpon()
                    .setTunnelingEnabled(isTunnelingEnabled)
                    .build()
            } else {
                DefaultTrackSelector.Parameters.Builder(context)
                    .setTunnelingEnabled(isTunnelingEnabled)
                    .build()
            }
            
            p.trackSelectionParameters = newParams
            
            // Restart current channel if playing to apply tunneling
            currentChannel?.let { playChannel(it) }
        }
    }

    @OptIn(UnstableApi::class)
    fun toggleFrameRateMatching() {
        isFrameRateMatchingEnabled = !isFrameRateMatchingEnabled
        prefs.isFrameRateMatchingEnabled = isFrameRateMatchingEnabled
        
        // Restart current channel to apply
        currentChannel?.let { playChannel(it) }
    }

    fun updateCustomEpgUrl(url: String?) {
        customEpgUrl = url
        prefs.customEpgUrl = url
    }

    fun toggleInternalSwedishEpg() {
        useInternalSwedishEpg = !useInternalSwedishEpg
        prefs.useInternalSwedishEpg = useInternalSwedishEpg
        if (useInternalSwedishEpg) {
            customEpgUrl = null
            prefs.customEpgUrl = null
        }
    }

    fun loadRecentChannels() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = dao.getHistoryByType("live").take(10)
            withContext(Dispatchers.Main) {
                recentChannels = history.map {
                    LiveStream(it.streamId, it.name, it.streamIcon, it.categoryId ?: "0", null, null)
                }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Stop player
                player?.stop()
                
                // 2. Clear Database
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                }
                
                // 3. Clear Prefs
                prefs.clear()
                
                // 4. Callback to navigate
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during logout", e)
            }
        }
    }

    fun updateServerUrl(newUrl: String) {
        val data = userData ?: return
        val updatedData = data.copy(url = newUrl)
        this.userData = updatedData
        prefs.saveUser(updatedData)
        prefs.lastFullSync = 0L // Force new sync on URL change
        
        // Refresh data from new server
        forceUpdateChannels()
        refreshVodAndSeries()
        refreshEpg()
    }

    fun isSyncNeeded(): Boolean {
        val lastSync = prefs.lastFullSync
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursMs = 24 * 3600 * 1000L
        return lastSync == 0L || (currentTime - lastSync) > twentyFourHoursMs
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}
