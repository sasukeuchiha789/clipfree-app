package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProject(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProject(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAtMs = :updatedAtMs WHERE id = :projectId")
    suspend fun renameProject(projectId: String, name: String, updatedAtMs: Long)

    @Query("UPDATE projects SET status = :status, statusMessage = :message, progressPercent = :progress, clipCount = :clipCount, updatedAtMs = :updatedAtMs WHERE id = :projectId")
    suspend fun updateProjectProgress(
        projectId: String,
        status: String,
        message: String,
        progress: Int,
        clipCount: Int,
        updatedAtMs: Long
    )

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()
}

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY updatedAtMs DESC")
    fun observeAllClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY viralityScore DESC, startMs ASC")
    fun observeClipsForProject(projectId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY viralityScore DESC, startMs ASC")
    suspend fun getClipsForProject(projectId: String): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE id = :clipId")
    fun observeClip(clipId: String): Flow<ClipEntity?>

    @Query("SELECT * FROM clips WHERE id = :clipId")
    suspend fun getClip(clipId: String): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClip(clip: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClips(clips: List<ClipEntity>)

    @Query("DELETE FROM clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: String)

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)
}

@Dao
interface TranscriptCacheDao {
    @Query("SELECT * FROM transcript_cache WHERE videoSignature = :signature")
    suspend fun getCache(signature: String): TranscriptCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entity: TranscriptCacheEntity)

    @Query("DELETE FROM transcript_cache")
    suspend fun clearCache()
}

@Dao
interface ExportedMediaDao {
    @Query("SELECT * FROM exported_media ORDER BY createdAtMs DESC")
    fun observeAllExports(): Flow<List<ExportedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: ExportedMediaEntity)

    @Query("DELETE FROM exported_media WHERE id = :exportId")
    suspend fun deleteExportById(exportId: String)

    @Query("DELETE FROM exported_media")
    suspend fun deleteAllExports()
}

@Dao
interface ThumbnailDao {
    @Query("SELECT * FROM thumbnails ORDER BY createdAtMs DESC")
    fun observeAllThumbnails(): Flow<List<ThumbnailEntity>>

    @Query("SELECT * FROM thumbnails WHERE clipId = :clipId ORDER BY createdAtMs DESC")
    fun observeThumbnailsForClip(clipId: String): Flow<List<ThumbnailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThumbnail(thumbnail: ThumbnailEntity)

    @Query("DELETE FROM thumbnails WHERE id = :thumbnailId")
    suspend fun deleteThumbnailById(thumbnailId: String)
}

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY priority ASC")
    fun observeProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY priority ASC")
    suspend fun getProviders(): List<AiProviderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviders(providers: List<AiProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: AiProviderEntity)

    @Query("UPDATE ai_providers SET status = :status, lastError = :lastError, lastCheckedMs = :checkedMs WHERE id = :id")
    suspend fun updateProviderStatus(id: String, status: String, lastError: String?, checkedMs: Long)
}
