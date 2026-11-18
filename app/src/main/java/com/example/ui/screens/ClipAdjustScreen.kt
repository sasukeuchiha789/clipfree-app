package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionAnimation
import com.example.domain.model.CaptionFontStyle
import com.example.domain.model.CaptionPosition
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ExportResolution
import com.example.media.ShareHelper
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.components.ViralityScorePill
import com.example.ui.components.formatDurationMs
import com.example.ui.viewmodel.ClipAdjustViewModel
import java.io.File

private val CAPTION_COLOR_SWATCHES = listOf(
    0xFFFFFFFFL,
    0xFFFFE066L,
    0xFF9878FFL,
    0xFF2BD98DL,
    0xFFFF6B8AL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipAdjustScreen(
    viewModel: ClipAdjustViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clip by viewModel.clip.collectAsStateWithLifecycle()
    val project by viewModel.project.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val candidateFrames by viewModel.candidateFrames.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val availableResolutions by viewModel.availableResolutions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Captions", "Reframe", "Trim & Info", "Thumbnail", "Export")

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            onMessage(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = clip?.title ?: "Adjust & Export",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("adjust_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    clip?.let { c ->
                        ViralityScorePill(score = c.viralityScore, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentClip = clip
        val currentProject = project

        if (currentClip == null || currentProject == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val renderedFile = currentClip.clipFilePath?.let { File(it) }?.takeIf { it.exists() }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Video preview
            item {
                VideoPlayerPreview(
                    sourcePathOrUri = renderedFile?.absolutePath ?: currentProject.sourceUri,
                    aspectRatio = currentClip.aspectRatio,
                    clipStartMs = currentClip.effectiveStartMs,
                    clipEndMs = currentClip.effectiveEndMs,
                    isLocalFile = renderedFile != null,
                    autoPlay = true,
                    adjustment = currentClip.adjustment,
                    captions = currentClip.captions,
                    showCaptionOverlay = renderedFile == null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (exportProgress != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Rendering MP4 (${exportProgress}%)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (exportProgress ?: 0) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }

            // Tab Strip
            item {
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> { // Captions
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Caption Preset", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionStylePreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = currentClip.adjustment.captionStyle == preset,
                                            onClick = { viewModel.updateCaptionStyle(style = preset) },
                                            label = { Text(preset.label) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text("Position & Animation", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CaptionPosition.entries.forEach { pos ->
                                        FilterChip(
                                            selected = currentClip.adjustment.captionPosition == pos,
                                            onClick = { viewModel.updateCaptionStyle(position = pos) },
                                            label = { Text(pos.label) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionAnimation.entries.forEach { anim ->
                                        FilterChip(
                                            selected = currentClip.adjustment.captionAnimation == anim,
                                            onClick = { viewModel.updateCaptionStyle(animation = anim) },
                                            label = { Text(anim.label) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text("Font Style", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionFontStyle.entries.forEach { fs ->
                                        FilterChip(
                                            selected = currentClip.adjustment.captionFontStyle == fs,
                                            onClick = { viewModel.updateCaptionStyle(fontStyle = fs) },
                                            label = { Text(fs.label) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Font Size (${currentClip.adjustment.captionSizeSp.toInt()}sp)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Slider(
                                    value = currentClip.adjustment.captionSizeSp,
                                    onValueChange = { viewModel.updateCaptionStyle(sizeSp = it) },
                                    valueRange = 14f..34f
                                )

                                Text("Caption Color", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CAPTION_COLOR_SWATCHES.forEach { colorHex ->
                                        val selected = currentClip.adjustment.captionColorHex == colorHex
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorHex))
                                                .border(
                                                    width = if (selected) 3.dp else 1.dp,
                                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.updateCaptionStyle(colorHex = colorHex) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Edit Synced Caption Lines", style = MaterialTheme.typography.titleMedium)
                    }
                    items(currentClip.captions, key = { it.id }) { seg ->
                        var textValue by remember(seg.id, seg.text) { mutableStateOf(seg.text) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${formatDurationMs(seg.startMs)} – ${formatDurationMs(seg.endMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = {
                                        textValue = it
                                        viewModel.updateCaptionText(seg.id, it)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                1 -> { // Reframe
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Subject Framing & Aspect Ratio", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = currentClip.adjustment.faceTrackingStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AspectRatioPreset.entries.forEach { ratio ->
                                        FilterChip(
                                            selected = currentClip.aspectRatio == ratio,
                                            onClick = { viewModel.updateFraming(aspectRatio = ratio) },
                                            label = { Text(ratio.wireValue) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                Text("Horizontal Position", style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = currentClip.adjustment.cropOffsetX,
                                    onValueChange = { viewModel.updateFraming(cropOffsetX = it) },
                                    valueRange = -1f..1f
                                )
                                Text("Vertical Position", style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = currentClip.adjustment.cropOffsetY,
                                    onValueChange = { viewModel.updateFraming(cropOffsetY = it) },
                                    valueRange = -1f..1f
                                )
                                Text("Zoom (${String.format("%.1fx", currentClip.adjustment.zoomScale)})", style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = currentClip.adjustment.zoomScale,
                                    onValueChange = { viewModel.updateFraming(zoomScale = it) },
                                    valueRange = 1.0f..2.2f
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateFraming(cropOffsetX = 0f, cropOffsetY = 0f, zoomScale = 1.0f)
                                    }
                                ) {
                                    Text("Reset Subject Framing")
                                }
                            }
                        }
                    }
                }

                2 -> { // Trim & Info
                    item {
                        var titleText by remember(currentClip.id, currentClip.title) { mutableStateOf(currentClip.title) }
                        var hookText by remember(currentClip.id, currentClip.openingHook) { mutableStateOf(currentClip.openingHook) }
                        var descText by remember(currentClip.id, currentClip.description) { mutableStateOf(currentClip.description) }
                        var socialText by remember(currentClip.id, currentClip.socialCaption) { mutableStateOf(currentClip.socialCaption) }
                        var hashtagsText by remember(currentClip.id, currentClip.hashtags) {
                            mutableStateOf(currentClip.hashtags.joinToString(" "))
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Quick Trim Nudge", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${formatDurationMs(currentClip.effectiveStartMs)} – ${formatDurationMs(currentClip.effectiveEndMs)} (${formatDurationMs(currentClip.durationMs)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.nudgeStartEnd(-1000L, 0L) }, modifier = Modifier.weight(1f)) {
                                        Text("Start -1s")
                                    }
                                    OutlinedButton(onClick = { viewModel.nudgeStartEnd(1000L, 0L) }, modifier = Modifier.weight(1f)) {
                                        Text("Start +1s")
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.nudgeStartEnd(0L, -1000L) }, modifier = Modifier.weight(1f)) {
                                        Text("End -1s")
                                    }
                                    OutlinedButton(onClick = { viewModel.nudgeStartEnd(0L, 1000L) }, modifier = Modifier.weight(1f)) {
                                        Text("End +1s")
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Title, Hook & Social Copy", style = MaterialTheme.typography.titleMedium)
                                    OutlinedButton(
                                        onClick = { viewModel.regenerateMetadataWithAi() },
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("AI Refresh")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = titleText,
                                    onValueChange = { titleText = it },
                                    label = { Text("Clip Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = hookText,
                                    onValueChange = { hookText = it },
                                    label = { Text("Opening Hook") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = descText,
                                    onValueChange = { descText = it },
                                    label = { Text("Description") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = socialText,
                                    onValueChange = { socialText = it },
                                    label = { Text("Social Caption") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = hashtagsText,
                                    onValueChange = { hashtagsText = it },
                                    label = { Text("Hashtags") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateMetadata(titleText, descText, hookText, socialText, hashtagsText)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Details")
                                }
                            }
                        }
                    }
                }

                3 -> { // Thumbnail
                    item {
                        var selectedFrameTs by remember(candidateFrames) {
                            mutableLongStateOf(candidateFrames.firstOrNull()?.first ?: currentClip.effectiveStartMs)
                        }
                        var overlayTitle by remember(currentClip.id) { mutableStateOf(currentClip.title) }
                        var thumbColor by remember { mutableLongStateOf(0xFFFFFFFFL) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Frame-Based Thumbnail Studio", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                if (candidateFrames.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(candidateFrames, key = { it.first }) { (ts, path) ->
                                            val isPicked = selectedFrameTs == ts
                                            AsyncImage(
                                                model = File(path),
                                                contentDescription = "Frame at ${formatDurationMs(ts)}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(90.dp, 120.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        width = if (isPicked) 3.dp else 1.dp,
                                                        color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable { selectedFrameTs = ts }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                                OutlinedTextField(
                                    value = overlayTitle,
                                    onValueChange = { overlayTitle = it },
                                    label = { Text("Thumbnail Headline") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CAPTION_COLOR_SWATCHES.forEach { colorHex ->
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorHex))
                                                .border(
                                                    width = if (thumbColor == colorHex) 3.dp else 1.dp,
                                                    color = if (thumbColor == colorHex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                )
                                                .clickable { thumbColor = colorHex }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        viewModel.generateCustomThumbnail(selectedFrameTs, overlayTitle, thumbColor)
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Generate Thumbnail")
                                }
                            }
                        }
                    }

                    if (thumbnails.isNotEmpty()) {
                        items(thumbnails, key = { it.id }) { thumb ->
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
                                        Text(
                                            text = thumb.aspectRatio.wireValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        viewModel.downloadThumbnailToGallery(thumb.filePath, thumb.overlayTitle)
                                    }) {
                                        Icon(Icons.Rounded.Download, contentDescription = "Save thumbnail")
                                    }
                                    IconButton(onClick = {
                                        ShareHelper.shareImageFile(context, File(thumb.filePath), thumb.overlayTitle)
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share thumbnail")
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> { // Export & Share
                    item {
                        var selectedResolution by remember(availableResolutions) {
                            mutableStateOf(
                                availableResolutions.firstOrNull { it == ExportResolution.FHD_1080P }
                                    ?: availableResolutions.first()
                            )
                        }
                        var saveToMovies by remember { mutableStateOf(true) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Export Quality", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableResolutions.forEach { res ->
                                        FilterChip(
                                            selected = selectedResolution == res,
                                            onClick = { selectedResolution = res },
                                            label = { Text(res.label) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Save to Movies/ClipFreeAI", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Makes MP4 visible in Android Gallery & social apps",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = saveToMovies,
                                        onCheckedChange = { saveToMovies = it }
                                    )
                                }

                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = { viewModel.exportMp4(selectedResolution, saveToMovies) },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("export_mp4_button")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Export ${selectedResolution.label} MP4")
                                }

                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.regenerateClipPreview() },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Regenerate Rendered Clip")
                                }

                                Spacer(Modifier.height(16.dp))
                                Text("Share & Social Copy", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))

                                val fullCaption = buildString {
                                    append(currentClip.socialCaption.ifBlank { currentClip.title })
                                    if (currentClip.hashtags.isNotEmpty()) {
                                        append("\n\n")
                                        append(currentClip.hashtags.joinToString(" "))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            ShareHelper.copyToClipboard(context, "ClipFree Caption", fullCaption)
                                            onMessage("Caption & hashtags copied.")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy Copy")
                                    }
                                    Button(
                                        onClick = {
                                            val f = currentClip.clipFilePath?.let { File(it) }
                                            if (f != null && f.exists()) {
                                                ShareHelper.shareVideoFile(context, f, currentClip.title, fullCaption)
                                            } else {
                                                onMessage("Export or regenerate the MP4 first before sharing.")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("System Share")
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val targets = listOf(
                                        "YouTube" to "com.google.android.youtube",
                                        "Instagram" to "com.instagram.android",
                                        "TikTok" to "com.zhiliaoapp.musically",
                                        "Facebook" to "com.facebook.katana"
                                    )
                                    targets.forEach { (label, pkg) ->
                                        OutlinedButton(
                                            onClick = {
                                                val f = currentClip.clipFilePath?.let { File(it) }
                                                if (f != null && f.exists()) {
                                                    ShareHelper.shareVideoFile(
                                                        context = context,
                                                        file = f,
                                                        title = currentClip.title,
                                                        caption = fullCaption,
                                                        targetPackage = pkg
                                                    )
                                                } else {
                                                    onMessage("Export the MP4 clip first to share to $label.")
                                                }
                                            }
                                        ) {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
