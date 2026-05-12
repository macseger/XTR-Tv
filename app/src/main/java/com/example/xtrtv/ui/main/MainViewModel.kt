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
import com.example.xtrtv.R
import com.example.xtrtv.api.*
import com.example.xtrtv.data.Prefs
import com.example.xtrtv.data.UserData
import com.example.xtrtv.data.db.*
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
    var isTunnelingEnabled by mutableStateOf(false)
    var isFrameRateMatchingEnabled by mutableStateOf(false)
    var subtitleTracks by mutableStateOf<List<TrackInfo>>(emptyList())
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

    data class TrackInfo(val id: String, val name: String, val groupIndex: Int, val trackIndex: Int)

    private var player: ExoPlayer? = null
    private var userData: UserData? = null
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.appDao()
    private val prefs = Prefs(application)

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
                15_000, // Min buffer (Lowered for less RAM usage)
                30_000, // Max buffer
                1_000,  // Buffer for playback (Faster start)
                2_500   // Buffer for rebuffering
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
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
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val subTracks = mutableListOf<TrackInfo>()
                    tracks.groups.forEachIndexed { groupIndex, group ->
                        if (group.type == C.TRACK_TYPE_TEXT) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val label = format.label ?: format.language ?: getApplication<Application>().getString(R.string.track_label, subTracks.size + 1)
                                subTracks.add(TrackInfo(format.id ?: "$groupIndex-$i", label, groupIndex, i))
                            }
                        }
                    }
                    subtitleTracks = subTracks
                }
            })
        }
        loadInitialData()
        
        // Centralized time update loop
        viewModelScope.launch {
            while (true) {
                currentTime = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
        
        // Smart EPG update
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val hasData = withContext(Dispatchers.IO) { 
                dao.getValidEpgCount(currentTime) > 0 
            }
            
            if (hasData) {
                Log.d(TAG, "Found existing EPG data in DB, skipping auto-download")
                // Load mappings from DB to enable name matching
                withContext(Dispatchers.IO) {
                    val savedMappings = dao.getAllMappings()
                    updateChannelMappings(savedMappings.associate { it.displayName to it.channelId })
                    lastEpgUpdate = dao.getLastUpdatedTime()
                }
                refreshEpgMap()
            } else {
                Log.d(TAG, "No valid EPG data found, triggering refresh")
                refreshEpg()
            }
        }
    }

    fun refreshEpg() {
        if (userData == null) return
        viewModelScope.launch {
            isEpgUpdating = true
            val currentTime = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                dao.deleteOldEpg(currentTime)
            }
            fetchEpgFromApi()
        }
    }

    fun refreshVodAndSeries() {
        if (userData == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                val data = userData!!
                val apiService = ApiClient.createService(data.url)
                
                // 1. Clear old data to ensure dead movies/series are removed
                withContext(Dispatchers.IO) {
                    dao.clearVod()
                    dao.clearSeries()
                    // We don't clear categories here to avoid UI flickering, 
                    // they will be replaced by the insert below
                }

                // 2. Refresh categories
                val vodCatResp = apiService.getVodCategories(data.username, data.password)
                if (vodCatResp.isSuccessful) {
                    val apiCategories = vodCatResp.body() ?: emptyList()
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "vod") })
                    }
                    if (currentMode == AppMode.VOD) {
                        var finalCats = apiCategories.map { it.copy(type = "vod", name = cleanCategoryName(it.name)) }.toMutableList()
                        val hasHistory = withContext(Dispatchers.IO) { dao.getHistoryByType("vod").isNotEmpty() }
                        if (hasHistory) finalCats.add(0, Category("history", getApplication<Application>().getString(R.string.history), "vod"))
                        categories = finalCats
                    }
                }

                val seriesCatResp = apiService.getSeriesCategories(data.username, data.password)
                if (seriesCatResp.isSuccessful) {
                    val apiCategories = seriesCatResp.body() ?: emptyList()
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "series") })
                    }
                    if (currentMode == AppMode.SERIES) {
                        var finalCats = apiCategories.map { it.copy(type = "series", name = cleanCategoryName(it.name)) }.toMutableList()
                        val hasHistory = withContext(Dispatchers.IO) { dao.getHistoryByType("series").isNotEmpty() }
                        if (hasHistory) finalCats.add(0, Category("history", getApplication<Application>().getString(R.string.history), "series"))
                        categories = finalCats
                    }
                }

                // 3. Sync ALL content (this also populates the UI for the current category)
                syncAllContent()
                
                // 4. Update the UI state for the current selected category
                selectedCategory?.let { 
                    selectCategory(it, forceRefresh = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing VOD/Series", e)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun syncAllContent() = withContext(Dispatchers.IO) {
        val data = userData ?: return@withContext
        try {
            val apiService = ApiClient.createService(data.url)
            
            // 1. Fetch categories first to ensure we can iterate if "fetch all" fails
            val vodCats = dao.getCategoriesByType("vod")
            val seriesCats = dao.getCategoriesByType("series")

            // Try to fetch ALL VOD first (faster if supported)
            val vodResp = apiService.getVodStreams(data.username, data.password)
            if (vodResp.isSuccessful && !vodResp.body().isNullOrEmpty()) {
                val movies = vodResp.body()!!
                dao.insertVod(movies.map { 
                    VodEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.containerExtension, it.added)
                })
                Log.d(TAG, "Sync: Successfully fetched all ${movies.size} VODs at once")
            } else {
                // Fallback: Fetch per category
                Log.d(TAG, "Sync: 'Fetch all VOD' failed or empty, falling back to per-category fetch")
                vodCats.forEach { cat ->
                    val resp = apiService.getVodStreams(data.username, data.password, categoryId = cat.id)
                    if (resp.isSuccessful) {
                        resp.body()?.let { movies ->
                            dao.insertVod(movies.map { 
                                VodEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.containerExtension, it.added)
                            })
                        }
                    }
                }
            }

            // Try to fetch ALL Series first
            val seriesResp = apiService.getSeries(data.username, data.password)
            if (seriesResp.isSuccessful && !seriesResp.body().isNullOrEmpty()) {
                val series = seriesResp.body()!!
                dao.insertSeries(series.map { 
                    SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId, it.rating, it.plot)
                })
                Log.d(TAG, "Sync: Successfully fetched all ${series.size} series at once")
            } else {
                // Fallback: Fetch per category
                Log.d(TAG, "Sync: 'Fetch all Series' failed or empty, falling back to per-category fetch")
                seriesCats.forEach { cat ->
                    val resp = apiService.getSeries(data.username, data.password, categoryId = cat.id)
                    if (resp.isSuccessful) {
                        resp.body()?.let { series ->
                            dao.insertSeries(series.map { 
                                SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId, it.rating, it.plot)
                            })
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing all content", e)
        }
    }

    private fun fetchAllContentForSearch() {
        viewModelScope.launch {
            syncAllContent()
        }
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
                isEpgUpdating = true
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
                            if (batch.size >= 500) {
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
                        dao.insertMappings(mappingEntities)
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

    private suspend fun refreshEpgMap() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val currentChannels = channels
        if (currentChannels.isEmpty()) return@withContext

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
            dao.getCurrentEpgForChannels(allCandidateIds.toList(), currentTime)
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
    }

    fun changeMode(mode: AppMode) {
        if (currentMode == mode) return
        currentMode = mode
        loadInitialData()
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
                
                // NEW: Even if categories are cached, ensure we have content for search
                if (currentMode != AppMode.LIVE) {
                    fetchAllContentForSearch()
                }
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

                    // Index search content in background on first load
                    if (currentMode != AppMode.LIVE) {
                        fetchAllContentForSearch()
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
        selectedCategory = category

        viewModelScope.launch {
            when(currentMode) {
                AppMode.LIVE -> {
                    val cachedStreams = withContext(Dispatchers.IO) {
                        dao.getStreamsByCategory(category.id).map { 
                            LiveStream(it.streamId, it.name, it.streamIcon, it.categoryId, it.num, it.epgChannelId)
                        }
                    }
                    if (cachedStreams.isNotEmpty() && !forceRefresh) {
                        channels = cachedStreams.sortedBy { it.num ?: it.streamId }
                        refreshEpgMap()
                        
                        // Check if we should auto-play the last channel or the first in list
                        if (currentChannel == null && channels.isNotEmpty()) {
                            val lastId = prefs.lastChannelId
                            val lastChannel = channels.find { it.streamId == lastId }
                            if (lastChannel != null) {
                                playChannel(lastChannel)
                            } else if (category.id != "history") { 
                                // Only auto-play first if not in a history/special category to avoid confusion
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
                            dao.getVodByCategory(category.id).map {
                                VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.added, it.containerExtension)
                            }
                        }
                        if (cachedVod.isNotEmpty() && !forceRefresh) {
                            vodMovies = cachedVod
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
                            dao.getSeriesByCategory(category.id).map {
                                Series(it.seriesId, it.name, it.cover, it.plot, null, null, null, null, it.rating, it.categoryId)
                            }
                        }
                        if (cachedSeries.isNotEmpty() && !forceRefresh) {
                            seriesList = cachedSeries
                        } else {
                            fetchSeries(category.id)
                        }
                    }
                }
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
                    channels = apiStreams
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
                    vodMovies = movies.sortedByDescending { it.streamId }
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
                    seriesList = apiSeries.sortedByDescending { it.seriesId }
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
        val streamUrl = "$baseUrl/live/${data.username}/${data.password}/${stream.streamId}.ts"
        
        val mediaItem = MediaItem.fromUri(streamUrl)
        
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
            }
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
        
        // Refresh data from new server
        forceUpdateChannels()
        refreshVodAndSeries()
        refreshEpg()
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}
