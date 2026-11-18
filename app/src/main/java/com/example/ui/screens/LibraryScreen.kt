package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.ClipProject
import com.example.domain.model.ExportedVideo
import com.example.domain.model.GeneratedClip
import com.example.domain.model.GeneratedThumbnail
import com.example.media.ShareHelper
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.components.ViralityScorePill
import com.example.ui.components.formatDate
import com.example.ui.components.formatDurationMs
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    clips: List<GeneratedClip>,
    exports: List<ExportedVideo>,
    thumbnails: List<GeneratedThumbnail>,
    projects: List<ClipProject>,
    initialTab: Int = 0,
    onOpenClip: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onDeleteExport: (ExportedVideo) -> Unit,
    onDeleteThumbnail: (GeneratedThumbnail) -> Unit,
    onDeleteProject: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    val tabs = listOf("Clips", "Downloads", "Thumbnails", "Captions", "Projects")

    var previewClip by remember { mutableStateOf<GeneratedClip?>(null) }
    var previewExport by remember { mutableStateOf<ExportedVideo?>(null) }
    var deleteConfirmAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val projectById = remember(projects) { projects.associateBy { it.id } }
    val query = searchQuery.trim().lowercase()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Media Library", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search clips, captions, downloads, projects...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input")
            )
        }

        PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> { // Clips
                    val filtered = clips.filter {
                        query.isBlank() || it.title.lowercase().contains(query) || it.description.lowercase().contains(query)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No generated clips",
                                subtitle = "Run Auto Create on any video and your 4–5 ranked clips will appear here."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { clip ->
                            Card(
                                onClick = { previewClip = clip },
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
                                                .size(64.dp, 86.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(clip.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                        Text(
                                            text = "${formatDurationMs(clip.durationMs)} • ${clip.aspectRatio.wireValue}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        ViralityScorePill(score = clip.viralityScore)
                                    }
                                    IconButton(onClick = { previewClip = clip }) {
                                        Icon(
                                            Icons.Rounded.PlayCircleOutline,
                                            contentDescription = "Play clip",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { onOpenClip(clip.id) }) {
                                        Icon(Icons.Rounded.Tune, contentDescription = "Adjust clip")
                                    }
                                    IconButton(onClick = {
                                        deleteConfirmAction = "Delete clip \"${clip.title}\"?" to { onDeleteClip(clip.id) }
                                    }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete clip", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> { // Downloads
                    val filtered = exports.filter { query.isBlank() || it.title.lowercase().contains(query) }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No exported downloads",
                                subtitle = "Open any generated clip and tap Export MP4 to save share-ready videos."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { exp ->
                            Card(
                                onClick = { previewExport = exp },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exp.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${exp.resolution.label} • ${exp.aspectRatio.wireValue} • ${exp.fileSizeBytes / 1024} KB",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatDate(exp.createdAtMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { previewExport = exp }) {
                                        Icon(
                                            Icons.Rounded.PlayCircleOutline,
                                            contentDescription = "Play export",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = {
                                        val ok = ShareHelper.shareVideoFile(context, File(exp.filePath), exp.title, exp.title)
                                        if (!ok) onMessage("Video file not found on device.")
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share export")
                                    }
                                    IconButton(onClick = {
                                        deleteConfirmAction = "Delete exported MP4 \"${exp.title}\"?" to { onDeleteExport(exp) }
                                    }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete export", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> { // Thumbnails
                    val filtered = thumbnails.filter { query.isBlank() || it.overlayTitle.lowercase().contains(query) }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No custom thumbnails yet",
                                subtitle = "Create custom thumbnails from any clip frame inside the Adjust screen."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { thumb ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = File(thumb.filePath),
                                        contentDescription = thumb.overlayTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp, 86.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(thumb.overlayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                        Text(thumb.aspectRatio.wireValue, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    IconButton(onClick = {
                                        ShareHelper.shareImageFile(context, File(thumb.filePath), thumb.overlayTitle)
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share thumbnail")
                                    }
                                    IconButton(onClick = {
                                        deleteConfirmAction = "Delete thumbnail?" to { onDeleteThumbnail(thumb) }
                                    }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete thumbnail", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> { // Captions
                    val filtered = clips.filter {
                        query.isBlank() || it.title.lowercase().contains(query) ||
                            it.captions.any { seg -> seg.text.lowercase().contains(query) }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No transcripts yet",
                                subtitle = "Auto-generated clip captions and social copy appear here."
                            )
                        }
                    } else {
                        items(filtered, key = { "cap_${it.id}" }) { clip ->
                            val transcriptText = clip.captions.joinToString("\n") { it.text }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(clip.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            ShareHelper.copyToClipboard(context, clip.title, transcriptText)
                                            onMessage("Captions copied to clipboard.")
                                        }) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy transcript")
                                        }
                                    }
                                    Text(
                                        text = transcriptText.ifBlank { clip.socialCaption },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                4 -> { // Projects
                    val filtered = projects.filter { query.isBlank() || it.name.lowercase().contains(query) }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No matching projects",
                                subtitle = "Your saved projects are stored locally on your device."
                            )
                        }
                    } else {
                        items(filtered, key = { "libproj_${it.id}" }) { proj ->
                            Card(
                                onClick = { onOpenProject(proj.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(proj.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${proj.clipCount} clips • ${formatDate(proj.createdAtMs)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        deleteConfirmAction = "Delete project \"${proj.name}\"?" to { onDeleteProject(proj.id) }
                                    }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete project", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewClip?.let { clip ->
        val localRendered = clip.clipFilePath?.let { File(it) }?.takeIf { it.exists() }
        val sourceUri = projectById[clip.projectId]?.sourceUri
        if (localRendered != null || !sourceUri.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { previewClip = null },
                title = { Text(clip.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    VideoPlayerPreview(
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
                        previewClip = null
                        onOpenClip(clip.id)
                    }) {
                        Text("Adjust & Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { previewClip = null }) {
                        Text("Close")
                    }
                }
            )
        } else {
            onMessage("Video source is missing on this device.")
            previewClip = null
        }
    }

    previewExport?.let { exp ->
        AlertDialog(
            onDismissRequest = { previewExport = null },
            title = { Text(exp.title, maxLines = 1) },
            text = {
                VideoPlayerPreview(
                    sourcePathOrUri = exp.filePath,
                    aspectRatio = exp.aspectRatio,
                    isLocalFile = true,
                    autoPlay = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    ShareHelper.shareVideoFile(context, File(exp.filePath), exp.title, exp.title)
                }) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewExport = null }) {
                    Text("Close")
                }
            }
        )
    }

    deleteConfirmAction?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { deleteConfirmAction = null },
            title = { Text("Confirm Deletion") },
            text = { Text(message) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        action()
                        deleteConfirmAction = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
