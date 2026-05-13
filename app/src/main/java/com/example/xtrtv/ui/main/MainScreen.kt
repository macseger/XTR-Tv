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
import com.example.xtrtv.data.UserData
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
    var focusPlayingNow by remember { mutableStateOf(true) }
    
    // Derived state for better performance - avoids full recomposition on every sub-state change
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
        if (showChannelList && !showContextMenu && !showSeriesDetails && !showSearchOverlay) {
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
            enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkHorizontally(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A).copy(alpha = 0.85f))
                ) {
                    // Left Navigation Rail
                    Column(
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .focusProperties {
                                exit = { focusDirection ->
                                    if (focusDirection == FocusDirection.Right) categoryFocusRequester else FocusRequester.Default
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val modes = listOf(
                            Triple(MainViewModel.AppMode.LIVE, Icons.Default.Tv, stringResource(R.string.live)),
                            Triple(MainViewModel.AppMode.VOD, Icons.Default.Movie, stringResource(R.string.movies)),
                            Triple(MainViewModel.AppMode.SERIES, Icons.Default.VideoLibrary, stringResource(R.string.series))
                        )

                        modes.forEachIndexed { index, (mode, icon, label) ->
                            val isSelected = viewModel.currentMode == mode
                            Surface(
                                onClick = { 
                                    viewModel.changeMode(mode)
                                    if (mode == MainViewModel.AppMode.LIVE) {
                                        showChannelList = false
                                    }
                                    focusPlayingNow = false
                                    focusContentTrigger++
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(vertical = 4.dp)
                                    .then(if (isSelected) Modifier.focusRequester(railFocusRequester) else Modifier),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Turquoise.copy(alpha = 0.2f) else Color.Transparent,
                                    focusedContainerColor = Turquoise,
                                    contentColor = if (isSelected) Turquoise else Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Search
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            onClick = { 
                                viewModel.performSearch("")
                                showSearchOverlay = true 
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .padding(vertical = 4.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Turquoise,
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.size(24.dp))
                                Text(stringResource(R.string.search), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Settings
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            onClick = { 
                                showSubtitleMenu = false
                                showContextMenu = true 
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .padding(vertical = 4.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Turquoise,
                                contentColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(24.dp))
                                Text(stringResource(R.string.settings_short), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Categories
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = stringResource(R.string.categories),
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            letterSpacing = 2.sp
                        )
                        val hasContent = when (viewModel.currentMode) {
                            MainViewModel.AppMode.LIVE -> viewModel.channels.isNotEmpty()
                            MainViewModel.AppMode.VOD -> viewModel.vodMovies.isNotEmpty()
                            MainViewModel.AppMode.SERIES -> viewModel.seriesList.isNotEmpty()
                        }
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = categoryListState
                        ) {
                            items(viewModel.categories, key = { it.id }) { category ->
                                val isSelected = viewModel.selectedCategory?.id == category.id
                                Surface(
                                    onClick = { 
                                        // Still keep click for manual trigger if needed
                                        viewModel.selectCategory(category)
                                        focusPlayingNow = false
                                        focusContentTrigger++
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .onFocusChanged { 
                                            if (it.isFocused && !isSelected) {
                                                viewModel.selectCategory(category)
                                            }
                                        }
                                        .focusProperties {
                                            exit = { dir ->
                                                if (dir == FocusDirection.Right) {
                                                    contentFocusRequester
                                                } else if (dir == FocusDirection.Left) {
                                                    railFocusRequester
                                                } else {
                                                    FocusRequester.Default
                                                }
                                            }
                                        }
                                        .then(if (isSelected) Modifier.focusRequester(categoryFocusRequester) else Modifier),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSelected) Color(0xFF2A2A2A) else Color.Transparent,
                                        focusedContainerColor = Turquoise,
                                        contentColor = if (isSelected) Turquoise else Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small)
                                ) {
                                    Text(
                                        text = category.name,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.DarkGray))

                    Column(
                        modifier = Modifier
                            .weight(3f)
                            .focusProperties {
                                exit = { focusDirection ->
                                    if (focusDirection == FocusDirection.Left) categoryFocusRequester else FocusRequester.Default
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = viewModel.selectedCategory?.name ?: stringResource(R.string.content),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                                    viewModel.lastEpgUpdate?.let { lastUpdate ->
                                        val timeStr = remember(lastUpdate) {
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdate))
                                        }
                                        Text(
                                            text = stringResource(R.string.updated_at, timeStr),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            TopRightClock()
                        }

                        if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                state = channelListState
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
                                            .then(
                                                if (focusPlayingNow && playingIndex >= 0) {
                                                    if (index == playingIndex) Modifier.focusRequester(contentFocusRequester) else Modifier
                                                } else {
                                                    if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier
                                                }
                                            ),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isPlaying) Color(0xFF1E1E1E) else Color.Transparent,
                                            focusedContainerColor = Color(0xFF2A2A2A),
                                            contentColor = if (isPlaying) Turquoise else Color.White,
                                            focusedContentColor = Turquoise
                                        ),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Picon
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(stream.streamIcon)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .padding(end = 12.dp)
                                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                            )

                                            Column(modifier = Modifier.weight(1.2f)) {
                                                Text(
                                                    text = stream.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            if (epgEntry != null) {
                                                Column(
                                                    modifier = Modifier.weight(2f),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = epgEntry.title,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    
                                                    val timeRange = remember(epgEntry) {
                                                        "${timeFormat.format(Date(epgEntry.start))} - ${timeFormat.format(Date(epgEntry.stop))}"
                                                    }
                                                    
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = timeRange,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = LocalContentColor.current.copy(alpha = 0.6f)
                                                        )
                                                        
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    state = vodGridState,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                                modifier = if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier,
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
                                                modifier = if (index == 0) Modifier.focusRequester(contentFocusRequester) else Modifier,
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
