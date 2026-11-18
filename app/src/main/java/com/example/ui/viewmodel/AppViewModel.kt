package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiProviderRouter
import com.example.data.local.PreferencesManager
import com.example.data.remote.firebase.AuthCancelledException
import com.example.data.remote.firebase.AuthManager
import com.example.data.remote.firebase.CloudSyncRepository
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AccountSession
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.ClipProject
import com.example.domain.model.ExportedVideo
import com.example.domain.model.GeneratedClip
import com.example.domain.model.GeneratedThumbnail
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val authManager: AuthManager,
    private val preferencesManager: PreferencesManager,
    private val repository: ProjectRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val aiRouter: AiProviderRouter
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val session: StateFlow<AccountSession> = combine(
        authManager.observeFirebaseUser(),
        preferencesManager.preferencesFlow
    ) { firebaseUser, prefs ->
        when {
            firebaseUser != null -> AccountSession.GoogleUser(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "ClipFree Creator",
                email = firebaseUser.email,
                photoUrl = firebaseUser.photoUrl?.toString()
            )
            prefs.isGuestModeActive -> AccountSession.Guest
            else -> AccountSession.Unauthenticated
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AccountSession.Unauthenticated)

    val projects: StateFlow<List<ClipProject>> = repository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allClips: StateFlow<List<GeneratedClip>> = repository.allClips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allExports: StateFlow<List<ExportedVideo>> = repository.allExports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allThumbnails: StateFlow<List<GeneratedThumbnail>> = repository.allThumbnails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val aiAvailability: StateFlow<AiAvailabilityState> = aiRouter.availabilityState

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultProviders()
            if (!aiRouter.isOnline()) {
                aiRouter.setAvailabilityState(AiAvailabilityState.OFFLINE_MODE)
            }
            authManager.attemptSilentAutoSignIn()
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val result = authManager.signInWithGoogle(activityContext)
            _authLoading.value = false
            result.onSuccess {
                preferencesManager.setGuestMode(false)
                _toastMessage.value = "Signed in as ${it.displayName ?: it.email ?: "Creator"}"
            }.onFailure { err ->
                if (err is AuthCancelledException) {
                    _authError.value = null
                } else {
                    _authError.value = err.localizedMessage ?: "Google Sign-In failed."
                }
            }
        }
    }

    fun enterGuestMode() {
        viewModelScope.launch {
            preferencesManager.setGuestMode(true)
            _authError.value = null
        }
    }

    fun signOut() {
        viewModelScope.launch {
            preferencesManager.setGuestMode(false)
            authManager.signOut()
            _toastMessage.value = "Signed out. Local projects are preserved on this device."
        }
    }

    fun deleteGoogleAccount() {
        viewModelScope.launch {
            _authLoading.value = true
            cloudSyncRepository.deleteAllUserCloudData()
            val res = authManager.deleteAccount()
            _authLoading.value = false
            res.onSuccess {
                preferencesManager.setCloudSyncConsent(false)
                preferencesManager.setGuestMode(false)
                _toastMessage.value = "Account and cloud-synced metadata deleted."
            }.onFailure { err ->
                _toastMessage.value = "Account deletion requires recent sign-in: ${err.localizedMessage}"
            }
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            repository.renameProject(projectId, newName)
            syncProjectIfConsented(projectId)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (preferences.value.cloudSyncConsent && session.value is AccountSession.GoogleUser) {
                cloudSyncRepository.deleteCloudProject(projectId)
            }
            _toastMessage.value = "Project deleted."
        }
    }

    fun deleteClip(clipId: String) {
        viewModelScope.launch {
            repository.deleteClip(clipId)
            _toastMessage.value = "Clip deleted."
        }
    }

    fun deleteExport(export: ExportedVideo) {
        viewModelScope.launch {
            repository.deleteExport(export.id, export.filePath)
            _toastMessage.value = "Exported video removed."
        }
    }

    fun deleteThumbnail(thumbnail: GeneratedThumbnail) {
        viewModelScope.launch {
            repository.deleteThumbnail(thumbnail.id, thumbnail.filePath)
            _toastMessage.value = "Thumbnail deleted."
        }
    }

    fun syncProjectIfConsented(projectId: String) {
        viewModelScope.launch {
            if (!preferences.value.cloudSyncConsent) return@launch
            if (session.value !is AccountSession.GoogleUser) return@launch
            try {
                val project = repository.getProject(projectId) ?: return@launch
                val clips = repository.getClipsForProject(projectId)
                val res = cloudSyncRepository.syncProjectAndClips(project, clips)
                if (res.isSuccess) {
                    repository.upsertProject(project.copy(cloudSynced = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
