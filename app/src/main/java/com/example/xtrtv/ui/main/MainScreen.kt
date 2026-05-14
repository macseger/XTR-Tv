package com.example.xtrtv.ui.main

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.xtrtv.R
import com.example.xtrtv.data.*
import com.example.xtrtv.api.*
import com.example.xtrtv.ui.components.*
import com.example.xtrtv.utils.UpdateManager
import com.example.xtrtv.ui.theme.Turquoise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    userData: UserData,
    viewModel: MainViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var showChannelList by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSeriesDetails by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showPlaybackControls by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var focusContentTrigger by remember { mutableIntStateOf(0) }
    var focusCategoryTrigger by remember { mutableIntStateOf(0) }
    var focusPlayingNow by remember { mutableStateOf(true) }
    
    var focusedChannel by remember { mutableStateOf<LiveStream?>(null) }
    var focusedMovie by remember { mutableStateOf<VodMovie?>(null) }
    var focusedSeries by remember { mutableStateOf<Series?>(null) }
    val isAnyOverlayVisible by remember {
        derivedStateOf {
            showChannelList || showContextMenu || showPlaybackControls || 
            showSeriesDetails || showSearchOverlay || showExitDialog || 
            showLogoutDialog || showUrlDialog || viewModel.showResumeDialog ||
            showAudioMenu || viewModel.showNextEpisodeDialog
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showPlaybackControls, lastInteractionTime, viewModel.isPlaying) {
        if (showPlaybackControls && viewModel.isPlaying) {
            kotlinx.coroutines.delay(5000)
            showPlaybackControls = false
        }
    }
    
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val vodGridState = rememberLazyGridState()
    
    val rootFocusRequester = remember { FocusRequester() }
    val railFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val contextMenuFocusRequester = remember { FocusRequester() }
    val playbackFocusRequester = remember { FocusRequester() }

    val playingIndex = remember(viewModel.channels, viewModel.currentChannel) {
        viewModel.channels.indexOfFirst { it.streamId == viewModel.currentChannel?.streamId }
    }

    LaunchedEffect(userData) {
        viewModel.init(context, userData)
        
        if (viewModel.isSyncNeeded()) {
            viewModel.initialSync(onChannelsReady = {
                showChannelList = true
            })
        } else {
            showChannelList = true
        }
    }

    LaunchedEffect(showChannelList) {
        if (showChannelList) {
            if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                focusedChannel = viewModel.currentChannel
            }
            
            if (!showContextMenu && !showSeriesDetails && !showSearchOverlay) {
                focusPlayingNow = true
                kotlinx.coroutines.yield()
                
                if (viewModel.currentMode == MainViewModel.AppMode.LIVE && playingIndex >= 0) {
                    try {
                        channelListState.scrollToItem(playingIndex)
                        contentFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        railFocusRequester.requestFocus()
                    }
                } else {
                    // Always start focus on the rail for better TV UX predictability
                    railFocusRequester.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(showSeriesDetails, showSearchOverlay) {
        if (!showSeriesDetails && !showSearchOverlay && showChannelList) {
            try {
                contentFocusRequester.requestFocus()
            } catch (e: Exception) {
                railFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(isAnyOverlayVisible) {
        if (!isAnyOverlayVisible) {
            rootFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(focusContentTrigger) {
        if (focusContentTrigger > 0 && showChannelList) {
            try {
                // Wait for the list to be non-empty with a shorter timeout and more frequent checks
                kotlinx.coroutines.withTimeout(2000) {
                    while (true) {
                        val isNotEmpty = when (viewModel.currentMode) {
                            MainViewModel.AppMode.LIVE -> viewModel.channels.isNotEmpty()
                            MainViewModel.AppMode.VOD -> viewModel.vodMovies.isNotEmpty()
                            MainViewModel.AppMode.SERIES -> viewModel.seriesList.isNotEmpty()
                        }
                        if (isNotEmpty) break
                        kotlinx.coroutines.delay(50)
                    }
                }
            } catch (e: Exception) {
                return@LaunchedEffect
            }

            // Small delay to allow composition to catch up
            kotlinx.coroutines.delay(100)

            try {
                if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                    channelListState.scrollToItem(0)
                } else {
                    vodGridState.scrollToItem(0)
                }
                
                // Try requesting focus multiple times as composition might be in progress
                repeat(5) {
                    try {
                        contentFocusRequester.requestFocus()
                        return@repeat
                    } catch (e: Exception) {
                        kotlinx.coroutines.delay(50)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainScreen", "Failed to focus content", e)
            }
        }
    }

    LaunchedEffect(focusCategoryTrigger) {
        if (focusCategoryTrigger > 0 && showChannelList) {
            try {
                // Wait for categories to be available
                kotlinx.coroutines.withTimeoutOrNull(2000) {
                    while (viewModel.categories.isEmpty()) {
                        kotlinx.coroutines.delay(50)
                    }
                }
                kotlinx.coroutines.delay(100)
                categoryListState.scrollToItem(0)
                categoryFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("MainScreen", "Failed to focus category", e)
            }
        }
    }

    LaunchedEffect(showContextMenu) {
        if (showContextMenu) {
            contextMenuFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = true) {
        when {
            showUrlDialog -> showUrlDialog = false
            showLogoutDialog -> showLogoutDialog = false
            showExitDialog -> showExitDialog = false
            viewModel.showResumeDialog -> viewModel.showResumeDialog = false
            showSearchOverlay -> showSearchOverlay = false
            showSeriesDetails -> showSeriesDetails = false
            showContextMenu -> {
                showSubtitleMenu = false
                showAudioMenu = false
                showContextMenu = false
            }
            showPlaybackControls -> showPlaybackControls = false
            showChannelList -> showChannelList = false
            else -> {
                if (viewModel.activePlaybackMode == MainViewModel.AppMode.LIVE) {
                    showExitDialog = true
                } else {
                    viewModel.changeMode(viewModel.activePlaybackMode)
                    showChannelList = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                // Global interaction tracking
                if (event.type == KeyEventType.KeyUp) {
                    lastInteractionTime = System.currentTimeMillis()
                }

                if (!isAnyOverlayVisible) {
                    if (event.type == KeyEventType.KeyDown && 
                        (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
                         event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                        if (event.nativeKeyEvent.isLongPress) {
                            showContextMenu = true
                            return@onKeyEvent true
                        }
                    }

                    if (event.type == KeyEventType.KeyUp) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                if (viewModel.activePlaybackMode == MainViewModel.AppMode.LIVE) {
                                    viewModel.changeMode(MainViewModel.AppMode.LIVE)
                                    showChannelList = true
                                } else {
                                    // Toggle play/pause and show overlay
                                    viewModel.togglePlayPause()
                                    showPlaybackControls = true
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                showSubtitleMenu = true
                                showContextMenu = true
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                showSubtitleMenu = false
                                showContextMenu = true
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (viewModel.activePlaybackMode != MainViewModel.AppMode.LIVE) {
                                    viewModel.skipBackward()
                                    showPlaybackControls = true
                                    true
                                } else false
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (viewModel.activePlaybackMode != MainViewModel.AppMode.LIVE) {
                                    viewModel.skipForward()
                                    showPlaybackControls = true
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                } else if (showPlaybackControls && event.type == KeyEventType.KeyUp) {
                    lastInteractionTime = System.currentTimeMillis()
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            showPlaybackControls = false
                            showSubtitleMenu = true
                            showContextMenu = true
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showPlaybackControls = false
                            showSubtitleMenu = false
                            showContextMenu = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. Player (Background)
        viewModel.getPlayer()?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        @OptIn(UnstableApi::class)
                        useController = false
                        this.player = player
                        focusable = android.view.View.NOT_FOCUSABLE
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
            )
        }

        // Invisible click handler for touch/mouse
        if (!showChannelList && !showContextMenu && !showPlaybackControls && !showSeriesDetails) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                            showChannelList = true
                        } else {
                            showPlaybackControls = true
                        }
                    }
            )
        }

        // 3. Playback Controls Overlay
        if (showPlaybackControls && !viewModel.showResumeDialog && (viewModel.activePlaybackMode == MainViewModel.AppMode.VOD || viewModel.activePlaybackMode == MainViewModel.AppMode.SERIES)) {
            val displayTitle = if (viewModel.activePlaybackMode == MainViewModel.AppMode.SERIES) {
                val seriesName = viewModel.selectedSeries?.name ?: ""
                val episode = viewModel.pendingEpisode
                val seasonNum = viewModel.seriesDetails?.episodes?.entries?.find { entry -> 
                    entry.value.any { it.id == episode?.id } 
                }?.key ?: ""
                val episodeNum = episode?.episodeNum ?: ""
                
                if (seriesName.isNotEmpty()) {
                    var title = seriesName
                    if (seasonNum.isNotEmpty()) title += " - ${stringResource(R.string.season_label, seasonNum)}"
                    if (episodeNum.isNotEmpty()) title += " - ${stringResource(R.string.episode_label, episodeNum)}"
                    title
                } else {
                    viewModel.pendingMovie?.name ?: stringResource(R.string.now_playing)
                }
            } else {
                viewModel.pendingMovie?.name ?: stringResource(R.string.now_playing)
            }

            PlaybackControls(
                title = displayTitle,
                isPlaying = viewModel.isPlaying,
                position = viewModel.playbackPosition,
                duration = viewModel.playbackDuration,
                onPlayPause = { 
                    val wasPlaying = viewModel.isPlaying
                    viewModel.togglePlayPause() 
                    if (!wasPlaying) { // If it was paused, it's now playing
                        showPlaybackControls = false
                    }
                },
                onSeek = { pos, smooth -> viewModel.seekTo(pos, smooth) },
                focusRequester = playbackFocusRequester
            )
            LaunchedEffect(Unit) {
                playbackFocusRequester.requestFocus()
            }
        }

        // 3.5 Resume Dialog
        if (viewModel.showResumeDialog) {
            ResumeDialog(
                onResume = { 
                    showPlaybackControls = false
                    if (viewModel.activePlaybackMode == MainViewModel.AppMode.SERIES) {
                        viewModel.pendingEpisode?.let { viewModel.playEpisode(it, fromStart = false) }
                    } else {
                        viewModel.pendingMovie?.let { viewModel.playVod(it, fromStart = false) }
                    }
                },
                onRestart = { 
                    showPlaybackControls = false
                    if (viewModel.activePlaybackMode == MainViewModel.AppMode.SERIES) {
                        viewModel.pendingEpisode?.let { viewModel.playEpisode(it, fromStart = true) }
                    } else {
                        viewModel.pendingMovie?.let { viewModel.playVod(it, fromStart = true) }
                    }
                }
            )
        }

        // 4. Content Navigation Overlay
        AnimatedVisibility(
            visible = showChannelList,
            enter = fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF050505),
                                Color(0xFF0A0A0A).copy(alpha = 0.95f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1800f
                        )
                    )
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 1. Navigation Rail (Sleek & Minimal)
                    Column(
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .focusProperties {
                                exit = { dir -> if (dir == FocusDirection.Right) categoryFocusRequester else FocusRequester.Default }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        val modes = listOf(
                            Triple(MainViewModel.AppMode.LIVE, Icons.Default.Tv, stringResource(R.string.live)),
                            Triple(MainViewModel.AppMode.VOD, Icons.Default.Movie, stringResource(R.string.movies)),
                            Triple(MainViewModel.AppMode.SERIES, Icons.Default.VideoLibrary, stringResource(R.string.series))
                        )

                        modes.forEach { (mode, icon, label) ->
                            val isSelected = viewModel.currentMode == mode
                            Surface(
                                onClick = { 
                                    viewModel.changeMode(mode)
                                    if (mode == MainViewModel.AppMode.LIVE) {
                                        showChannelList = false
                                    } else {
                                        focusCategoryTrigger++
                                    }
                                    focusPlayingNow = false
                                    focusContentTrigger++
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .then(if (isSelected) Modifier.focusRequester(railFocusRequester) else Modifier),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Turquoise.copy(alpha = 0.15f) else Color.Transparent,
                                    focusedContainerColor = Turquoise,
                                    contentColor = if (isSelected) Turquoise else Color.White.copy(alpha = 0.6f),
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
                                }
                            }
                        }

                        Box(modifier = Modifier.height(1.dp).width(30.dp).background(Color.White.copy(alpha = 0.1f)))

                        // Search
                        Surface(
                            onClick = { 
                                viewModel.performSearch("")
                                showSearchOverlay = true 
                            },
                            modifier = Modifier.size(56.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.White.copy(alpha = 0.6f),
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.size(28.dp))
                            }
                        }

                        // Settings
                        Surface(
                            onClick = { 
                                showSubtitleMenu = false
                                showContextMenu = true 
                            },
                            modifier = Modifier.size(56.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.White,
                                contentColor = Color.White.copy(alpha = 0.6f),
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    // 2. Categories (Elegant Sidebar)
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = stringResource(R.string.categories).uppercase(),
                            modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 16.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Turquoise,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = categoryListState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                        ) {
                            items(viewModel.categories, key = { it.id }) { category ->
                                val isSelected = viewModel.selectedCategory?.id == category.id
                                Surface(
                                    onClick = { 
                                        viewModel.selectCategory(category)
                                        focusPlayingNow = false
                                        focusContentTrigger++
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp)
                                        .onFocusChanged { 
                                            if (it.isFocused && !isSelected) {
                                                viewModel.selectCategory(category)
                                            }
                                        }
                                        .focusProperties {
                                            exit = { dir ->
                                                if (dir == FocusDirection.Right) contentFocusRequester
                                                else if (dir == FocusDirection.Left) railFocusRequester
                                                else FocusRequester.Default
                                            }
                                        }
                                        .then(if (isSelected) Modifier.focusRequester(categoryFocusRequester) else Modifier),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSelected) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                                        focusedContainerColor = Color.White,
                                        contentColor = if (isSelected) Turquoise else Color.White.copy(alpha = 0.7f),
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Content Area with Dynamic Info Header
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusProperties {
                                exit = { dir -> if (dir == FocusDirection.Left) categoryFocusRequester else FocusRequester.Default }
                            }
                    ) {
                        // Header / Info Panel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        ) {
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                val itemTitle = when (viewModel.currentMode) {
                                    MainViewModel.AppMode.LIVE -> focusedChannel?.name
                                    MainViewModel.AppMode.VOD -> focusedMovie?.name
                                    MainViewModel.AppMode.SERIES -> focusedSeries?.name
                                } ?: viewModel.selectedCategory?.name ?: ""

                                Text(
                                    text = itemTitle,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (viewModel.currentMode == MainViewModel.AppMode.LIVE && focusedChannel != null) {
                                    val epg = viewModel.epgMap[focusedChannel?.streamId]
                                    if (epg != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = epg.title,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = Turquoise,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                            Text(
                                                text = "${timeFormat.format(Date(epg.start))} - ${timeFormat.format(Date(epg.stop))}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.Gray
                                            )
                                        }
                                        Text(
                                            text = epg.description ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(0.8f)
                                        )
                                    }
                                } else if (viewModel.currentMode == MainViewModel.AppMode.VOD && focusedMovie != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!focusedMovie?.rating.isNullOrBlank() && focusedMovie?.rating != "0") {
                                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(focusedMovie?.rating ?: "", color = Color(0xFFFFD700), style = MaterialTheme.typography.titleMedium)
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }
                                        Text(stringResource(R.string.movies), color = Color.Gray, style = MaterialTheme.typography.labelLarge)
                                        focusedMovie?.containerExtension?.let {
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(it.uppercase(), modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                } else if (viewModel.currentMode == MainViewModel.AppMode.SERIES && focusedSeries != null) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!focusedSeries?.rating.isNullOrBlank() && focusedSeries?.rating != "0") {
                                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(focusedSeries?.rating ?: "", color = Color(0xFFFFD700), style = MaterialTheme.typography.titleMedium)
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            Text(focusedSeries?.genre ?: stringResource(R.string.series), color = Color.Gray, style = MaterialTheme.typography.labelLarge)
                                            focusedSeries?.releaseDate?.let {
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Text(it, color = Color.Gray, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = focusedSeries?.plot ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(0.8f)
                                        )
                                    }
                                }
                            }
                            
                            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                TopRightClock()
                            }
                        }

                        // Content List/Grid
                        if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                state = channelListState,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
                            ) {
                                itemsIndexed(
                                    items = viewModel.channels,
                                    key = { _, stream -> stream.streamId }
                                ) { index, stream ->
                                    val isPlaying = viewModel.currentChannel?.streamId == stream.streamId
                                    val epgEntry = viewModel.epgMap[stream.streamId]

                                    Surface(
                                        onClick = { 
                                            if (!showContextMenu) {
                                                viewModel.playChannel(stream)
                                                showChannelList = false
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .onFocusChanged { if (it.isFocused) focusedChannel = stream }
                                            .then(
                                                if (focusPlayingNow && playingIndex >= 0) {
                                                    if (index == playingIndex) Modifier.focusRequester(contentFocusRequester) else Modifier
                                                } else {
                                                    if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier
                                                }
                                            ),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isPlaying) Turquoise.copy(alpha = 0.05f) else Color.Transparent,
                                            focusedContainerColor = Color.White,
                                            contentColor = if (isPlaying) Turquoise else Color.White,
                                            focusedContentColor = Color.Black
                                        ),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(stream.streamIcon)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .padding(end = 16.dp)
                                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                            )

                                            Column(modifier = Modifier.weight(1.5f)) {
                                                Text(
                                                    text = stream.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (epgEntry != null) {
                                                    Text(
                                                        text = epgEntry.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (isPlaying) Turquoise else LocalContentColor.current.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }

                                            if (epgEntry != null) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    val timeRange = remember(epgEntry) {
                                                        "${timeFormat.format(Date(epgEntry.start))} - ${timeFormat.format(Date(epgEntry.stop))}"
                                                    }
                                                    Text(
                                                        text = timeRange,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = LocalContentColor.current.copy(alpha = 0.5f)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    EpgProgressBar(
                                                        start = epgEntry.start,
                                                        stop = epgEntry.stop,
                                                        isActive = isPlaying,
                                                        timeProvider = { viewModel.currentTime }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if ((viewModel.currentMode == MainViewModel.AppMode.VOD && viewModel.vodMovies.isEmpty()) ||
                                    (viewModel.currentMode == MainViewModel.AppMode.SERIES && viewModel.seriesList.isEmpty())) {
                                    Text(
                                        text = stringResource(R.string.no_data_available),
                                        modifier = Modifier.align(Alignment.Center).focusable(),
                                        color = Color.Gray
                                    )
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(5),
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                    state = vodGridState,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    if (viewModel.currentMode == MainViewModel.AppMode.VOD) {
                                        itemsIndexed(
                                            items = viewModel.vodMovies,
                                            key = { _, movie -> movie.streamId },
                                            contentType = { _, _ -> "movie" }
                                        ) { index, movie ->
                                            VodCard(
                                                title = movie.name,
                                                posterUrl = movie.streamIcon,
                                                rating = movie.rating,
                                                modifier = Modifier
                                                    .onFocusChanged { if (it.isFocused) focusedMovie = movie }
                                                    .then(if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier),
                                                onClick = { 
                                                    showPlaybackControls = false
                                                    viewModel.playVod(movie)
                                                    showChannelList = false
                                                }
                                            )
                                        }
                                    } else {
                                        itemsIndexed(
                                            items = viewModel.seriesList,
                                            key = { _, series -> series.seriesId },
                                            contentType = { _, _ -> "series" }
                                        ) { index, series ->
                                            VodCard(
                                                title = series.name,
                                                posterUrl = series.cover,
                                                rating = series.rating,
                                                modifier = Modifier
                                                    .onFocusChanged { if (it.isFocused) focusedSeries = series }
                                                    .then(if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier),
                                                onClick = { 
                                                    viewModel.loadSeriesDetails(series)
                                                    showSeriesDetails = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlays
        if (showSeriesDetails) {
            SeriesDetailsOverlay(
                viewModel = viewModel,
                onClose = { showSeriesDetails = false },
                onPlayEpisode = { episode ->
                    showPlaybackControls = false
                    viewModel.playEpisode(episode)
                    showSeriesDetails = false
                    showChannelList = false
                }
            )
        }

        if (showSearchOverlay) {
            SearchOverlay(
                viewModel = viewModel,
                onClose = { showSearchOverlay = false },
                onPlayVod = { movie ->
                    showPlaybackControls = false
                    viewModel.playVod(movie)
                    showSearchOverlay = false
                    showChannelList = false
                },
                onOpenSeries = { series ->
                    viewModel.loadSeriesDetails(series)
                    showSeriesDetails = true
                    showSearchOverlay = false
                }
            )
        }

        if (showContextMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .onKeyEvent {
                        if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                            showSubtitleMenu = false
                            showAudioMenu = false
                            showContextMenu = false
                            true
                        } else false
                    }
                    .clickable { 
                        showContextMenu = false
                        showSubtitleMenu = false
                        showAudioMenu = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .background(Color(0xFF121212), shape = RoundedCornerShape(24.dp))
                        .padding(24.dp)
                        .clickable(enabled = false) {}
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val titleIcon = when {
                                showSubtitleMenu -> Icons.Default.Subtitles
                                showAudioMenu -> Icons.Default.Audiotrack
                                else -> Icons.Default.Settings
                            }
                            val titleText = when {
                                showSubtitleMenu -> stringResource(R.string.subtitles)
                                showAudioMenu -> stringResource(R.string.audio_tracks)
                                else -> stringResource(R.string.menu)
                            }
                            Icon(
                                imageVector = titleIcon,
                                contentDescription = null,
                                tint = Turquoise,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (showSubtitleMenu || showAudioMenu) {
                            Surface(
                                onClick = { 
                                    showSubtitleMenu = false
                                    showAudioMenu = false
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp).size(20.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!showSubtitleMenu && !showAudioMenu) {
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.refresh_channels),
                                    icon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp)) },
                                    focusRequester = contextMenuFocusRequester,
                                    onClick = {
                                        viewModel.forceUpdateChannels()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.refresh_epg),
                                    icon = { Icon(Icons.Default.Event, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        viewModel.refreshEpg()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.refresh_vod_series),
                                    icon = { Icon(Icons.Default.MovieFilter, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        viewModel.refreshVodAndSeries()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.subtitles),
                                    icon = { Icon(Icons.Default.Subtitles, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showSubtitleMenu = true
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.audio_tracks),
                                    icon = { Icon(Icons.Default.Audiotrack, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showAudioMenu = true
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.tunneling),
                                    icon = { Icon(Icons.Default.Speed, null, modifier = Modifier.size(20.dp)) },
                                    isSelected = viewModel.isTunnelingEnabled,
                                    onClick = {
                                        viewModel.toggleTunneling()
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.frame_rate_matching),
                                    icon = { Icon(Icons.Default.SlowMotionVideo, null, modifier = Modifier.size(20.dp)) },
                                    isSelected = viewModel.isFrameRateMatchingEnabled,
                                    onClick = {
                                        viewModel.toggleFrameRateMatching()
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.change_server_url),
                                    icon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showUrlDialog = true
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.logout),
                                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showLogoutDialog = true
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                val context = LocalContext.current
                                val updateStatus = viewModel.updateStatus
                                val isChecking = viewModel.isCheckingUpdate
                                
                                ContextMenuItem(
                                    text = updateStatus ?: stringResource(R.string.check_updates),
                                    icon = { 
                                        if (isChecking) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = Turquoise
                                            )
                                        } else {
                                            Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                    onClick = {
                                        if (viewModel.latestRelease != null) {
                                            val apkAsset = viewModel.latestRelease?.assets?.find { it.name.endsWith(".apk") }
                                            if (apkAsset != null) {
                                                val updateManager = UpdateManager(context)
                                                updateManager.downloadAndInstall(apkAsset.browser_download_url, apkAsset.name)
                                                showContextMenu = false
                                            } else {
                                                // Fallback to browser if no APK asset found
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(viewModel.latestRelease?.html_url))
                                                context.startActivity(intent)
                                            }
                                        } else {
                                            viewModel.checkForUpdates()
                                        }
                                    }
                                )
                            }
                        } else if (showSubtitleMenu) {
                            item {
                                ContextMenuItem(
                                    text = stringResource(R.string.off),
                                    icon = { Icon(Icons.Default.SubtitlesOff, null, modifier = Modifier.size(20.dp)) },
                                    isSelected = viewModel.selectedSubtitleId == null,
                                    focusRequester = contextMenuFocusRequester,
                                    onClick = {
                                        viewModel.selectSubtitle(null)
                                        showSubtitleMenu = false
                                        showContextMenu = false
                                    }
                                )
                            }
                            items(viewModel.subtitleTracks) { track ->
                                ContextMenuItem(
                                    text = track.name,
                                    icon = { Icon(Icons.Default.ClosedCaption, null, modifier = Modifier.size(20.dp)) },
                                    isSelected = viewModel.selectedSubtitleId == track.id,
                                    onClick = {
                                        viewModel.selectSubtitle(track)
                                        showSubtitleMenu = false
                                        showContextMenu = false
                                    }
                                )
                            }
                        } else {
                            // Audio Menu
                            itemsIndexed(viewModel.audioTracks) { index, track ->
                                ContextMenuItem(
                                    text = track.name,
                                    icon = { Icon(Icons.Default.Audiotrack, null, modifier = Modifier.size(20.dp)) },
                                    isSelected = viewModel.selectedAudioId == track.id,
                                    focusRequester = if (index == 0) contextMenuFocusRequester else null,
                                    onClick = {
                                        viewModel.selectAudio(track)
                                        showAudioMenu = false
                                        showContextMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.isEpgUpdating || viewModel.isLoading || viewModel.backgroundStatus != null || viewModel.backgroundSyncMessage != null) {
            LoadingIndicator(
                text = viewModel.backgroundSyncMessage ?: viewModel.backgroundStatus ?: if (viewModel.isEpgUpdating) stringResource(R.string.updating_epg) else stringResource(R.string.loading)
            )
        }

        if (showExitDialog) {
            ExitDialog(
                onConfirm = { (context as? android.app.Activity)?.finish() },
                onDismiss = { showExitDialog = false }
            )
        }

        if (showLogoutDialog) {
            LogoutDialog(
                username = userData.username,
                onConfirm = { 
                    viewModel.logout(onLogout) 
                    showLogoutDialog = false
                },
                onDismiss = { showLogoutDialog = false }
            )
        }

        if (viewModel.showNextEpisodeDialog) {
            NextEpisodeDialog(
                onNext = { viewModel.playNextEpisode() },
                onDismiss = { 
                    viewModel.showNextEpisodeDialog = false
                    viewModel.changeMode(MainViewModel.AppMode.SERIES)
                    showSeriesDetails = true
                    showChannelList = true
                }
            )
        }

        if (showUrlDialog) {
            ChangeUrlDialog(
                initialUrl = userData.url,
                onConfirm = { newUrl ->
                    viewModel.updateServerUrl(newUrl)
                    showUrlDialog = false
                },
                onDismiss = { showUrlDialog = false }
            )
        }
    }
}
