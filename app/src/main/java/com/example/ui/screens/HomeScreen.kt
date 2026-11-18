package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AccountSession
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.ClipProject
import com.example.domain.model.ExportedVideo
import com.example.domain.model.GeneratedClip
import com.example.domain.model.ProcessingStatus
import com.example.ui.components.AiStatusBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ViralityScorePill
import com.example.ui.components.formatDurationMs
import java.io.File

@Composable
fun HomeScreen(
    session: AccountSession,
    aiAvailability: AiAvailabilityState,
    projects: List<ClipProject>,
    recentClips: List<GeneratedClip>,
    exports: List<ExportedVideo>,
    onUploadVideoClick: () -> Unit,
    onAutoCreateClick: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenClip: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onCancelProcessing: (String) -> Unit
) {
    val activeProjects = projects.filter { it.status == ProcessingStatus.PROCESSING }
    var playingClip by remember { mutableStateOf<GeneratedClip?>(null) }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val greetingName = when (session) {
        is AccountSession.GoogleUser -> session.displayName.substringBefore(' ')
        AccountSession.Guest -> "Creator (Guest)"
        AccountSession.Unauthenticated -> "Creator"
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Welcome back, $greetingName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AiStatusBadge(state = aiAvailability)
            }
        }

        if (aiAvailability == AiAvailabilityState.QUOTA_EXHAUSTED) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(R.string.quota_exhausted_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Hero Studio Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_studio),
                            contentDescription = "CLIPFREE.AI Studio",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Long Videos In. Viral-Ready Shorts Out.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onUploadVideoClick,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_upload_video_button")
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload Video")
                        }
                        FilledTonalButton(
                            onClick = onAutoCreateClick,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_auto_create_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto Create")
                        }
                    }
                }
            }
        }

        // Active Processing Banner
        if (activeProjects.isNotEmpty()) {
            items(activeProjects, key = { "active_${it.id}" }) { proj ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Processing: ${proj.name}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${proj.progressPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { proj.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = proj.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { onCancelProcessing(proj.id) }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        // Quick Access to Downloads
        item {
            Card(
                onClick = onOpenDownloads,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = "Downloads",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ready Downloads", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${exports.size} exported MP4 shorts ready to share",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Open downloads",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Recent Projects
        item {
            Text("Recent Projects", style = MaterialTheme.typography.titleLarge)
        }

        if (projects.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No projects yet",
                    subtitle = "Import a podcast, vlog, lecture, or gaming video and let AI turn it into viral-ready shorts.",
                    actionLabel = "Import First Video",
                    onAction = onUploadVideoClick
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(projects.take(6), key = { it.id }) { proj ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .width(220.dp)
                                .clickable { onOpenProject(proj.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!proj.thumbnailPath.isNullOrBlank() && File(proj.thumbnailPath).exists()) {
                                    AsyncImage(
                                        model = File(proj.thumbnailPath),
                                        contentDescription = proj.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${proj.clipCount} clips • ${formatDurationMs(proj.sourceDurationMs)} • ${proj.status.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recently Generated Clips
        if (recentClips.isNotEmpty()) {
            item {
                Text("Recently Generated Clips", style = MaterialTheme.typography.titleLarge)
            }
            items(recentClips.take(5), key = { "recent_${it.id}" }) { clip ->
                Card(
                    onClick = { onOpenClip(clip.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!clip.thumbnailPath.isNullOrBlank() && File(clip.thumbnailPath).exists()) {
                            AsyncImage(
                                model = File(clip.thumbnailPath),
                                contentDescription = clip.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp, 96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = clip.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${formatDurationMs(clip.durationMs)} • ${clip.aspectRatio.wireValue} • ${clip.category.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            ViralityScorePill(score = clip.viralityScore)
                        }
                        androidx.compose.material3.IconButton(onClick = { playingClip = clip }) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircleOutline,
                                contentDescription = "Play clip",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    playingClip?.let { clip ->
        val localRendered = clip.clipFilePath?.let { File(it) }?.takeIf { it.exists() }
        val sourceUri = projectById[clip.projectId]?.sourceUri
        if (localRendered != null || !sourceUri.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { playingClip = null },
                title = { Text(clip.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    com.example.ui.components.VideoPlayerPreview(
                        sourcePathOrUri = localRendered?.absolutePath ?: sourceUri.orEmpty(),
                        aspectRatio = clip.aspectRatio,
                        clipStartMs = clip.effectiveStartMs,
                        clipEndMs = clip.effectiveEndMs,
                        isLocalFile = localRendered != null,
                        autoPlay = true,
                        adjustment = clip.adjustment,
                        captions = clip.captions,
                        showCaptionOverlay = localRendered == null
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        playingClip = null
                        onOpenClip(clip.id)
                    }) {
                        Text("Adjust & Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playingClip = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
