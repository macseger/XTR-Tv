package com.example.xtrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.xtrtv.R
import com.example.xtrtv.ui.main.MainViewModel
import com.example.xtrtv.ui.theme.Turquoise

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailsOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onPlayEpisode: (com.example.xtrtv.api.Episode) -> Unit
) {
    val details = viewModel.seriesDetails
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var showFullPlot by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }
    val moreInfoFocusRequester = remember { FocusRequester() }

    // Reset season when series details change
    LaunchedEffect(details?.info?.name, viewModel.lastWatchedEpisode) {
        val lastEp = viewModel.lastWatchedEpisode
        if (lastEp != null && details?.episodes != null) {
            val season = details.episodes.entries.find { entry -> 
                entry.value.any { it.id == lastEp.id } 
            }?.key
            if (season != null) {
                selectedSeason = season
                return@LaunchedEffect
            }
        }
        if (selectedSeason == null) {
            selectedSeason = details?.episodes?.keys?.firstOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onClose() }
    ) {
        // Fullscreen Background with cinematic overlay
        if (details != null) {
            AsyncImage(
                model = details.info?.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black),
                            startY = 0f,
                            endY = 1400f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent),
                            startX = 0f,
                            endX = 2000f
                        )
                    )
            )
        }

        if (viewModel.isSeriesLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Turquoise)
        } else if (details != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 60.dp, vertical = 40.dp)
                    .clickable(enabled = false) {}
            ) {
                // Left side: Poster & Detailed Plot
                Column(modifier = Modifier.width(220.dp).fillMaxHeight().padding(end = 30.dp)) {
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        modifier = Modifier.width(180.dp).aspectRatio(0.7f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Turquoise))
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                    ) {
                        AsyncImage(
                            model = details.info?.cover,
                            contentDescription = details.info?.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = details.info?.rating ?: "N/A",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = details.info?.releaseDate?.take(4) ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    Text(
                        text = "INNEHÅLL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Turquoise,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    val plot = details.info?.plot ?: ""
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 6,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (plot.length > 150) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            onClick = { showFullPlot = true },
                            modifier = Modifier.focusRequester(moreInfoFocusRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                        ) {
                            Text(
                                text = "MERA INFO",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Right side: Title, Selection & Episodes
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Title at the top right
                    Text(
                        text = details.info?.name ?: "",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Text(
                        text = details.info?.genre ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Compact Actions & Seasons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play / Continue Button
                        val lastEpWatchedBtn = viewModel.lastWatchedEpisode
                        val firstEpBtn = details.episodes?.values?.firstOrNull()?.firstOrNull()
                        val targetEpisodeBtn = lastEpWatchedBtn ?: firstEpBtn

                        if (targetEpisodeBtn != null) {
                            val seasonNum = details.episodes?.entries?.find { it.value.contains(targetEpisodeBtn) }?.key ?: "1"
                            val epNum = targetEpisodeBtn.episodeNum ?: "1"
                            val isContinue = lastEpWatchedBtn != null

                            Surface(
                                onClick = { onPlayEpisode(targetEpisodeBtn) },
                                modifier = Modifier
                                    .height(40.dp)
                                    .focusRequester(continueFocusRequester),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isContinue) "FORTSÄTT S$seasonNum:A$epNum"
                                               else "SPELA S$seasonNum:A$epNum",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Seasons Selector
                        val seasons = details.episodes?.keys?.toList() ?: emptyList()
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            items(seasons) { seasonNum ->
                                val isSelected = selectedSeason == seasonNum
                                Surface(
                                    onClick = { selectedSeason = seasonNum },
                                    modifier = Modifier.height(40.dp),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                        contentColor = if (isSelected) Turquoise else Color.White.copy(alpha = 0.6f),
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxHeight().padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "SÄSONG $seasonNum",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Episodes List
                    val episodes = details.episodes?.get(selectedSeason) ?: emptyList()
                    val lastEpWatched = viewModel.lastWatchedEpisode
                    val initialFocusIndex = remember(episodes, lastEpWatched) {
                        val idx = episodes.indexOfFirst { it.id == lastEpWatched?.id }
                        if (idx != -1) idx else 0
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            val isWatched = lastEpWatched?.id == episode.id
                            Surface(
                                onClick = { onPlayEpisode(episode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == initialFocusIndex) Modifier.focusRequester(focusRequester) else Modifier),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.03f),
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (episode.episodeNum ?: (index + 1).toString()).padStart(2, '0'),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = if (isWatched) Turquoise else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.width(32.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        val epTitle = episode.title ?: stringResource(R.string.episode_label, (index + 1).toString())
                                        Text(
                                            text = epTitle,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!episode.info?.plot.isNullOrBlank()) {
                                            Text(
                                                text = episode.info!!.plot!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isWatched) Color.Black.copy(alpha = 0.6f) else Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    if (isWatched) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Turquoise,
                                            modifier = Modifier.padding(start = 16.dp).size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    LaunchedEffect(selectedSeason) {
                        if (episodes.isNotEmpty()) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }

    if (showFullPlot) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { showFullPlot = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(600.dp)
                    .background(Color(0xFF121212), RoundedCornerShape(24.dp))
                    .padding(40.dp)
                    .clickable(enabled = false) {}
            ) {
                Text(
                    text = "INNEHÅLL",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Turquoise,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = details?.info?.plot ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 26.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Surface(
                    onClick = { showFullPlot = false },
                    modifier = Modifier.align(Alignment.End),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Turquoise,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "STÄNG",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
