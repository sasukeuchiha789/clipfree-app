package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.AccountSession
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.ProviderStatus
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.ui.components.AiStatusBadge
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    session: AccountSession,
    preferences: UserPreferences,
    aiAvailability: AiAvailabilityState,
    onSignInGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onMessage: (String) -> Unit
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val storageUsedMb by viewModel.storageUsedMb.collectAsStateWithLifecycle()
    val availableMb by viewModel.availableMb.collectAsStateWithLifecycle()
    val testingProviderId by viewModel.testingProviderId.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showCustomProviderDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showClearMediaDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            onMessage(it)
            viewModel.clearStatusBanner()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // Account Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (session) {
                            is AccountSession.GoogleUser -> {
                                if (!session.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = session.photoUrl,
                                        contentDescription = session.displayName,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.padding(14.dp))
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session.displayName, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = session.email ?: "Signed in with Google",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.padding(14.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Guest (Local Studio)", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = "Projects and clips stay saved locally on this device.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    if (session is AccountSession.GoogleUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                            OutlinedButton(
                                onClick = { showDeleteAccountDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete Account")
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onSignInGoogle,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_google_signin_button")
                            ) {
                                Text("Sign In with Google")
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Exit Guest Mode")
                            }
                        }
                    }
                }
            }
        }

        // Theme & Language
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Caption & AI Language", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val languages = listOf("English", "Spanish", "French", "German", "Hindi", "Japanese")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = preferences.language == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                label = { Text(lang) }
                            )
                        }
                    }
                }
            }
        }

        // AI Usage & Multi-Provider Switching Dashboard
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("AI Usage & Multi-Provider", style = MaterialTheme.typography.titleMedium)
                        }
                        AiStatusBadge(state = aiAvailability)
                    }

                    preferences.lastSwitchEvent?.let { sw ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Last failover: ${sw.fromProvider} → ${sw.toProvider} (${sw.reason})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    providers.forEach { provider ->
                        val isActive = preferences.activeProviderId == provider.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${provider.priority}. ${provider.name}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${provider.modelName} • ${provider.status.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (provider.status == ProviderStatus.QUOTA_EXHAUSTED) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (testingProviderId == provider.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = { viewModel.testProvider(provider) }) {
                                        Text("Test")
                                    }
                                    TextButton(onClick = { viewModel.setActiveProvider(provider.id) }) {
                                        Text(if (isActive) "Active" else "Use")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCustomProviderDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Custom HTTPS")
                        }
                        OutlinedButton(
                            onClick = { viewModel.refreshProviderAvailability() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh Status")
                        }
                    }
                }
            }
        }

        // Privacy & Smart AI Caching
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Privacy & Performance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart AI Caching", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Reuse local transcript & highlight analysis to save free AI quota",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.smartAiCachingEnabled,
                            onCheckedChange = { viewModel.setSmartAiCaching(it) }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cloud Sync (Google Account)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Sync project & clip metadata to Firebase Firestore when signed in",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.cloudSyncConsent,
                            enabled = session is AccountSession.GoogleUser,
                            onCheckedChange = { viewModel.setCloudSyncConsent(it) }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Processing Consent", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Allow sending extracted audio & sample frames for AI highlight detection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.externalAiProcessingConsent,
                            onCheckedChange = { viewModel.setExternalAiConsent(it) }
                        )
                    }
                }
            }
        }

        // Storage & About
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Storage & Cache", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "CLIPFREE.AI media: $storageUsedMb MB • Device free space: $availableMb MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.clearTranscriptCache() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear AI Cache")
                        }
                        OutlinedButton(
                            onClick = { showClearMediaDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Media")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("About CLIPFREE.AI", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Long Videos In. Viral-Ready Shorts Out. Real on-device Media3 rendering + ML Kit subject framing + Firebase AI Logic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showCustomProviderDialog) {
        val existing = providers.firstOrNull { it.id == "custom_compatible_backend" }
        var endpoint by remember { mutableStateOf(existing?.endpointUrl ?: "") }
        var modelName by remember { mutableStateOf(existing?.modelName ?: "gpt-4o-mini") }
        var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
        var enabled by remember { mutableStateOf(existing?.isEnabled ?: true) }

        AlertDialog(
            onDismissRequest = { showCustomProviderDialog = false },
            title = { Text("Secondary HTTPS Provider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Optional OpenAI-compatible chat completions endpoint used automatically if free Gemini quota is exhausted.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("HTTPS Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key (stored locally)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable automatic fallback")
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCustomProvider(endpoint, modelName, apiKey, enabled)
                    showCustomProviderDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomProviderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Google Account Data?") },
            text = { Text("This deletes your Firebase account and cloud-synced metadata. Local projects on your device remain intact unless cleared manually.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearMediaDialog) {
        AlertDialog(
            onDismissRequest = { showClearMediaDialog = false },
            title = { Text("Clear All Local Media?") },
            text = { Text("Removes all CLIPFREE.AI projects, generated clips, thumbnails, and exported MP4 files from app storage.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showClearMediaDialog = false
                        viewModel.clearAllLocalMedia()
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMediaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
