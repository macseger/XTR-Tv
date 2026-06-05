package com.example.xtrtv.ui.main

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.example.xtrtv.R
import com.example.xtrtv.api.*
import com.example.xtrtv.data.Prefs
import com.example.xtrtv.data.UserData
import com.example.xtrtv.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    companion object {
        private val PREFIX_REGEX = Regex("^(se|se:|sweden|sweden:)\\s*")
        private val BRACKET_REGEX = Regex("[\\[(].*?[\\])]")
        private val SUFFIX_REGEX = Regex("\\s+(fhd|hd|sd|hevc|4k|se|s)$")
        private val CLEAN_REGEX = Regex("[^a-z0-9åäö]")
        private val CAT_PREFIX_REGEX = Regex("^(Movies|Series|Filmer|Serier)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE)
    }

    enum class AppMode { LIVE, VOD, SERIES }
    var currentMode by mutableStateOf(AppMode.LIVE)
        private set
    var activePlaybackMode by mutableStateOf(AppMode.LIVE)

    var categories by mutableStateOf<List<Category>>(emptyList())
    var channels by mutableStateOf<List<LiveStream>>(emptyList())
    var vodMovies by mutableStateOf<List<VodMovie>>(emptyList())
    var seriesList by mutableStateOf<List<Series>>(emptyList())
    
    var selectedSeries by mutableStateOf<Series?>(null)
    var seriesDetails by mutableStateOf<SeriesDetailsResponse?>(null)
    var lastWatchedEpisode by mutableStateOf<Episode?>(null)
    var isSeriesLoading by mutableStateOf(false)
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
            .replace(BRACKET_REGEX, "")
            .replace(PREFIX_REGEX, "")
            .replace(SUFFIX_REGEX, "")
            .replace(CLEAN_REGEX, "")
    }

    private fun cleanCategoryName(name: String): String {
        return name.replace(CAT_PREFIX_REGEX, "").trim()
    }

    var selectedCategory by mutableStateOf<Category?>(null)
    var currentChannel by mutableStateOf<LiveStream?>(null)
    var recentChannels by mutableStateOf<List<LiveStream>>(emptyList())
    
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
    private var vodInfoJob: Job? = null

    var channelEpgList by mutableStateOf<List<EpgEntity>>(emptyList())
    var isFetchingChannelEpg by mutableStateOf(false)

    var isPlaying by mutableStateOf(false)
    var playbackPosition by mutableLongStateOf(0L)
    var playbackDuration by mutableLongStateOf(0L)
    private var playbackProgressJob: Job? = null

    var currentTime by mutableLongStateOf(System.currentTimeMillis())
        private set

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

    private var categoryLoadJob: Job? = null
    private var syncJob: Job? = null

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
                val hasCategories = withContext(Dispatchers.IO) { dao.getCategoriesByType("live").isNotEmpty() }
                
                if (!hasCategories) {
                    isLoading = true
                    backgroundStatus = getApplication<Application>().getString(R.string.loading)
                    
                    val apiService = ApiClient.createService(data.url)
                    val catResp = apiService.getLiveCategories(data.username, data.password)
                    if (catResp.isSuccessful) {
                        val apiCategories = catResp.body() ?: emptyList()
                        val mappedCategories = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
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
                                channels = apiStreams.sortedBy { it.num ?: it.streamId }
                                if (currentChannel == null && channels.isNotEmpty()) playChannel(channels.first())
                            }
                        }
                    }
                }
                
                onChannelsReady()
                isLoading = false
                backgroundStatus = null
                
                if (isSyncNeeded()) {
                    startGentleBackgroundSync()
                } else {
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
                val catResp = apiService.getLiveCategories(data.username, data.password)
                if (catResp.isSuccessful) {
                    val apiCategories = catResp.body() ?: emptyList()
                    dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                    
                    if (currentMode == AppMode.LIVE) {
                        val updatedCats = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                        withContext(Dispatchers.Main) { categories = updatedCats }
                    }

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
                withContext(Dispatchers.Main) { isBackgroundSyncing = true }
                val data = userData ?: return@launch
                val apiService = ApiClient.createService(data.url)
                
                val loadingStr = getApplication<Application>().getString(R.string.loading)
                withContext(Dispatchers.Main) { backgroundSyncMessage = "$loadingStr..." }

                val liveCatResp = apiService.getLiveCategories(data.username, data.password)
                if (liveCatResp.isSuccessful) {
                    val apiCategories = liveCatResp.body() ?: emptyList()
                    dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, "live") })
                    if (currentMode == AppMode.LIVE) {
                        val updatedCats = apiCategories.map { Category(it.id, cleanCategoryName(it.name), "live") }
                        withContext(Dispatchers.Main) { categories = updatedCats }
                    }
                }

                val epgUpdateStr = getApplication<Application>().getString(R.string.updating_epg)
                withContext(Dispatchers.Main) { backgroundSyncMessage = epgUpdateStr }
                fetchEpgFromApi()

                withContext(Dispatchers.Main) { backgroundSyncMessage = "Syncing Categories..." }
                val vCatResp = apiService.getVodCategories(data.username, data.password)
                if (vCatResp.isSuccessful) {
                    dao.insertCategories(vCatResp.body()?.map { CategoryEntity(it.id, it.name, "vod") } ?: emptyList())
                }
                
                val sCatResp = apiService.getSeriesCategories(data.username, data.password)
                if (sCatResp.isSuccessful) {
                    dao.insertCategories(sCatResp.body()?.map { CategoryEntity(it.id, it.name, "series") } ?: emptyList())
                }

                val vodResp = apiService.getVodStreams(data.username, data.password)
                if (vodResp.isSuccessful) {
                    val movies = vodResp.body() ?: emptyList()
                    if (movies.isNotEmpty()) {
                        val chunks = movies.chunked(100)
                        chunks.forEachIndexed { index, chunk ->
                            val progress = ((index + 1) * 100 / chunks.size).coerceAtMost(100)
                            withContext(Dispatchers.Main) { backgroundSyncMessage = "Syncing VOD: $progress%" }
                            dao.insertVod(chunk.map { 
                                VodEntity(
                                    it.streamId, it.name, it.streamIcon, it.categoryId ?: "0", 
                                    it.rating, it.containerExtension, it.added,
                                    it.plot, it.cast, it.director, it.genre, it.releaseDate
                                )
                            })
                            delay(200)
                        }
                    }
                }

                val seriesResp = apiService.getSeries(data.username, data.password)
                if (seriesResp.isSuccessful) {
                    val apiSeriesList = seriesResp.body() ?: emptyList()
                    if (apiSeriesList.isNotEmpty()) {
                        val chunks = apiSeriesList.chunked(100)
                        chunks.forEachIndexed { index, chunk ->
                            val progress = ((index + 1) * 100 / chunks.size).coerceAtMost(100)
                            withContext(Dispatchers.Main) { backgroundSyncMessage = "Syncing Series: $progress%" }
                            dao.insertSeries(chunk.map { 
                                SeriesEntity(it.seriesId, it.name, it.cover, it.categoryId ?: "0", it.rating, it.plot, it.genre, it.releaseDate)
                            })
                            delay(200)
                        }
                    }
                }

                prefs.lastFullSync = System.currentTimeMillis()
                refreshCategoryMaps()
                withContext(Dispatchers.Main) { backgroundSyncMessage = "Update Complete!" }
                delay(3000)
            } catch (e: CancellationException) {
                // Silently handle
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isBackgroundSyncing = false
                    backgroundSyncMessage = null
                }
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
            .setBufferDurationsMs(30_000, 60_000, 2_500, 5_000)
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
            .setUserAgent("TiviMate")
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

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MainViewModel.isPlaying = playing
                    if (playing) startPlaybackProgressLoop() else stopPlaybackProgressLoop()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        playbackDuration = duration.coerceAtLeast(0L)
                    } else if (state == Player.STATE_ENDED) {
                        if (activePlaybackMode == AppMode.SERIES) handleSeriesEnded()
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
        
        loadInitialData()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val savedMappings = dao.getAllMappings()
                val mappingMap = savedMappings.associate { it.displayName to it.channelId }
                val normalizedMap = mappingMap.entries.associate { (displayName, id) ->
                    normalizeName(displayName) to id
                }
                
                withContext(Dispatchers.Main) {
                    channelIdMap = mappingMap
                    normalizedChannelIdMap = normalizedMap
                    lastEpgUpdate = dao.getLastUpdatedTime()
                    refreshEpgMap()
                }
            }
        }
        
        viewModelScope.launch {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
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
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                dao.deleteOldEpg(now - (24 * 3600 * 1000))
            }
            fetchEpgFromApi()
        }
    }

    fun refreshVodAndSeries() {
        if (userData == null) return
        viewModelScope.launch {
            isLoading = true
            try {
                syncJob?.cancel()
                startGentleBackgroundSync()
                
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

    fun forceUpdateChannels() {
        if (userData == null) return
        viewModelScope.launch {
            isLoading = true
            try {
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
                    response.body()?.byteStream()?.use { inputStream ->
                        val batch = mutableListOf<EpgEntity>()
                        val result = XmlEpgParser.parse(inputStream) { program ->
                            batch.add(program)
                            if (batch.size >= 2000) {
                                val currentBatch = batch.toList()
                                batch.clear()
                                dao.insertEpgData(currentBatch)
                            }
                        }
                        
                        if (batch.isNotEmpty()) dao.insertEpgData(batch)

                        withContext(Dispatchers.Main) {
                            updateChannelMappings(result.channelMap)
                            val mappingEntities = result.channelMap.map { ChannelMappingEntity(it.key, it.value) }
                            withContext(Dispatchers.IO) {
                                mappingEntities.chunked(500).forEach { dao.insertMappings(it) }
                            }
                            lastEpgUpdate = System.currentTimeMillis()
                            refreshEpgMap()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching EPG", e)
            } finally {
                withContext(Dispatchers.Main) { isEpgUpdating = false }
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
                val allCandidateIds = mutableSetOf<String>()
                val channelCandidatesMap = mutableMapOf<Int, List<String>>()

                currentChannels.forEach { stream ->
                    val candidates = mutableListOf<String>()
                    if (!stream.epgChannelId.isNullOrBlank() && stream.epgChannelId != "null") candidates.add(stream.epgChannelId)
                    channelIdMap[stream.name]?.let { candidates.add(it) }
                    normalizedChannelIdMap[normalizeName(stream.name)]?.let { candidates.add(it) }
                    
                    val uniqueCandidates = candidates.distinct()
                    channelCandidatesMap[stream.streamId] = uniqueCandidates
                    allCandidateIds.addAll(uniqueCandidates)
                }

                val programs = if (allCandidateIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        allCandidateIds.toList().chunked(500).flatMap { chunk ->
                            dao.getUpcomingEpgForChannels(chunk, currentTimeMs)
                        }
                    }
                } else emptyList()
                
                val groupedPrograms = programs.groupBy { it.channelId }
                val currentProgramLookup = mutableMapOf<String, EpgEntity>()
                val nextProgramLookup = mutableMapOf<String, EpgEntity>()

                groupedPrograms.forEach { (channelId, channelPrograms) ->
                    val current = channelPrograms.find { it.start <= currentTimeMs && it.stop >= currentTimeMs }
                    val next = if (current != null) channelPrograms.find { it.start >= current.stop } else channelPrograms.firstOrNull()
                    
                    if (current != null) currentProgramLookup[channelId] = current
                    if (next != null) nextProgramLookup[channelId] = next
                }

                val newEpgMap = mutableMapOf<Int, EpgEntity>()
                val newNextEpgMap = mutableMapOf<Int, EpgEntity>()
                currentChannels.forEach { stream ->
                    val candidates = channelCandidatesMap[stream.streamId] ?: emptyList()
                    for (id in candidates) {
                        val currentMatch = currentProgramLookup[id]
                        if (currentMatch != null) newEpgMap[stream.streamId] = currentMatch
                        val nextMatch = nextProgramLookup[id]
                        if (nextMatch != null) newNextEpgMap[stream.streamId] = nextMatch
                        if (currentMatch != null || nextMatch != null) break
                    }
                }
                
                withContext(Dispatchers.Main) {
                    epgMap = newEpgMap
                    nextEpgMap = newNextEpgMap
                }
            } finally {
                isMappingEpg = false
            }
        }
    }

    fun changeMode(mode: AppMode) {
        val oldMode = currentMode
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
                val hasHistory = withContext(Dispatchers.IO) { dao.getHistoryByType(type).isNotEmpty() }
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
        if (lastId == -1) return
        activePlaybackMode = AppMode.LIVE

        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getStreamById(lastId)
            withContext(Dispatchers.Main) {
                if (entity != null) {
                    playChannel(LiveStream(entity.streamId, entity.name, entity.streamIcon, entity.categoryId, entity.num, entity.epgChannelId))
                } else if (channels.isNotEmpty()) {
                    playChannel(channels.first())
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
            
            val cachedCategories = withContext(Dispatchers.IO) {
                dao.getCategoriesByType(type).map { Category(it.id, cleanCategoryName(it.name), it.type) }
            }.toMutableList()

            if (currentMode == AppMode.VOD || currentMode == AppMode.SERIES) {
                val hasHistory = withContext(Dispatchers.IO) { dao.getHistoryByType(type).isNotEmpty() }
                if (hasHistory) {
                    cachedCategories.add(0, Category("history", getApplication<Application>().getString(R.string.history), type))
                }
            }

            if (cachedCategories.isNotEmpty()) {
                categories = cachedCategories
                selectCategory(categories.first(), forceRefresh = false)
            } else {
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
                    val finalCategories = apiCategories.map { it.copy(type = type, name = cleanCategoryName(it.name)) }.toMutableList()
                    
                    if (currentMode == AppMode.VOD || currentMode == AppMode.SERIES) {
                        val hasHistory = withContext(Dispatchers.IO) { dao.getHistoryByType(type).isNotEmpty() }
                        if (hasHistory) {
                            finalCategories.add(0, Category("history", getApplication<Application>().getString(R.string.history), type))
                        }
                    }
                    categories = finalCategories
                    withContext(Dispatchers.IO) {
                        dao.insertCategories(apiCategories.map { CategoryEntity(it.id, it.name, type) })
                    }
                    refreshCategoryMaps()
                    if (categories.isNotEmpty()) selectCategory(categories.first(), forceRefresh = true)
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
                            channels = cachedStreams.sortedBy { it.num ?: it.streamId }
                            refreshEpgMap()
                            if (currentChannel == null && channels.isNotEmpty()) {
                                val lastId = prefs.lastChannelId
                                val lastChannel = channels.find { it.streamId == lastId }
                                if (lastChannel != null) playChannel(lastChannel)
                                else if (category.id != "history") playChannel(channels.first())
                            }
                        } else fetchLiveStreams(category.id)
                    }
                    AppMode.VOD -> {
                        if (category.id == "history") {
                            vodMovies = withContext(Dispatchers.IO) {
                                dao.getHistoryByType("vod").map {
                                    VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, null, null, it.plot, null, null, it.genre, it.releaseDate)
                                }
                            }
                        } else {
                            val cachedVod = withContext(Dispatchers.IO) { dao.getVodByCategory(category.id) }
                            if (cachedVod.isNotEmpty() && !forceRefresh) {
                                vodMovies = cachedVod.map {
                                    VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.added, it.containerExtension, it.plot, it.cast, it.director, it.genre, it.releaseDate)
                                }.sortedByDescending { it.streamId }
                            } else fetchVodMovies(category.id)
                        }
                    }
                    AppMode.SERIES -> {
                        if (category.id == "history") {
                            seriesList = withContext(Dispatchers.IO) {
                                dao.getHistoryByType("series").distinctBy { it.seriesId ?: it.streamId }.map {
                                    Series(it.seriesId ?: it.streamId, it.name, it.streamIcon, it.plot, null, null, it.genre, it.releaseDate, it.rating, it.categoryId)
                                }
                            }
                        } else {
                            val cachedSeries = withContext(Dispatchers.IO) { dao.getSeriesByCategory(category.id) }
                            if (cachedSeries.isNotEmpty() && !forceRefresh) {
                                seriesList = cachedSeries.map {
                                    Series(it.seriesId, it.name, it.cover, it.plot, null, null, it.genre, it.releaseDate, it.rating, it.categoryId)
                                }.sortedByDescending { it.seriesId }
                            } else fetchSeries(category.id)
                        }
                    }
                }
            } catch (e: CancellationException) {
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
                    channels = apiStreams.sortedBy { it.num ?: it.streamId }
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
                            VodEntity(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.containerExtension, it.added, it.plot, it.cast, it.director, it.genre, it.releaseDate)
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
            selectedMovieHistory = withContext(Dispatchers.IO) { dao.getHistoryById(movie.streamId) }
        }

        if (movie.plot != null && movie.genre != null) return
        
        val data = userData ?: return
        vodInfoJob?.cancel()
        vodInfoJob = viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getVodInfo(data.username, data.password, movie.streamId)
                if (response.isSuccessful) {
                    response.body()?.info?.let { info ->
                        currentVodPlot = info.plot ?: currentVodPlot
                        currentVodGenre = info.genre ?: currentVodGenre
                        currentVodRating = info.rating ?: currentVodRating
                        currentVodReleaseDate = info.releaseDate ?: currentVodReleaseDate
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playVod(movie: VodMovie, fromStart: Boolean = false, skipResumeDialog: Boolean = false) {
        val data = userData ?: return
        pendingMovie = movie
        activePlaybackMode = AppMode.VOD
        currentSeriesId = null
        currentChannel = null
        
        viewModelScope.launch {
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
            
            val mediaItem = MediaItem.Builder().setUri(movieUrl).setMediaId(movie.streamId.toString()).build()

            player?.let { p ->
                p.setMediaItem(mediaItem)
                p.playWhenReady = true
                p.prepare()
                if (fromStart) p.seekTo(0) else if (history != null) p.seekTo(history.position)
            }
        }
    }

    fun loadSeriesDetails(series: Series) {
        val data = userData ?: return
        selectedSeries = series
        isSeriesLoading = true
        lastWatchedEpisode = null
        viewModelScope.launch {
            try {
                val apiService = ApiClient.createService(data.url)
                val response = apiService.getSeriesInfo(data.username, data.password, series.seriesId)
                if (response.isSuccessful) {
                    val details = response.body()
                    seriesDetails = details
                    val history = withContext(Dispatchers.IO) { dao.getLastHistoryBySeriesId(series.seriesId) }
                    if (history != null && details?.episodes != null) {
                        lastWatchedEpisode = withContext(Dispatchers.Default) {
                            details.episodes.values.flatten().find { it.id?.toIntOrNull() == history.streamId }
                        }
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
        activePlaybackMode = AppMode.SERIES
        currentSeriesId = selectedSeries?.seriesId
        pendingEpisode = episode
        lastWatchedEpisode = episode
        currentChannel = null
        
        player?.stop()
        player?.clearMediaItems()
        
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
                
                val mediaItem = MediaItem.Builder().setUri(episodeUrl).setMediaId(streamId.toString()).build()

                player?.let { p ->
                    p.setMediaItem(mediaItem)
                    p.playWhenReady = true
                    p.prepare()
                    if (fromStart) p.seekTo(0) else if (history != null) p.seekTo(history.position)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing episode: ${e.message}", e)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playChannel(stream: LiveStream) {
        if (currentChannel?.streamId == stream.streamId && player?.playbackState != Player.STATE_IDLE) return
        val data = userData ?: return
        currentChannel = stream
        activePlaybackMode = AppMode.LIVE
        currentSeriesId = null
        prefs.lastChannelId = stream.streamId

        viewModelScope.launch(Dispatchers.IO) {
            dao.insertHistory(
                PlaybackHistoryEntity(
                    streamId = stream.streamId, name = stream.name, streamIcon = stream.streamIcon,
                    categoryId = stream.categoryId, type = "live", position = 0, duration = 0,
                    lastWatched = System.currentTimeMillis(), plot = null, genre = null, releaseDate = null, rating = null
                )
            )
            loadRecentChannels()
        }
        
        player?.stop()
        player?.clearMediaItems()
        
        val baseUrl = data.url.removeSuffix("/")
        val streamUrl = "$baseUrl/live/${data.username}/${data.password}/${stream.streamId}.ts"
        val mediaItem = MediaItem.Builder().setUri(streamUrl).build()
        
        player?.let { p ->
            p.setMediaItem(mediaItem)
            p.playWhenReady = true
            p.prepare()
        }
    }

    fun selectSubtitle(track: TrackInfo?) {
        player?.let { p ->
            val builder = p.trackSelectionParameters.buildUpon()
            if (track == null) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                selectedSubtitleId = null
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                       .setOverrideForType(TrackSelectionOverride(p.currentTracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                selectedSubtitleId = track.id
            }
            p.trackSelectionParameters = builder.build()
        }
    }

    fun selectAudio(track: TrackInfo) {
        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(p.currentTracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
                .build()
            selectedAudioId = track.id
        }
    }

    fun getPlayer(): ExoPlayer? = player

    private var lastSavedTime = 0L
    private fun startPlaybackProgressLoop() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (isActive && isPlaying) {
                player?.let { p ->
                    playbackPosition = p.currentPosition.coerceAtLeast(0L)
                    playbackDuration = p.duration.coerceAtLeast(0L)
                    
                    val now = System.currentTimeMillis()
                    if (now - lastSavedTime >= 5000L) {
                        if (activePlaybackMode == AppMode.VOD || activePlaybackMode == AppMode.SERIES) {
                            saveProgress(p.currentPosition, p.duration)
                            lastSavedTime = now
                        }
                    }
                }
                delay(1000)
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
            val isNearEnd = position > (duration - 10_000) || position > (duration * 0.97)
            if (isNearEnd) {
                dao.deleteHistory(movie.streamId)
            } else if (position > 10_000) {
                dao.insertHistory(
                    PlaybackHistoryEntity(
                        streamId = movie.streamId, name = movie.name, streamIcon = movie.streamIcon,
                        categoryId = movie.categoryId, type = if (activePlaybackMode == AppMode.VOD) "vod" else "series",
                        position = position, duration = duration, seriesId = if (activePlaybackMode == AppMode.SERIES) currentSeriesId else null,
                        plot = movie.plot, genre = movie.genre, releaseDate = movie.releaseDate, rating = movie.rating
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
                if (!stream.epgChannelId.isNullOrBlank() && stream.epgChannelId != "null") candidates.add(stream.epgChannelId)
                channelIdMap[stream.name]?.let { candidates.add(it) }
                normalizedChannelIdMap[normalizeName(stream.name)]?.let { candidates.add(it) }
                
                val epgId = candidates.distinct().firstOrNull()
                val epg = if (epgId != null) {
                    val now = System.currentTimeMillis()
                    dao.getEpgForChannel(epgId, now - 3600_000).filter { it.start < now + (12 * 3600 * 1000) }
                } else emptyList()
                
                withContext(Dispatchers.Main) { channelEpgList = epg }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading channel EPG", e)
            } finally {
                isFetchingChannelEpg = false
            }
        }
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    @OptIn(UnstableApi::class)
    fun seekTo(position: Long, smooth: Boolean = false) {
        player?.let {
            val targetPosition = position.coerceIn(0L, it.duration)
            it.setSeekParameters(if (smooth) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
            it.seekTo(targetPosition)
            playbackPosition = targetPosition
        }
    }

    fun skipForward() { player?.let { it.seekTo(it.currentPosition + 10_000) } }
    fun skipBackward() { player?.let { it.seekTo(it.currentPosition - 10_000) } }

    private var searchJob: Job? = null
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
            delay(300)
            val lowerQuery = query.lowercase()
            val vodResults = dao.searchVod("%$lowerQuery%")
            val seriesResults = dao.searchSeries("%$lowerQuery%")
            
            val mappedVod = vodResults.map { 
                VodMovie(it.streamId, it.name, it.streamIcon, it.categoryId, it.rating, it.added, it.containerExtension, it.plot, it.cast, it.director, it.genre, it.releaseDate)
            }
            val mappedSeries = seriesResults.map {
                Series(it.seriesId, it.name, it.cover, it.plot, null, null, null, null, it.rating, it.categoryId)
            }

            withContext(Dispatchers.Main) {
                filteredVod = mappedVod
                filteredSeries = mappedSeries
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
            withContext(Dispatchers.Main) { searchHistory = history }
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
                val service = ApiClient.createGithubService()
                val response = service.getLatestRelease("macseger", "XTR-Tv")
                if (response.isSuccessful) {
                    response.body()?.let { release ->
                        val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
                        val currentVersion = (pInfo.versionName ?: "0.0.0").removePrefix("v")
                        val githubVersion = (release.tag_name ?: "0.0.0").removePrefix("v")
                        if (githubVersion != currentVersion) {
                            latestRelease = release
                            updateStatus = getApplication<Application>().getString(R.string.update_available)
                        } else updateStatus = getApplication<Application>().getString(R.string.update_not_available)
                    }
                }
            } catch (e: Exception) {
                updateStatus = "Error: ${e.message}"
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    private fun handleSeriesEnded() {
        val current = pendingEpisode ?: return
        val details = seriesDetails ?: return
        var foundNext = false
        details.episodes?.values?.flatten()?.let { allEpisodes ->
            val currentIndex = allEpisodes.indexOfFirst { it.id == current.id }
            if (currentIndex != -1 && currentIndex < allEpisodes.size - 1) {
                nextEpisode = allEpisodes[currentIndex + 1]
                foundNext = true
            }
        }
        if (foundNext) showNextEpisodeDialog = true
        else viewModelScope.launch(Dispatchers.Main) { changeMode(activePlaybackMode) }
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
            val builder = (p.trackSelectionParameters as? DefaultTrackSelector.Parameters)?.buildUpon()
                ?: DefaultTrackSelector.Parameters.Builder(context)
            p.trackSelectionParameters = builder.setTunnelingEnabled(isTunnelingEnabled).build()
            currentChannel?.let { playChannel(it) }
        }
    }

    @OptIn(UnstableApi::class)
    fun toggleFrameRateMatching() {
        isFrameRateMatchingEnabled = !isFrameRateMatchingEnabled
        prefs.isFrameRateMatchingEnabled = isFrameRateMatchingEnabled
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
            val mapped = history.map { LiveStream(it.streamId, it.name, it.streamIcon, it.categoryId ?: "0", null, null) }
            withContext(Dispatchers.Main) { recentChannels = mapped }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                player?.stop()
                withContext(Dispatchers.IO) { db.clearAllTables() }
                prefs.clear()
                onComplete()
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
        prefs.lastFullSync = 0L
        forceUpdateChannels()
        refreshVodAndSeries()
        refreshEpg()
    }

    fun isSyncNeeded(): Boolean {
        val lastSync = prefs.lastFullSync
        val now = System.currentTimeMillis()
        return lastSync == 0L || (now - lastSync) > 24 * 3600 * 1000L
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}
