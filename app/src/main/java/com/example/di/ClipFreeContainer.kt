package com.example.di

import android.content.Context
import androidx.work.WorkManager
import com.example.data.ai.AiProviderRouter
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.firebase.AuthManager
import com.example.data.remote.firebase.CloudSyncRepository
import com.example.data.repository.ProjectRepository

class ClipFreeContainer(appContext: Context) {
    val applicationContext: Context = appContext.applicationContext
    val database: AppDatabase by lazy { AppDatabase.getInstance(applicationContext) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(applicationContext) }
    val projectRepository: ProjectRepository by lazy { ProjectRepository(database) }
    val authManager: AuthManager by lazy { AuthManager(applicationContext) }
    val cloudSyncRepository: CloudSyncRepository by lazy { CloudSyncRepository(applicationContext) }
    val aiProviderRouter: AiProviderRouter by lazy {
        AiProviderRouter(applicationContext, projectRepository, preferencesManager)
    }
    val workManager: WorkManager by lazy { WorkManager.getInstance(applicationContext) }

    companion object {
        @Volatile
        private var INSTANCE: ClipFreeContainer? = null

        fun getInstance(context: Context): ClipFreeContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipFreeContainer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
