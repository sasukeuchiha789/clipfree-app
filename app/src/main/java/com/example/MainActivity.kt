package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.remote.firebase.configureAppCheck
import com.example.di.ClipFreeContainer
import com.example.ui.navigation.ClipFreeApp
import com.example.ui.theme.ClipFreeTheme
import com.example.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val container = ClipFreeContainer.getInstance(applicationContext)

        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AppViewModel(
                            authManager = container.authManager,
                            preferencesManager = container.preferencesManager,
                            repository = container.projectRepository,
                            cloudSyncRepository = container.cloudSyncRepository,
                            aiRouter = container.aiProviderRouter
                        ) as T
                    }
                }
            )
            val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            ClipFreeTheme(themeMode = preferences.themeMode) {
                ClipFreeApp(
                    container = container,
                    appViewModel = appViewModel,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }
}
