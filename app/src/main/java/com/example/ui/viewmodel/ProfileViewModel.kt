package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiProviderRouter
import com.example.data.local.PreferencesManager
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.AiProviderConfig
import com.example.domain.model.ProviderStatus
import com.example.domain.model.ThemeMode
import com.example.media.DeviceCapabilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileViewModel(
    private val appContext: Context,
    private val repository: ProjectRepository,
    private val preferencesManager: PreferencesManager,
    private val aiRouter: AiProviderRouter
) : ViewModel() {

    val providers: StateFlow<List<AiProviderConfig>> = repository.providers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _storageUsedMb = MutableStateFlow(0L)
    val storageUsedMb: StateFlow<Long> = _storageUsedMb.asStateFlow()

    private val _availableMb = MutableStateFlow(0L)
    val availableMb: StateFlow<Long> = _availableMb.asStateFlow()

    private val _testingProviderId = MutableStateFlow<String?>(null)
    val testingProviderId: StateFlow<String?> = _testingProviderId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        refreshStorageStats()
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { preferencesManager.setLanguage(lang) }
    }

    fun setSmartAiCaching(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSmartAiCaching(enabled) }
    }

    fun setCloudSyncConsent(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setCloudSyncConsent(enabled) }
    }

    fun setExternalAiConsent(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setExternalAiConsent(enabled) }
    }

    fun testProvider(provider: AiProviderConfig) {
        viewModelScope.launch {
            _testingProviderId.value = provider.id
            val res = aiRouter.testProviderConnection(provider)
            _testingProviderId.value = null
            _statusBanner.value = res.fold(
                onSuccess = { "${provider.name}: $it" },
                onFailure = { "${provider.name} test failed: ${it.localizedMessage}" }
            )
        }
    }

    fun saveCustomProvider(endpointUrl: String, modelName: String, apiKey: String, enabled: Boolean) {
        viewModelScope.launch {
            val cleanUrl = endpointUrl.trim()
            val cleanModel = modelName.trim().ifBlank { "gpt-4o-mini" }
            val cleanKey = apiKey.trim()
            val configured = cleanUrl.startsWith("https://") && cleanKey.isNotBlank()
            val status = if (configured) ProviderStatus.AVAILABLE else ProviderStatus.UNCONFIGURED
            repository.upsertProvider(
                AiProviderConfig(
                    id = "custom_compatible_backend",
                    name = "Custom Authorized Provider",
                    modelName = cleanModel,
                    providerType = "CUSTOM_COMPATIBLE",
                    priority = 3,
                    status = status,
                    isEnabled = enabled && configured,
                    endpointUrl = cleanUrl,
                    apiKey = cleanKey
                )
            )
            _statusBanner.value = if (configured) {
                "Secondary AI provider saved."
            } else {
                "Enter a valid HTTPS endpoint and API key to enable."
            }
        }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch {
            preferencesManager.setActiveProvider(providerId)
            _statusBanner.value = "Preferred AI provider updated."
        }
    }

    fun refreshProviderAvailability() {
        viewModelScope.launch {
            val all = repository.getProviders()
            all.filter { it.providerType == "FIREBASE_AI_LOGIC" }.forEach {
                repository.updateProviderStatus(it.id, ProviderStatus.AVAILABLE, null)
            }
            aiRouter.setAvailabilityState(
                if (aiRouter.isOnline()) AiAvailabilityState.AI_AVAILABLE else AiAvailabilityState.OFFLINE_MODE
            )
            _statusBanner.value = "Provider status refreshed."
        }
    }

    fun clearTranscriptCache() {
        viewModelScope.launch {
            repository.transcriptCacheDao.clearCache()
            _statusBanner.value = "Smart AI transcript cache cleared."
        }
    }

    fun clearAllLocalMedia() {
        viewModelScope.launch {
            repository.clearAllLocalProjectsAndMedia(appContext)
            refreshStorageStats()
            _statusBanner.value = "All local projects and exported files removed."
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val usedBytes = withContext(Dispatchers.IO) {
                listOf("clips", "exports", "thumbnails").sumOf { folder ->
                    val dir = File(appContext.filesDir, folder)
                    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
            }
            _storageUsedMb.value = usedBytes / (1024L * 1024L)
            _availableMb.value = DeviceCapabilityChecker.availableStorageBytes(appContext) / (1024L * 1024L)
        }
    }
}
