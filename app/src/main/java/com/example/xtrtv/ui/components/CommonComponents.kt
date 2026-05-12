package com.example.xtrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.xtrtv.ui.theme.Turquoise
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TopRightClock(currentTime: Long) {
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
fun EpgProgressBar(start: Long, stop: Long, isActive: Boolean, currentTime: Long) {
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
fun ContextMenuItem(
    text: String,
    icon: (@Composable () -> Unit)? = null,
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
            focusedContainerColor = Turquoise,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodCard(
    title: String,
    posterUrl: String?,
    rating: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
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
