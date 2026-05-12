package com.example.xtrtv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.xtrtv.data.UserData
import java.text.SimpleDateFormat
import java.util.*

private val Turquoise = Color(0xFF00CED1)

@OptIn(ExperimentalTvMaterial3Api::class)
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
    var showSeriesDetails by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showPlaybackControls by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Auto-hide controls
    LaunchedEffect(showPlaybackControls, lastInteractionTime) {
        if (showPlaybackControls) {
            kotlinx.coroutines.delay(5000)
            showPlaybackControls = false
        }
    }
    
    // States för att kontrollera scroll
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val vodGridState = rememberLazyGridState()
    
    val rootFocusRequester = remember { FocusRequester() }
    val menuFocusRequester = remember { FocusRequester() }
    val channelFocusRequester = remember { FocusRequester() }
    val contextMenuFocusRequester = remember { FocusRequester() }
    val railFocusRequester = remember { FocusRequester() }
    val playbackFocusRequester = remember { FocusRequester() }

    LaunchedEffect(userData) {
        viewModel.init(context, userData)
    }

    // Manage focus and scroll based on menu visibility
    LaunchedEffect(showChannelList, showContextMenu, showSubtitleMenu, showSeriesDetails, showSearchOverlay, showExitDialog, showLogoutDialog, showUrlDialog, viewModel.showResumeDialog, viewModel.currentMode) {
        if (showSeriesDetails || showSearchOverlay || showExitDialog || showLogoutDialog || showUrlDialog || viewModel.showResumeDialog) {
            // Focus will be requested inside respective Overlays
            return@LaunchedEffect
        }
        
        // Delay to allow UI to settle, especially when coming back from player or closing overlays
        kotlinx.coroutines.delay(150)
        
        if (showContextMenu) {
            contextMenuFocusRequester.requestFocus()
        } else if (showChannelList) {
            if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                val currentChannel = viewModel.currentChannel
                val hasPlayingChannel = viewModel.channels.any { it.streamId == currentChannel?.streamId }
                
                if (hasPlayingChannel) {
                    val index = viewModel.channels.indexOfFirst { it.streamId == currentChannel?.streamId }
                    if (index >= 0) {
                        channelListState.scrollToItem(index)
                        kotlinx.coroutines.delay(100)
                        channelFocusRequester.requestFocus()
                    }
                } else if (viewModel.channels.isNotEmpty()) {
                    channelListState.scrollToItem(0)
                    kotlinx.coroutines.delay(100)
                    channelFocusRequester.requestFocus()
                } else {
                    railFocusRequester.requestFocus()
                }
            } else {
                if (viewModel.categories.isNotEmpty()) {
                    menuFocusRequester.requestFocus()
                } else {
                    railFocusRequester.requestFocus()
                }
            }
        } else {
            kotlinx.coroutines.delay(100)
            rootFocusRequester.requestFocus()
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
                if (showSubtitleMenu) showSubtitleMenu = false
                else showContextMenu = false
            }
            showPlaybackControls -> showPlaybackControls = false
            showChannelList -> showChannelList = false
            viewModel.activePlaybackMode == MainViewModel.AppMode.LIVE -> showExitDialog = true
            else -> showChannelList = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                val anyOverlayVisible = showChannelList || showContextMenu || showPlaybackControls || 
                                       showSeriesDetails || showSearchOverlay || showExitDialog || 
                                       showLogoutDialog || showUrlDialog || viewModel.showResumeDialog
                
                if (!anyOverlayVisible && event.type == KeyEventType.KeyUp) {
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            // Open overlay matching what is currently playing
                            if (viewModel.activePlaybackMode == MainViewModel.AppMode.LIVE) {
                                viewModel.changeMode(MainViewModel.AppMode.LIVE)
                                showChannelList = true
                            } else {
                                showPlaybackControls = true
                                lastInteractionTime = System.currentTimeMillis()
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            // Always sync mode to playback when opening via UP
                            viewModel.changeMode(viewModel.activePlaybackMode)
                            showChannelList = true
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (viewModel.activePlaybackMode != MainViewModel.AppMode.LIVE) {
                                viewModel.skipBackward()
                                showPlaybackControls = true
                                lastInteractionTime = System.currentTimeMillis()
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (viewModel.activePlaybackMode != MainViewModel.AppMode.LIVE) {
                                viewModel.skipForward()
                                showPlaybackControls = true
                                lastInteractionTime = System.currentTimeMillis()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else if (showPlaybackControls && event.type == KeyEventType.KeyUp) {
                    lastInteractionTime = System.currentTimeMillis()
                    false
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
            PlaybackControls(
                title = viewModel.pendingMovie?.name ?: "Spelar nu",
                isPlaying = viewModel.isPlaying,
                position = viewModel.playbackPosition,
                duration = viewModel.playbackDuration,
                onPlayPause = { viewModel.togglePlayPause() },
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

        // 4. Channel List / VOD / Series Overlay
        if (showChannelList) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)) // Mer transparent bakgrund
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A).copy(alpha = 0.85f)) // Genomskinlig panel
                ) {
                    // Left Navigation Rail
                    Column(
                        modifier = Modifier
                            .width(100.dp) // Lite bredare för bättre balans
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val modes = listOf(
                            Triple(MainViewModel.AppMode.LIVE, Icons.Default.Tv, "LIVE"),
                            Triple(MainViewModel.AppMode.VOD, Icons.Default.Movie, "VOD"),
                            Triple(MainViewModel.AppMode.SERIES, Icons.Default.VideoLibrary, "SERIER")
                        )

                        modes.forEachIndexed { index, (mode, icon, label) ->
                            val isSelected = viewModel.currentMode == mode
                            Surface(
                                onClick = { viewModel.changeMode(mode) },
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(vertical = 4.dp)
                                    .then(if (index == 0) Modifier.focusRequester(railFocusRequester) else Modifier),
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

                        // Sök
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            onClick = { 
                                viewModel.performSearch("") // Clear previous search
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
                                Icon(Icons.Default.Search, contentDescription = "Sök", modifier = Modifier.size(24.dp))
                                Text("SÖK", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Inställningar (Gear icon)
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
                                Icon(Icons.Default.Settings, contentDescription = "Inställningar", modifier = Modifier.size(24.dp))
                                Text("INST.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Categories
                    Column(
                        modifier = Modifier
                            .weight(0.9f) // Mer plats för kategorier
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        Text(
                            "KATEGORIER",
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            letterSpacing = 2.sp
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = categoryListState
                        ) {
                            items(viewModel.categories) { category ->
                                val isSelected = viewModel.selectedCategory == category
                                Surface(
                                    onClick = { viewModel.selectCategory(category) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .then(if (isSelected) Modifier.focusRequester(menuFocusRequester) else Modifier),
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

                    // Divider
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.DarkGray))

                    // Content Area (Channels / VOD / Series)
                    Column(modifier = Modifier.weight(3f)) { // Utnyttja mer bredd
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = viewModel.selectedCategory?.name ?: "Innehåll",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                                    viewModel.lastEpgUpdate?.let { lastUpdate ->
                                        val timeStr = remember(lastUpdate) {
                                            val sdf = SimpleDateFormat("'Uppdaterad' HH:mm", Locale.getDefault())
                                            sdf.format(Date(lastUpdate))
                                        }
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            // Digital Clock
                            TopRightClock()
                        }

                        if (viewModel.currentMode == MainViewModel.AppMode.LIVE) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                state = channelListState
                            ) {
                                itemsIndexed(viewModel.channels) { index, stream ->
                                    val isPlaying = viewModel.currentChannel == stream
                                    val isFirst = index == 0
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
                                                if (isPlaying) Modifier.focusRequester(channelFocusRequester) 
                                                else if (isFirst && viewModel.channels.none { it.streamId == viewModel.currentChannel?.streamId }) 
                                                    Modifier.focusRequester(channelFocusRequester) 
                                                else Modifier
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
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
                                                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                        "${sdf.format(Date(epgEntry.start))} - ${sdf.format(Date(epgEntry.stop))}"
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
                                                            isActive = isPlaying
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // VOD / Series Grid
                            Box(modifier = Modifier.fillMaxSize()) {
                                if ((viewModel.currentMode == MainViewModel.AppMode.VOD && viewModel.vodMovies.isEmpty()) ||
                                    (viewModel.currentMode == MainViewModel.AppMode.SERIES && viewModel.seriesList.isEmpty())) {
                                    Text(
                                        "Ingen data tillgänglig",
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
                                    contentPadding = PaddingValues(bottom = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (viewModel.currentMode == MainViewModel.AppMode.VOD) {
                                        items(viewModel.vodMovies) { movie ->
                                            VodCard(
                                                title = movie.name,
                                                posterUrl = movie.streamIcon,
                                                rating = movie.rating,
                                                onClick = { 
                                                    showPlaybackControls = false
                                                    viewModel.playVod(movie)
                                                    showChannelList = false
                                                }
                                            )
                                        }
                                    } else {
                                        items(viewModel.seriesList) { series ->
                                            VodCard(
                                                title = series.name,
                                                posterUrl = series.cover,
                                                rating = series.rating,
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

        // Series Details Overlay
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

        // Search Overlay
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

        // Context Menu Overlay
        if (showContextMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .onKeyEvent {
                        if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                            if (showSubtitleMenu) {
                                showSubtitleMenu = false
                            } else {
                                showContextMenu = false
                            }
                            true
                        } else false
                    }
                    .clickable { 
                        showContextMenu = false
                        showSubtitleMenu = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color(0xFF1A1A1A), shape = MaterialTheme.shapes.medium)
                        .padding(16.dp)
                        .clickable(enabled = false) {}
                ) {
                    Text(
                        text = if (showSubtitleMenu) "Undertexter" else "Meny",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = Color.White
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!showSubtitleMenu) {
                            item {
                                ContextMenuItem(
                                    text = "Uppdatera kanaler",
                                    icon = null,
                                    focusRequester = contextMenuFocusRequester,
                                    onClick = {
                                        viewModel.forceUpdateChannels()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Uppdatera EPG",
                                    icon = null,
                                    onClick = {
                                        viewModel.refreshEpg()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Uppdatera VOD/Serier",
                                    icon = null,
                                    onClick = {
                                        viewModel.refreshVodAndSeries()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Undertexter",
                                    icon = null,
                                    onClick = {
                                        showSubtitleMenu = true
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Tunneluppspelning: ${if (viewModel.isTunnelingEnabled) "På" else "Av"}",
                                    icon = null,
                                    onClick = {
                                        viewModel.toggleTunneling()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Matcha bildfrekvens: ${if (viewModel.isFrameRateMatchingEnabled) "På" else "Av"}",
                                    icon = null,
                                    onClick = {
                                        viewModel.toggleFrameRateMatching()
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Byt serveradress",
                                    icon = null,
                                    onClick = {
                                        showUrlDialog = true
                                        showContextMenu = false
                                    }
                                )
                            }
                            item {
                                ContextMenuItem(
                                    text = "Logga ut",
                                    icon = null,
                                    onClick = {
                                        showLogoutDialog = true
                                        showContextMenu = false
                                    }
                                )
                            }
                        } else {
                            item {
                                ContextMenuItem(
                                    text = "Av",
                                    icon = null,
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
                                    icon = null,
                                    onClick = {
                                        viewModel.selectSubtitle(track)
                                        showSubtitleMenu = false
                                        showContextMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading and Update Indicators
        if (viewModel.isEpgUpdating || viewModel.isLoading) {
            LoadingIndicator(
                text = if (viewModel.isEpgUpdating) "Uppdaterar EPG..." else "Laddar..."
            )
        }

        // Exit Dialog
        if (showExitDialog) {
            ExitDialog(
                onConfirm = { (context as? android.app.Activity)?.finish() },
                onDismiss = { showExitDialog = false }
            )
        }

        // Logout Dialog
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

        // Change URL Dialog
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

@Composable
fun TopRightClock() {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val clockStr = remember(currentTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime))
    }
    Text(
        text = clockStr,
        style = MaterialTheme.typography.headlineMedium,
        color = Turquoise,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun EpgProgressBar(start: Long, stop: Long, isActive: Boolean) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val progress = if (stop > start) {
        ((currentTime - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(4.dp)
            .background(LocalContentColor.current.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(if (isActive) Turquoise else Color.White, RoundedCornerShape(2.dp))
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResumeDialog(
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Fortsätt titta?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Vill du fortsätta från där du slutade eller spela från början?",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            val resumeFocusRequester = remember { FocusRequester() }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    onClick = onResume,
                    modifier = Modifier.weight(1f).focusRequester(resumeFocusRequester),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Turquoise,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Fortsätt", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                
                Surface(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Från början", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            LaunchedEffect(Unit) {
                resumeFocusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun ChangeUrlDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ändra serveradress",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Ange den nya serveradressen nedan. Användarnamn och lösenord behålls.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { 
                        if (it.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                            // Let the system handle focus move, but ensure it doesn't get stuck
                            false
                        } else false
                    },
                label = { Text("Server URL") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Turquoise,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = Turquoise,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val saveFocusRequester = remember { FocusRequester() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    onClick = { onConfirm(url) },
                    modifier = Modifier.weight(1f).focusRequester(saveFocusRequester),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Turquoise,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Spara", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Avbryt", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100)
                focusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ExitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Avsluta appen?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Vill du stänga XTR Tv?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            val exitFocusRequester = remember { FocusRequester() }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).focusRequester(exitFocusRequester),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Turquoise,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Ja, avsluta", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Avbryt", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            LaunchedEffect(Unit) {
                exitFocusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LogoutDialog(
    username: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Logga ut?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Är du säker på att du vill logga ut $username?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            val logoutFocusRequester = remember { FocusRequester() }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).focusRequester(logoutFocusRequester),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Turquoise,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Ja, logga ut", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text("Avbryt", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100)
                logoutFocusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun LoadingIndicator(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.8f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Turquoise,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodCard(
    title: String,
    posterUrl: String?,
    rating: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(0.7f)
            .fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1A1A1A),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Rating badge
            if (!rating.isNullOrBlank() && rating != "0") {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD700)
                    )
                }
            }

            // Title overlay (only on focus or bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackControls(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long, Boolean) -> Unit,
    focusRequester: FocusRequester
) {
    // Continuous seek logic
    var isSeekingForward by remember { mutableStateOf(false) }
    var isSeekingBackward by remember { mutableStateOf(false) }
    var seekMultiplier by remember { mutableIntStateOf(1) }

    // Use a local position state to drive the UI for instant feedback
    var localPosition by remember { mutableLongStateOf(position) }
    
    // Update local position when playback position changes (if not seeking)
    LaunchedEffect(position) {
        if (!isSeekingForward && !isSeekingBackward) {
            localPosition = position
        }
    }

    LaunchedEffect(isSeekingForward, isSeekingBackward) {
        if (isSeekingForward || isSeekingBackward) {
            val startTime = System.currentTimeMillis()
            while (isSeekingForward || isSeekingBackward) {
                val elapsed = System.currentTimeMillis() - startTime
                // Increase speed after 2s and 5s
                seekMultiplier = when {
                    elapsed > 5000 -> 10
                    elapsed > 2000 -> 3
                    else -> 1
                }
                
                localPosition = if (isSeekingForward) {
                    (localPosition + 5000L * seekMultiplier).coerceAtMost(duration)
                } else {
                    (localPosition - 5000L * seekMultiplier).coerceAtLeast(0L)
                }
                
                onSeek(localPosition, true)
                kotlinx.coroutines.delay(150) // Slightly faster updates
            }
            onSeek(localPosition, false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .onKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (event.type == KeyEventType.KeyDown) {
                            if (!isSeekingBackward) isSeekingBackward = true
                        } else if (event.type == KeyEventType.KeyUp) {
                            isSeekingBackward = false
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.type == KeyEventType.KeyDown) {
                            if (!isSeekingForward) isSeekingForward = true
                        } else if (event.type == KeyEventType.KeyUp) {
                            isSeekingForward = false
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.type == KeyEventType.KeyUp) onPlayPause()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
                .padding(horizontal = 60.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title Information
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Seeking speed indicator
            if (isSeekingForward || isSeekingBackward) {
                Text(
                    text = "${seekMultiplier}x",
                    style = MaterialTheme.typography.labelLarge,
                    color = Turquoise,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(localPosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                    // Progress track
                    val progress = if (duration > 0) (localPosition.toFloat() / duration.toFloat()) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp) // Slightly thicker when active
                            .background(if (isSeekingForward || isSeekingBackward) Color.White else Turquoise, RoundedCornerShape(3.dp))
                    )
                }
                
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Play/Pause indicator
            Surface(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(80.dp)
                    .focusRequester(focusRequester),
                shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isPlaying) Color.Transparent else Turquoise,
                    focusedContainerColor = Color.White,
                    contentColor = if (isPlaying) Color.White else Color.Black,
                    focusedContentColor = Color.Black
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContextMenuItem(
    text: String,
    icon: (@Composable () -> Unit)?,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF00CED1),
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onPlayVod: (com.example.xtrtv.api.VodMovie) -> Unit,
    onOpenSeries: (com.example.xtrtv.api.Series) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable { onClose() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp)
                .clickable(enabled = false) {}
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Turquoise, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.performSearch(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Sök efter filmer eller serier...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Turquoise,
                        unfocusedBorderColor = Color.DarkGray,
                        cursorColor = Turquoise,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (viewModel.searchQuery.length >= 2) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (viewModel.filteredVod.isNotEmpty()) {
                        item {
                            Text("FILMER", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(viewModel.filteredVod) { movie ->
                                    Box(modifier = Modifier.width(150.dp)) {
                                        VodCard(
                                            title = movie.name,
                                            posterUrl = movie.streamIcon,
                                            rating = movie.rating,
                                            onClick = { onPlayVod(movie) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.filteredSeries.isNotEmpty()) {
                        item {
                            Text("SERIER", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(viewModel.filteredSeries) { series ->
                                    Box(modifier = Modifier.width(150.dp)) {
                                        VodCard(
                                            title = series.name,
                                            posterUrl = series.cover,
                                            rating = series.rating,
                                            onClick = { onOpenSeries(series) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.filteredVod.isEmpty() && viewModel.filteredSeries.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                Text("Inga träffar hittades för \"${viewModel.searchQuery}\"", color = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (viewModel.searchQuery.isEmpty()) "Börja skriva för att söka..." 
                        else "Skriv minst 2 tecken för att söka...", 
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailsOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onPlayEpisode: (com.example.xtrtv.api.Episode) -> Unit
) {
    val details = viewModel.seriesDetails
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }

    // Reset season when series details change
    LaunchedEffect(details?.info?.name) {
        selectedSeason = details?.episodes?.keys?.firstOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onClose() }
    ) {
        if (viewModel.isSeriesLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Turquoise)
        } else if (details != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp)
                    .clickable(enabled = false) {}
            ) {
                // Left side: Info
                Column(modifier = Modifier.weight(1f).padding(end = 40.dp)) {
                    AsyncImage(
                        model = details.info?.cover,
                        contentDescription = details.info?.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .background(Color.DarkGray, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(details.info?.name ?: "", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text(
                        text = "★ ${details.info?.rating ?: "N/A"} | ${details.info?.genre ?: "Genre"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Turquoise
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        details.info?.plot ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right side: Seasons & Episodes
                Column(modifier = Modifier.weight(2f)) {
                    // Seasons Selection (FlowRow for automatic wrapping)
                    val seasons = details.episodes?.keys?.toList() ?: emptyList()
                    
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seasons.forEach { seasonNum ->
                            val isSelected = selectedSeason == seasonNum
                            Surface(
                                onClick = { selectedSeason = seasonNum },
                                modifier = Modifier.padding(vertical = 4.dp),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Turquoise else Color.Transparent,
                                    contentColor = if (isSelected) Color.Black else Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = "Säsong $seasonNum",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    // Continue/Play Button
                    val lastEp = viewModel.lastWatchedEpisode
                    val firstEp = details.episodes?.values?.firstOrNull()?.firstOrNull()
                    val targetEpisode = lastEp ?: firstEp

                    if (targetEpisode != null) {
                        val seasonNum = details.episodes?.entries?.find { it.value.contains(targetEpisode) }?.key ?: "1"
                        val epNum = targetEpisode.episodeNum ?: "1"
                        val isContinue = lastEp != null

                        Surface(
                            onClick = { onPlayEpisode(targetEpisode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .focusRequester(continueFocusRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Turquoise,
                                contentColor = Color.Black,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isContinue) "Fortsätt titta: Säsong $seasonNum Avsnitt $epNum"
                                    else "Spela Säsong $seasonNum, Avsnitt $epNum",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Episodes List
                    val episodes = details.episodes?.get(selectedSeason) ?: emptyList()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            Surface(
                                onClick = { onPlayEpisode(episode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF1A1A1A),
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = episode.episodeNum ?: (index + 1).toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.width(40.dp),
                                        color = Turquoise
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(episode.title ?: "Avsnitt ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                        if (!episode.info?.plot.isNullOrBlank()) {
                                            Text(
                                                episode.info.plot,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    LaunchedEffect(selectedSeason) {
                        if (targetEpisode != null) {
                            continueFocusRequester.requestFocus()
                        } else if (episodes.isNotEmpty()) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
