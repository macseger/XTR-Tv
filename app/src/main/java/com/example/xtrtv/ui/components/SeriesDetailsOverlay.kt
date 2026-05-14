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
    val focusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }

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
                alpha = 0.4f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = 1200f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
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
                // Left side: Large Poster & Info
                Column(modifier = Modifier.weight(1.2f).fillMaxHeight().padding(end = 60.dp)) {
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                    ) {
                        AsyncImage(
                            model = details.info?.cover,
                            contentDescription = details.info?.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = details.info?.name ?: "",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = details.info?.rating ?: "N/A",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = details.info?.genre ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    Text(
                        text = details.info?.plot ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 8,
                        lineHeight = 26.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right side: Content Selection
                Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                    // Seasons Selector
                    val seasons = details.episodes?.keys?.toList() ?: emptyList()
                    
                    Text(
                        text = stringResource(R.string.categories).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Turquoise,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(seasons) { seasonNum ->
                            val isSelected = selectedSeason == seasonNum
                            Surface(
                                onClick = { selectedSeason = seasonNum },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Turquoise else Color.White.copy(alpha = 0.1f),
                                    contentColor = if (isSelected) Color.Black else Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.season_label, seasonNum),
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Main Action (Continue)
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
                                .padding(bottom = 24.dp)
                                .height(72.dp)
                                .focusRequester(continueFocusRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Turquoise,
                                contentColor = Color.Black,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isContinue) stringResource(R.string.continue_watching_label, seasonNum, epNum).uppercase()
                                           else stringResource(R.string.play_episode_label, seasonNum, epNum).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Episodes Scrollable List
                    val episodes = details.episodes?.get(selectedSeason) ?: emptyList()
                    val initialFocusIndex = remember(episodes, lastEp) {
                        val idx = episodes.indexOfFirst { it.id == lastEp?.id }
                        if (idx != -1) idx else 0
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 40.dp)
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            val isWatched = lastEp?.id == episode.id
                            Surface(
                                onClick = { onPlayEpisode(episode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == initialFocusIndex) Modifier.focusRequester(focusRequester) else Modifier),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(if (isWatched) Turquoise.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = episode.episodeNum ?: (index + 1).toString(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                            color = if (isWatched) Turquoise else Color.White
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(20.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        val epTitle = episode.title ?: stringResource(R.string.episode_label, (index + 1).toString())
                                        Text(
                                            text = epTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!episode.info?.plot.isNullOrBlank()) {
                                            Text(
                                                text = episode.info!!.plot!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isWatched) Color.Black.copy(alpha = 0.6f) else Color.Gray,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    if (isWatched) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Turquoise,
                                            modifier = Modifier.padding(start = 16.dp)
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
}
