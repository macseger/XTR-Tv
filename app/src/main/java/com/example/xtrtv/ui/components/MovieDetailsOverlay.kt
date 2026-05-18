package com.example.xtrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.xtrtv.api.VodMovie
import com.example.xtrtv.ui.main.MainViewModel
import com.example.xtrtv.ui.theme.Turquoise

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailsOverlay(
    movie: VodMovie,
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onPlay: (Boolean) -> Unit // true to resume, false to play from start
) {
    val focusRequester = remember { FocusRequester() }
    val history = viewModel.selectedMovieHistory
    val hasHistory = history != null && history.position > 15_000

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onClose() }
    ) {
        // Fullscreen Background with cinematic overlay
        AsyncImage(
            model = movie.streamIcon,
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 60.dp, vertical = 40.dp)
                .clickable(enabled = false) {}
        ) {
            // Left side: Poster
            Column(modifier = Modifier.width(260.dp).fillMaxHeight().padding(end = 40.dp)) {
                Surface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                        focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Turquoise))
                    )
                ) {
                    AsyncImage(
                        model = movie.streamIcon,
                        contentDescription = movie.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Right side: Details
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    val rating = viewModel.currentVodRating ?: movie.rating
                    if (!rating.isNullOrBlank() && rating != "0") {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                    }

                    val releaseDate = viewModel.currentVodReleaseDate ?: movie.releaseDate
                    if (!releaseDate.isNullOrBlank()) {
                        Text(
                            text = releaseDate.take(4),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                    }

                    val genre = viewModel.currentVodGenre ?: movie.genre
                    if (!genre.isNullOrBlank()) {
                        Text(
                            text = genre.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Turquoise,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Text(
                    text = (viewModel.currentVodPlot ?: movie.plot) ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 10,
                    lineHeight = 22.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.95f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    if (hasHistory) {
                        Surface(
                            onClick = { onPlay(true) },
                            modifier = Modifier
                                .height(48.dp)
                                .focusRequester(focusRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Turquoise,
                                contentColor = Color.Black,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("FORTSÄTT TITTA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                            }
                        }

                        Surface(
                            onClick = { onPlay(false) },
                            modifier = Modifier.height(48.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                                Text("SPELA FRÅN BÖRJAN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                            }
                        }
                    } else {
                        Surface(
                            onClick = { onPlay(false) },
                            modifier = Modifier
                                .height(48.dp)
                                .focusRequester(focusRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Turquoise,
                                contentColor = Color.Black,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SPELA UPP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
