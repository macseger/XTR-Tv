package com.example.xtrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.example.xtrtv.R
import com.example.xtrtv.ui.theme.Turquoise
import java.util.*

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
                    brush = Brush.verticalGradient(
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
                shape = ClickableSurfaceDefaults.shape(CircleShape),
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
