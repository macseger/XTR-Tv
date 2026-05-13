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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    
    private val PREFIX_REGEX = Regex("^(se|se:|sweden|sweden:)\\s*")
    private val SUFFIX_REGEX = Regex("\\s+(fhd|hd|sd|hevc|4k|se|s)$")
    private val CLEAN_REGEX = Regex("[^a-z0-9]")
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
    var lastWatchedEpisode by mutableStateOf<com.example.xtrtv.api.Episode?>(null)
    var isSeriesLoading by mutableStateOf(false)
    
    var epgMap by mutableStateOf<Map<Int, EpgEntity>>(emptyMap())
        private set
    var lastEpgUpdate by mutableStateOf<Long?>(null)
        private set

    private var channelIdMap = emptyMap<String, String>()
    private var normalizedChannelIdMap = emptyMap<String, String>()

    private fun updateChannelMappings(newMap: Map<String, String>) {
        channelIdMap = newMap
        normalizedChannelIdMap = newMap.entries.associate { (displayName, id) ->
            normalizeName(displayName) to id
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace(PREFIX_REGEX, "") // Ta bort vanliga prefix
            .replace(SUFFIX_REGEX, "") // Ta bort vanliga suffix
            .replace(CLEAN_REGEX, "") // Behåll bara bokstäver och siffror
    }

    private fun cleanCategoryName(name: String): String {
        return name.replace(CAT_PREFIX_REGEX, "").trim()
    }
    var selectedCategory by mutableStateOf<Category?>(null)
    var currentChannel by mutableStateOf<LiveStream?>(null)
    
    // Resume dialog state
    var pendingMovie by mutableStateOf<VodMovie?>(null)
    var pendingEpisode by mutableStateOf<com.example.xtrtv.api.Episode?>(null)
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
    var subtitleTracks by mutableStateOf<List<TrackInfo>>(emptyList())
    var selectedSubtitleId by mutableStateOf<String?>(null)
    var audioTracks by mutableStateOf<List<TrackInfo>>(emptyList())
    var selectedAudioId by mutableStateOf<String?>(null)
        private set

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
    var filteredVod by mutableStateOf<List<VodMovie>>(emptyList())
    var filteredSeries by mutableStateOf<List<Series>>(emptyList())

    var updateStatus by mutableStateOf<String?>(null)
    var isCheckingUpdate by mutableStateOf(false)
    var latestRelease by mutableStateOf<GithubRelease?>(null)

    var showNextEpisodeDialog by mutableStateOf(false)
    var nextEpisode: com.example.xtrtv.api.Episode? = null

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
                        withContext(Dispatchers.IO) {
                            dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                        }
                        categories = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                        
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
                                channels = apiStreams.sortedBy { it.num ?: it.streamId }
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

    private fun startGentleBackgroundSync() {
        if (isBackgroundSyncing) return
        
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                isBackgroundSyncing = true
                val data = userData ?: return@launch
                val apiService = ApiClient.createService(data.url)
                
                // A. Sync VOD/Series Categories first (Small metadata)
                backgroundSyncMessage = getApplication<Application>().getString(R.string.loading) + "..."
                
                val vCatResp = apiService.getVodCategories(data.username, data.password)
                if (vCatResp.isSuccessful) {
                    dao.insertCategories(vCatResp.body()?.map { CategoryEntity(it.id, it.name, "vod") } ?: emptyList())
                }
                
                val sCatResp = apiService.getSeriesCategories(data.username, data.password)
                if (sCatResp.isSuccessful) {
                    dao.insertCategories(sCatResp.body()?.map { CategoryEntity(it.id, it.name, "series") } ?: emptyList())
                }

                // B. Sync Content in small chunks with delays (The "Gentle" part)
                // Use smaller chunks and longer delays to ensure UI thread database access isn't blocked
                
                // Throttled VOD Sync
                val vodResp = apiService.getVodStreams(data.username, data.password)
                if (vodResp.isSuccessful) {
                    val movies = vodResp.body() ?: emptyList()
                    val chunks = movies.chunked(100) // Slightly larger chunks for background
                    chunks.forEachIndexed { index, chunk ->
                        backgroundSyncMessage = "Syncing VOD: ${((index + 1) * 100 * 100 / movies.size).coerceAtMost(100)}%"
                        dao.insertVod(chunk.map { 
                            VodEntity(it.streamId, it.name, it.streamIcon, it.categoryId ?: "0", it.rating, it.containerExtension, it.added)
                        })
                        kotlinx.coroutines.delay(200)
                    }
                }

                // Throttled Series Sync
                val seriesResp = apiService.getSeries(data.username, data.password)
                if (seriesResp.isSuccessful) {
                    val seriesList = seriesResp.body() ?: emptyList()
                    val chunks = seriesList.chunked(100)
                    chunks.forEachIndexed { index, chunk ->
                        backgroundSyncMessage = "Syncing Series: ${((index + 1) * 100 * 100 / seriesList.size).coerceAtMost(100)}%"
                        dao.insertSeries(chunk.map { 
                            SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId ?: "0", it.rating, it.plot)
                        })
                        kotlinx.coroutines.delay(200)
                    }
                }

                // C. Finally EPG
                backgroundSyncMessage = getApplication<Application>().getString(R.string.updating_epg)
                fetchEpgFromApi() // Already has batching

                prefs.lastFullSync = System.currentTimeMillis()
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
    fun init(context: Context, data: UserData) {
        if (this.userData != null) return
        this.userData = data
        
        isTunnelingEnabled = prefs.isTunnelingEnabled
        isFrameRateMatchingEnabled = prefs.isFrameRateMatchingEnabled
        
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min buffer
                60_000, // Max buffer
                2_500,  // Buffer for playback
                5_000   // Buffer for rebuffering
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

        player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractorsFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build().apply {
            
            trackSelectionParameters = DefaultTrackSelector.Parameters.Builder(context)
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
        // Not used anymore in favor of startGentleBackgroundSync
        // but keeping it as a stub if needed for manual force refresh
    }

    private fun fetchAllContentForSearch() {
        // Handled by background sync
    }

    fun forceUpdateChannels() {
        if (userData == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    dao.clearStreams()
                    dao.clearCategories()
                }
                fetchCategories()
            } catch (e: Exception) {
                Log.e(TAG, "Error forcing channel update", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun fetchEpgFromApi() {
        val data = userData ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // isEpgUpdating should be managed by the caller
                val baseUrl = data.url.removeSuffix("/")
                val epgUrl = "$baseUrl/xmltv.php?username=${data.username}&password=${data.password}"
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

                        updateChannelMappings(result.channelMap)
                        Log.d(TAG, "Parsed $totalParsed EPG items, saved to DB in batches")
                        
                        // Save mappings to DB for next restart
                        val mappingEntities = result.channelMap.map { 
                            ChannelMappingEntity(it.key, it.value) 
                        }
                        mappingEntities.chunked(500).forEach { dao.insertMappings(it) }
                        lastEpgUpdate = System.currentTimeMillis()
                        refreshEpgMap()
                    }
                } else {
                    Log.e(TAG, "EPG Download failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching EPG", e)
                e.printStackTrace()
            } finally {
                isEpgUpdating = false
            }
        }
    }

    private suspend fun refreshEpgMap() = withContext(Dispatchers.Default) {
        if (isMappingEpg) return@withContext
        val currentTimeMs = System.currentTimeMillis()
        val currentChannels = channels
        if (currentChannels.isEmpty()) return@withContext
        
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

            // 2. Fetch all current programs for these IDs in one batch
            val programs = if (allCandidateIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    dao.getCurrentEpgForChannels(allCandidateIds.toList(), currentTimeMs)
                }
            } else {
                emptyList()
            }
            
            // Group by channelId as there might be multiple (though getCurrentEpgForChannels filters by time)
            val programLookup = programs.associateBy { it.channelId }

            // 3. Map programs back to channels based on priority
            val newEpgMap = mutableMapOf<Int, EpgEntity>()
            currentChannels.forEach { stream ->
                val candidates = channelCandidatesMap[stream.streamId] ?: emptyList()
                for (id in candidates) {
                    val match = programLookup[id]
                    if (match != null) {
                        newEpgMap[stream.streamId] = match
                        break
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                epgMap = newEpgMap
                Log.i(TAG, "EPG Map updated: ${newEpgMap.size} matches found out of ${currentChannels.size} channels")
            }
        } finally {
            isMappingEpg = false
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
        
        if (oldMode == mode) return
        currentMode = mode
        loadInitialData()
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
                    val stream = com.example.xtrtv.api.LiveStream(
                        entity.streamId, entity.name, entity.streamIcon, entity.categoryId, entity.num, entity.epgChannelId
                    )
                    playChannel(stream)
                } else {
                    Log.d(TAG, "Last channel entity not found, falling back to first available if in LIVE mode")
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
                                    VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, null, null, null)
                                }
                            }
                        } else {
                            val cachedVod = withContext(Dispatchers.IO) {
                                dao.getVodByCategory(category.id)
                            }
                            
                            if (cachedVod.isNotEmpty() && !forceRefresh) {
                                val mapped = withContext(Dispatchers.Default) {
                                    cachedVod.map {
                                        VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.added, it.containerExtension)
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
                                        Series(it.seriesId ?: it.streamId, it.name, it.streamIcon, null, null, null, null, null, null, it.categoryId)
                                    }
                            }
                        } else {
                            val cachedSeries = withContext(Dispatchers.IO) {
                                dao.getSeriesByCategory(category.id)
                            }
                            if (cachedSeries.isNotEmpty() && !forceRefresh) {
                                val mapped = withContext(Dispatchers.Default) {
                                    cachedSeries.map {
                                        Series(it.seriesId, it.name, it.cover, it.plot, null, null, null, null, it.rating, it.categoryId)
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
                            VodEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.containerExtension, it.added)
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
                            SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId, it.rating, it.plot)
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

    @OptIn(UnstableApi::class)
    fun playVod(movie: VodMovie, fromStart: Boolean = false) {
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
            
            if (history != null && history.position > 10_000 && !fromStart && !showResumeDialog) {
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
                        // Find matching episode in the episodes map
                        val foundEpisode = details.episodes.values.flatten().find { 
                            it.id?.toIntOrNull() == history.streamId 
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
            containerExtension = episode.containerExtension
        )
        pendingMovie = movieRepresentation

        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { dao.getHistoryById(streamId) }
                
                if (history != null && history.position > 10_000 && !fromStart && !showResumeDialog) {
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
        
        // Stop current playback
        player?.stop()
        player?.clearMediaItems()
        
        val baseUrl = data.url.removeSuffix("/")
        // Försök tvinga m3u8-format för live om .ts krånglar
        val streamUrl = "$baseUrl/live/${data.username}/${data.password}/${stream.streamId}.m3u8"
        
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
            // If near end (95%), delete from history or mark as finished
            if (position > duration * 0.95) {
                dao.deleteHistory(movie.streamId)
            } else if (position > 5000) {
                dao.insertHistory(
                    PlaybackHistoryEntity(
                        streamId = movie.streamId,
                        name = movie.name,
                        streamIcon = movie.streamIcon,
                        categoryId = movie.categoryId,
                        type = if (activePlaybackMode == AppMode.VOD) "vod" else "series",
                        position = position,
                        duration = duration,
                        seriesId = if (activePlaybackMode == AppMode.SERIES) currentSeriesId else null
                    )
                )
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

    fun performSearch(query: String) {
        searchQuery = query
        if (query.length < 2) {
            filteredVod = emptyList()
            filteredSeries = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val lowerQuery = query.lowercase()
            
            // Search in VOD
            val vodResults = dao.searchVod("%$lowerQuery%")
            val seriesResults = dao.searchSeries("%$lowerQuery%")
            
            withContext(Dispatchers.Main) {
                filteredVod = vodResults.map { 
                    VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.added, it.containerExtension) 
                }
                filteredSeries = seriesResults.map {
                    Series(it.seriesId, it.name, it.cover, it.plot, null, null, null, null, it.rating, it.categoryId)
                }
            }
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
                        val currentVersion = pInfo.versionName
                        
                        Log.d(TAG, "Current app version: $currentVersion, GitHub version: ${release.tag_name}")
                            
                        if (release.tag_name != currentVersion) {
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
