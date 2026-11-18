package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.ai.AiProviderRouter
import com.example.data.local.PreferencesManager
import com.example.data.remote.firebase.CloudSyncRepository
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.CaptionStylePreset
import com.example.domain.model.ClipDurationPreset
import com.example.domain.model.ClipProject
import com.example.domain.model.GeneratedClip
import com.example.domain.model.ProcessingStatus
import com.example.domain.model.VideoMetadata
import com.example.media.ClipGenerationWorker
import com.example.media.VideoInspector
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CreateViewModel(
    private val appContext: Context,
    private val repository: ProjectRepository,
    private val workManager: WorkManager,
    private val preferencesManager: PreferencesManager,
    private val cloudSyncRepository: CloudSyncRepository,
    private val aiRouter: AiProviderRouter
) : ViewModel() {

    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _durationPreset = MutableStateFlow(ClipDurationPreset.SHORT_15_30)
    val durationPreset: StateFlow<ClipDurationPreset> = _durationPreset.asStateFlow()

    private val _customMinSeconds = MutableStateFlow(20)
    val customMinSeconds: StateFlow<Int> = _customMinSeconds.asStateFlow()

    private val _customMaxSeconds = MutableStateFlow(50)
    val customMaxSeconds: StateFlow<Int> = _customMaxSeconds.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioPreset.VERTICAL_9_16)
    val aspectRatio: StateFlow<AspectRatioPreset> = _aspectRatio.asStateFlow()

    private val _captionStyle = MutableStateFlow(CaptionStylePreset.BOLD_CREATOR)
    val captionStyle: StateFlow<CaptionStylePreset> = _captionStyle.asStateFlow()

    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _quotaExhaustedMessage = MutableStateFlow<String?>(null)
    val quotaExhaustedMessage: StateFlow<String?> = _quotaExhaustedMessage.asStateFlow()

    private val _folderVideos = MutableStateFlow<List<com.example.media.FolderVideoItem>?>(null)
    val folderVideos: StateFlow<List<com.example.media.FolderVideoItem>?> = _folderVideos.asStateFlow()

    private val _isScanningFolder = MutableStateFlow(false)
    val isScanningFolder: StateFlow<Boolean> = _isScanningFolder.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeProject: StateFlow<ClipProject?> = _activeProjectId
        .flatMapLatest { pid -> if (pid == null) flowOf(null) else repository.observeProject(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeClips: StateFlow<List<GeneratedClip>> = _activeProjectId
        .flatMapLatest { pid -> if (pid == null) flowOf(emptyList()) else repository.observeClipsForProject(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            _isInspecting.value = true
            _errorMessage.value = null
            _quotaExhaustedMessage.value = null
            _folderVideos.value = null

            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val inspected = VideoInspector.inspectVideo(appContext, uri)
            _isInspecting.value = false
            inspected.onSuccess { meta ->
                _selectedVideo.value = meta
                _activeProjectId.value = null
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Unable to open selected video."
            }
        }
    }

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            _isScanningFolder.value = true
            _errorMessage.value = null
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val items = VideoInspector.listVideosInTreeFolder(appContext, treeUri)
            _isScanningFolder.value = false
            if (items.isEmpty()) {
                _errorMessage.value = "No video files found in the selected folder."
                _folderVideos.value = null
            } else {
                _folderVideos.value = items
            }
        }
    }

    fun clearFolderSelection() {
        _folderVideos.value = null
    }

    fun setDurationPreset(preset: ClipDurationPreset) {
        _durationPreset.value = preset
    }

    fun setCustomRange(minSec: Int, maxSec: Int) {
        val clampedMin = minSec.coerceIn(5, 180)
        val clampedMax = maxSec.coerceIn(clampedMin + 5, 240)
        _customMinSeconds.value = clampedMin
        _customMaxSeconds.value = clampedMax
    }

    fun setAspectRatio(ratio: AspectRatioPreset) {
        _aspectRatio.value = ratio
    }

    fun setCaptionStyle(style: CaptionStylePreset) {
        _captionStyle.value = style
    }

    fun openExistingProject(projectId: String) {
        viewModelScope.launch {
            _activeProjectId.value = projectId
            val proj = repository.getProject(projectId)
            if (proj != null) {
                _selectedVideo.value = VideoMetadata(
                    uriString = proj.sourceUri,
                    displayName = proj.sourceFileName,
                    durationMs = proj.sourceDurationMs,
                    width = proj.sourceWidth,
                    height = proj.sourceHeight,
                    rotationDegrees = 0,
                    mimeType = "video/mp4",
                    fileSizeBytes = 0L,
                    thumbnailPath = proj.thumbnailPath
                )
                _aspectRatio.value = proj.aspectRatio
                _durationPreset.value = proj.durationPreset
                _customMinSeconds.value = proj.customMinSeconds
                _customMaxSeconds.value = proj.customMaxSeconds
            }
        }
    }

    fun startAutoCreate() {
        val video = _selectedVideo.value ?: run {
            _errorMessage.value = "Please import a video first."
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _quotaExhaustedMessage.value = null

            val now = System.currentTimeMillis()
            val projectId = _activeProjectId.value ?: "proj_${UUID.randomUUID().toString().take(8)}"
            val cleanName = video.displayName.substringBeforeLast('.').take(60).ifBlank { "New Short Project" }

            val project = ClipProject(
                id = projectId,
                name = cleanName,
                sourceUri = video.uriString,
                sourceFileName = video.displayName,
                sourceDurationMs = video.durationMs,
                sourceWidth = video.effectiveWidth,
                sourceHeight = video.effectiveHeight,
                thumbnailPath = video.thumbnailPath,
                aspectRatio = _aspectRatio.value,
                durationPreset = _durationPreset.value,
                customMinSeconds = _customMinSeconds.value,
                customMaxSeconds = _customMaxSeconds.value,
                status = ProcessingStatus.PROCESSING,
                statusMessage = "Starting Auto Create pipeline...",
                progressPercent = 4,
                clipCount = 0,
                detectedLanguage = preferencesManager.preferencesFlow.first().language,
                cloudSynced = false,
                createdAtMs = now,
                updatedAtMs = now
            )
            repository.upsertProject(project)
            _activeProjectId.value = projectId
            enqueueWorker(projectId)
        }
    }

    fun retryActiveProject() {
        val pid = _activeProjectId.value ?: return
        viewModelScope.launch {
            _errorMessage.value = null
            _quotaExhaustedMessage.value = null
            aiRouter.setAvailabilityState(AiAvailabilityState.READY_TO_RETRY)
            repository.updateProjectProgress(pid, ProcessingStatus.PROCESSING, "Retrying AI clipping...", 5, 0)
            enqueueWorker(pid)
        }
    }

    fun cancelActiveProcessing() {
        val pid = _activeProjectId.value ?: return
        workManager.cancelUniqueWork("clipfree_work_$pid")
        viewModelScope.launch {
            repository.updateProjectProgress(
                pid,
                ProcessingStatus.PAUSED,
                "Cancelled by user. Saved work preserved.",
                0,
                repository.getClipsForProject(pid).size
            )
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _quotaExhaustedMessage.value = null
    }

    private fun enqueueWorker(projectId: String) {
        val request = OneTimeWorkRequestBuilder<ClipGenerationWorker>()
            .setInputData(
                workDataOf(
                    ClipGenerationWorker.KEY_PROJECT_ID to projectId,
                    ClipGenerationWorker.KEY_CAPTION_STYLE to _captionStyle.value.name
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            "clipfree_work_$projectId",
            ExistingWorkPolicy.REPLACE,
            request
        )

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collectLatest { info ->
                if (info == null) return@collectLatest
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val prefs = preferencesManager.preferencesFlow.first()
                        if (prefs.cloudSyncConsent && Firebase.auth.currentUser != null) {
                            val p = repository.getProject(projectId)
                            val c = repository.getClipsForProject(projectId)
                            if (p != null) {
                                val syncRes = cloudSyncRepository.syncProjectAndClips(p, c)
                                if (syncRes.isSuccess) {
                                    repository.upsertProject(p.copy(cloudSynced = true))
                                }
                            }
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(ClipGenerationWorker.KEY_ERROR_MESSAGE)
                        val isQuota = info.outputData.getBoolean(ClipGenerationWorker.KEY_QUOTA_EXHAUSTED, false)
                        if (isQuota) {
                            _quotaExhaustedMessage.value = err
                        } else if (!err.isNullOrBlank()) {
                            _errorMessage.value = err
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
