package com.example.xtrtv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.xtrtv.R
import com.example.xtrtv.ui.main.MainViewModel
import com.example.xtrtv.ui.theme.Turquoise

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onPlayVod: (com.example.xtrtv.api.VodMovie) -> Unit,
    onOpenSeries: (com.example.xtrtv.api.Series) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val resultsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadSearchHistory()
    }

    BackHandler {
        onClose()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp)
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
                        .focusRequester(focusRequester)
                        .focusProperties {
                            down = resultsFocusRequester
                        },
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
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

            Spacer(modifier = Modifier.height(24.dp))

            if (viewModel.searchQuery.length >= 2) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Movies Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.movies).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Turquoise,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (viewModel.filteredVod.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_search_results, ""), color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(viewModel.filteredVod, key = { _, movie -> "movie_${movie.streamId}" }) { index, movie ->
                                    val categoryName = viewModel.vodCategoryMap[movie.categoryId]
                                    SearchListItem(
                                        viewModel = viewModel,
                                        title = movie.name,
                                        categoryName = categoryName,
                                        imageUrl = movie.streamIcon,
                                        modifier = if (index == 0) Modifier.focusRequester(resultsFocusRequester) else Modifier,
                                        onClick = {
                                            viewModel.saveSearchQuery(viewModel.searchQuery)
                                            onPlayVod(movie)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Series Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.series).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Turquoise,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (viewModel.filteredSeries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_search_results, ""), color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(viewModel.filteredSeries, key = { _, series -> "series_${series.seriesId}" }) { index, series ->
                                    val categoryName = viewModel.seriesCategoryMap[series.categoryId]
                                    SearchListItem(
                                        viewModel = viewModel,
                                        title = series.name,
                                        categoryName = categoryName,
                                        imageUrl = series.cover,
                                        modifier = if (viewModel.filteredVod.isEmpty() && index == 0) Modifier.focusRequester(resultsFocusRequester) else Modifier,
                                        onClick = {
                                            viewModel.saveSearchQuery(viewModel.searchQuery)
                                            onOpenSeries(series)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Show Search History when query is empty or too short
                Column(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.searchHistory.isNotEmpty()) {
                        Text(
                            text = "SENASTE SÖKNINGAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Turquoise,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(viewModel.searchHistory, key = { _, query -> query }) { index, query ->
                                Surface(
                                    onClick = { 
                                        viewModel.performSearch(query)
                                        // Refocus text field to maintain focus state after UI swap
                                        focusRequester.requestFocus()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (index == 0) Modifier.focusRequester(resultsFocusRequester) else Modifier),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        focusedContainerColor = Color.White,
                                        contentColor = Color.White,
                                        focusedContentColor = Color.Black
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.History, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        
                                        // Delete button
                                        IconButton(onClick = { viewModel.deleteSearchQuery(query) }) {
                                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (viewModel.searchQuery.isEmpty()) stringResource(R.string.start_typing_to_search) 
                                else stringResource(R.string.search_min_chars), 
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchListItem(
    viewModel: MainViewModel,
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val localIconResId = remember(title) { viewModel.getLocalResourceIdentifier(title) }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .apply {
                        if (localIconResId != 0) {
                            error(localIconResId)
                            fallback(localIconResId)
                        }
                    }
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 60.dp, height = 34.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!categoryName.isNullOrBlank()) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Turquoise,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
