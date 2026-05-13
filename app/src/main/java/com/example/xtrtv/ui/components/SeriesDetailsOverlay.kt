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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 40.dp)) {
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
                        text = "★ ${details.info?.rating ?: "N/A"} | ${details.info?.genre ?: stringResource(R.string.genre_label)}",
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
                Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                    // Seasons Selection (LazyRow for horizontal scrolling on TV)
                    val seasons = details.episodes?.keys?.toList() ?: emptyList()
                    
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(seasons) { seasonNum ->
                            val isSelected = selectedSeason == seasonNum
                            Surface(
                                onClick = { selectedSeason = seasonNum },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Turquoise else Color.Transparent,
                                    contentColor = if (isSelected) Color.Black else Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = stringResource(R.string.season_label, seasonNum),
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
                                    text = if (isContinue) stringResource(R.string.continue_watching_label, seasonNum, epNum)
                                           else stringResource(R.string.play_episode_label, seasonNum, epNum),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Episodes List
                    val episodes = details.episodes?.get(selectedSeason) ?: emptyList()
                    val initialFocusIndex = remember(episodes, lastEp) {
                        val idx = episodes.indexOfFirst { it.id == lastEp?.id }
                        if (idx != -1) idx else 0
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(episodes) { index, episode ->
                            Surface(
                                onClick = { onPlayEpisode(episode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == initialFocusIndex) Modifier.focusRequester(focusRequester) else Modifier),
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
                                        val epTitle = episode.title ?: stringResource(R.string.episode_label, (index + 1).toString())
                                        Text(epTitle, style = MaterialTheme.typography.titleMedium)
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
                        if (episodes.isNotEmpty()) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
