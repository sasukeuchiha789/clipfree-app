package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ClipDurationPreset
import com.example.domain.model.GeneratedClip
import com.example.domain.model.ProcessingStatus
import com.example.ui.components.AiStatusBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.components.ViralityScorePill
import com.example.ui.components.formatDurationMs
import com.example.ui.viewmodel.CreateViewModel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    aiAvailability: AiAvailabilityState,
    onOpenClip: (String) -> Unit
) {
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()
    val isScanningFolder by viewModel.isScanningFolder.collectAsStateWithLifecycle()
    val folderVideos by viewModel.folderVideos.collectAsStateWithLifecycle()
    val durationPreset by viewModel.durationPreset.collectAsStateWithLifecycle()
    val customMin by viewModel.customMinSeconds.collectAsStateWithLifecycle()
    val customMax by viewModel.customMaxSeconds.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val captionStyle by viewModel.captionStyle.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeClips by viewModel.activeClips.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val quotaExhaustedMessage by viewModel.quotaExhaustedMessage.collectAsStateWithLifecycle()

    var playingClip by remember { mutableStateOf<GeneratedClip?>(null) }

    // 1. Gallery / Photos Picker (zero-permission Photo Picker)
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
    }

    // 2. Drive & Files Document Picker (Google Drive, Downloads, Recents, Cloud Providers)
    val driveFilesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
    }

    // 3. Folder Picker (SAF Directory Tree)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) viewModel.onFolderPicked(treeUri)
    }

    // 4. All Video Apps / System Content Picker
    val allSourcesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
    }

    val isProcessing = activeProject?.status == ProcessingStatus.PROCESSING
    val rankedClips = activeClips.sortedByDescending { it.viralityScore }
    val pickEnabled = !isInspecting && !isProcessing && !isScanningFolder

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
                    Text("Auto Create Studio", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Import from Gallery, Google Drive, Folder, or Files to create 4–5 viral-ready shorts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AiStatusBadge(state = aiAvailability)
            }
        }

        // Multi-source Import Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Import Long Video", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))

                    if (isInspecting || isScanningFolder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (isScanningFolder) "Scanning folder for videos..." else "Loading selected video...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = pickEnabled,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_pick_video_button")
                        ) {
                            Icon(Icons.Rounded.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                driveFilesPickerLauncher.launch(arrayOf("video/*"))
                            },
                            enabled = pickEnabled,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_pick_drive_button")
                        ) {
                            Icon(Icons.Rounded.AddToDrive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Drive & Files", maxLines = 1)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            enabled = pickEnabled,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_pick_folder_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("From Folder", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { allSourcesPickerLauncher.launch("video/*") },
                            enabled = pickEnabled,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_pick_all_button")
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("All Sources", maxLines = 1)
                        }
                    }

                    selectedVideo?.let { video ->
                        Spacer(Modifier.height(14.dp))
                        VideoPlayerPreview(
                            sourcePathOrUri = video.uriString,
                            aspectRatio = AspectRatioPreset.LANDSCAPE_16_9,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = video.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDurationMs(video.durationMs)} • ${video.effectiveWidth}×${video.effectiveHeight}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Preferences Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Clip Duration", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClipDurationPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = durationPreset == preset,
                                onClick = { viewModel.setDurationPreset(preset) },
                                enabled = !isProcessing,
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    if (durationPreset == ClipDurationPreset.CUSTOM) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Custom range: ${customMin}s – ${customMax}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = customMin.toFloat(),
                            onValueChange = { newMin ->
                                viewModel.setCustomRange(newMin.roundToInt(), customMax)
                            },
                            valueRange = 5f..120f,
                            enabled = !isProcessing
                        )
                        Slider(
                            value = customMax.toFloat(),
                            onValueChange = { newMax ->
                                viewModel.setCustomRange(customMin, newMax.roundToInt())
                            },
                            valueRange = (customMin + 5).toFloat()..240f,
                            enabled = !isProcessing
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Target Social Format", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatioPreset.entries.forEach { ratio ->
                            FilterChip(
                                selected = aspectRatio == ratio,
                                onClick = { viewModel.setAspectRatio(ratio) },
                                enabled = !isProcessing,
                                label = { Text(ratio.wireValue) }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Auto-Caption Style", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CaptionStylePreset.entries.forEach { style ->
                            FilterChip(
                                selected = captionStyle == style,
                                onClick = { viewModel.setCaptionStyle(style) },
                                enabled = !isProcessing,
                                label = { Text(style.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { viewModel.startAutoCreate() },
                        enabled = selectedVideo != null && !isProcessing && !isInspecting,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("create_auto_create_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "Generating Clips..." else "Auto Create Viral Shorts (4–5 Clips)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Quota Exhausted Banner
        if (!quotaExhaustedMessage.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.quota_exhausted_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { viewModel.retryActiveProject() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Retry AI Clipping")
                        }
                    }
                }
            }
        }

        // Error Message Banner
        if (!errorMessage.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.clearMessages() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // Active Progress Card
        activeProject?.let { proj ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (proj.status == ProcessingStatus.FAILED) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${proj.status.label}: ${proj.name}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${proj.progressPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (proj.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(proj.statusMessage, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isProcessing) {
                                OutlinedButton(onClick = { viewModel.cancelActiveProcessing() }) {
                                    Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancel")
                                }
                            } else if (proj.status == ProcessingStatus.FAILED || proj.status == ProcessingStatus.PAUSED) {
                                Button(onClick = { viewModel.retryActiveProject() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry AI Clipping")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ranked Clips Result List
        if (rankedClips.isNotEmpty()) {
            item {
                Text(
                    text = "AI-Ranked Clips (${rankedClips.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(rankedClips, key = { it.id }) { clip ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = clip.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            ViralityScorePill(score = clip.viralityScore)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(clip.title, style = MaterialTheme.typography.titleMedium)
                        if (clip.openingHook.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Hook: \"${clip.openingHook}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = clip.viralityExplanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${formatDurationMs(clip.effectiveStartMs)} – ${formatDurationMs(clip.effectiveEndMs)} (${formatDurationMs(clip.durationMs)}) • ${clip.aspectRatio.wireValue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { playingClip = clip },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("play_clip_${clip.id}")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play Clip")
                            }
                            FilledTonalButton(
                                onClick = { onOpenClip(clip.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Adjust")
                            }
                        }
                    }
                }
            }
        } else if (selectedVideo == null && activeProject == null) {
            item {
                EmptyStateCard(
                    title = "Ready when you are",
                    subtitle = "Select any video from Gallery, Google Drive, a Device Folder, or Files. CLIPFREE.AI generates 4–5 viral-ready shorts automatically.",
                    actionLabel = null,
                    onAction = null
                )
            }
        }
    }

    // Folder Video Selector Dialog
    folderVideos?.let { items ->
        AlertDialog(
            onDismissRequest = { viewModel.clearFolderSelection() },
            title = { Text("Select Video from Folder (${items.size})") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(items, key = { it.uri.toString() }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onVideoPicked(item.uri) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sizeMb = if (item.sizeBytes > 0L) "${item.sizeBytes / (1024 * 1024)} MB" else "Video file"
                            Text(
                                text = "$sizeMb • ${item.mimeType}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.clearFolderSelection() }) {
                    Text("Close")
                }
            }
        )
    }

    // Instant Clip Player Dialog
    playingClip?.let { clip ->
        val localRendered = clip.clipFilePath?.let { File(it) }?.takeIf { it.exists() }
        val fallbackUri = activeProject?.sourceUri ?: selectedVideo?.uriString
        if (localRendered != null || !fallbackUri.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { playingClip = null },
                title = {
                    Text(clip.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                text = {
                    VideoPlayerPreview(
                        sourcePathOrUri = localRendered?.absolutePath ?: fallbackUri.orEmpty(),
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
