package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionPosition
import com.example.domain.model.CaptionSegment
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ClipAdjustment
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

fun formatDate(ms: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return fmt.format(Date(ms))
}

@Composable
fun AiStatusBadge(
    state: AiAvailabilityState,
    modifier: Modifier = Modifier
) {
    val (bgColor, icon, textColor) = when (state) {
        AiAvailabilityState.AI_AVAILABLE -> Triple(
            StatusSuccess.copy(alpha = 0.14f),
            Icons.Rounded.CheckCircle,
            StatusSuccess
        )
        AiAvailabilityState.PROCESSING -> Triple(
            StatusInfo.copy(alpha = 0.16f),
            Icons.Rounded.HourglassTop,
            StatusInfo
        )
        AiAvailabilityState.TEMPORARILY_LIMITED -> Triple(
            StatusWarning.copy(alpha = 0.16f),
            Icons.Rounded.AutoAwesome,
            StatusWarning
        )
        AiAvailabilityState.QUOTA_EXHAUSTED -> Triple(
            StatusError.copy(alpha = 0.15f),
            Icons.Rounded.ErrorOutline,
            StatusError
        )
        AiAvailabilityState.OFFLINE_MODE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Rounded.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        AiAvailabilityState.READY_TO_RETRY -> Triple(
            StatusWarning.copy(alpha = 0.15f),
            Icons.Rounded.Refresh,
            StatusWarning
        )
    }

    Surface(
        color = bgColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = state.label,
                tint = textColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ViralityScorePill(
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        score >= 80 -> StatusSuccess
        score >= 60 -> MaterialTheme.colorScheme.primary
        else -> StatusWarning
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.TrendingUp,
                contentDescription = "Virality score",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerPreview(
    sourcePathOrUri: String,
    aspectRatio: AspectRatioPreset,
    modifier: Modifier = Modifier,
    clipStartMs: Long = 0L,
    clipEndMs: Long = 0L,
    isLocalFile: Boolean = false,
    autoPlay: Boolean = false,
    adjustment: ClipAdjustment? = null,
    captions: List<CaptionSegment> = emptyList(),
    showCaptionOverlay: Boolean = false
) {
    val context = LocalContext.current
    val mediaUri = remember(sourcePathOrUri, isLocalFile) {
        if (isLocalFile) Uri.fromFile(File(sourcePathOrUri)) else Uri.parse(sourcePathOrUri)
    }

    var playerError by remember(mediaUri, clipStartMs, clipEndMs) {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    val exoPlayer = remember(mediaUri, clipStartMs, clipEndMs, autoPlay) {
        ExoPlayer.Builder(context).build().apply {
            val itemBuilder = MediaItem.Builder().setUri(mediaUri)
            if (clipEndMs > clipStartMs && !isLocalFile) {
                itemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clipEndMs.coerceAtLeast(clipStartMs + 500L))
                        .build()
                )
            }
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerError = error.localizedMessage ?: "Unable to play this video clip."
                }
            })
            setMediaItem(itemBuilder.build())
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = autoPlay
            prepare()
        }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(exoPlayer, showCaptionOverlay) {
        if (showCaptionOverlay) {
            while (true) {
                currentPositionMs = clipStartMs + exoPlayer.currentPosition.coerceAtLeast(0L)
                delay(120L)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            runCatching {
                exoPlayer.stop()
                exoPlayer.clearVideoSurface()
                exoPlayer.release()
            }
        }
    }

    val ratioFloat = (aspectRatio.widthRatio.toFloat() / aspectRatio.heightRatio.toFloat())
        .coerceIn(0.5f, 1.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratioFloat)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0E0D14))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx)
                    .inflate(R.layout.view_clip_player, null, false) as PlayerView).apply {
                    player = exoPlayer
                    useController = true
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playerError != null) {
            Surface(
                color = Color(0xCC17151F),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = playerError ?: "Playback unavailable",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (showCaptionOverlay && adjustment != null && captions.isNotEmpty() && playerError == null) {
            val activeSeg = captions.firstOrNull { currentPositionMs in it.startMs..it.endMs }
                ?: captions.firstOrNull()
            if (activeSeg != null) {
                val captionAlign = when (adjustment.captionPosition) {
                    CaptionPosition.TOP -> Alignment.TopCenter
                    CaptionPosition.CENTER -> Alignment.Center
                    CaptionPosition.BOTTOM -> Alignment.BottomCenter
                }
                val activeText = when (adjustment.captionStyle) {
                    CaptionStylePreset.WORD_BY_WORD -> {
                        activeSeg.words.firstOrNull { currentPositionMs in it.startMs..it.endMs }?.word
                            ?: activeSeg.text
                    }
                    CaptionStylePreset.BOLD_CREATOR -> activeSeg.text.uppercase()
                    else -> activeSeg.text
                }
                Box(
                    modifier = Modifier
                        .align(captionAlign)
                        .padding(horizontal = 20.dp, vertical = 48.dp)
                        .background(
                            if (adjustment.captionStyle == CaptionStylePreset.MINIMAL) Color.Transparent
                            else Color(0xB514121E),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = activeText,
                        color = Color(adjustment.captionColorHex),
                        fontSize = (adjustment.captionSizeSp * 0.72f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_clips),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(140.dp)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
