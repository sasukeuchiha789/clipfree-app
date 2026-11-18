package com.example.data.remote.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.ClipProject
import com.example.domain.model.GeneratedClip
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud sync.")
    }

    private fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.take(120).ifBlank { "item_${System.currentTimeMillis()}" }
    }

    fun observeCloudProjects(): Flow<List<CloudProject>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncProjectAndClips(
        project: ClipProject,
        clips: List<GeneratedClip>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Google Sign-In required for cloud sync."))
        val safeProjectId = sanitizeDocId(project.id)
        val projectRef = db.collection("users").document(uid)
            .collection("projects").document(safeProjectId)
        val docPath = projectRef.path

        return try {
            val existingSnap = projectRef.get().await()
            val safeTitle = project.name.trim().take(120).ifBlank { "Untitled Project" }
            val safeDuration = project.sourceDurationMs.coerceIn(0L, 86_400_000L)
            val safeRatio = project.aspectRatio.wireValue
            val safeStatus = project.status.name
            val safeClipCount = clips.size.coerceIn(0, 50).toLong()

            if (existingSnap.exists()) {
                projectRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "aspectRatio" to safeRatio,
                        "status" to safeStatus,
                        "clipCount" to safeClipCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                projectRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to safeTitle,
                        "sourceDurationMs" to safeDuration,
                        "aspectRatio" to safeRatio,
                        "status" to safeStatus,
                        "clipCount" to safeClipCount,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            clips.take(50).forEach { clip ->
                val safeClipId = sanitizeDocId(clip.id)
                val clipRef = projectRef.collection("clips").document(safeClipId)
                val existingClip = clipRef.get().await()
                val safeStart = clip.effectiveStartMs.coerceAtLeast(0L)
                val safeEnd = clip.effectiveEndMs.coerceAtLeast(safeStart + 1000L)
                val safeTags = clip.hashtags.take(15).map { it.take(40) }
                val safeClipTitle = clip.title.trim().take(120).ifBlank { "Highlight Clip" }

                if (existingClip.exists()) {
                    clipRef.update(
                        mapOf(
                            "title" to safeClipTitle,
                            "hook" to clip.openingHook.take(240),
                            "description" to clip.description.take(600),
                            "reason" to clip.selectionReason.take(400),
                            "viralityScore" to clip.viralityScore.coerceIn(0, 100).toLong(),
                            "viralityReason" to clip.viralityExplanation.take(400),
                            "startMs" to safeStart,
                            "endMs" to safeEnd,
                            "language" to clip.language.take(40).ifBlank { "English" },
                            "aspectRatio" to clip.aspectRatio.wireValue,
                            "hashtags" to safeTags,
                            "captionStyle" to clip.adjustment.captionStyle.name.take(40),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    clipRef.set(
                        mapOf(
                            "userId" to uid,
                            "projectId" to safeProjectId,
                            "title" to safeClipTitle,
                            "hook" to clip.openingHook.take(240),
                            "description" to clip.description.take(600),
                            "reason" to clip.selectionReason.take(400),
                            "viralityScore" to clip.viralityScore.coerceIn(0, 100).toLong(),
                            "viralityReason" to clip.viralityExplanation.take(400),
                            "startMs" to safeStart,
                            "endMs" to safeEnd,
                            "language" to clip.language.take(40).ifBlank { "English" },
                            "aspectRatio" to clip.aspectRatio.wireValue,
                            "hashtags" to safeTags,
                            "captionStyle" to clip.adjustment.captionStyle.name.take(40),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudProject(projectId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Google Sign-In required."))
        val safeProjectId = sanitizeDocId(projectId)
        val projectRef = db.collection("users").document(uid)
            .collection("projects").document(safeProjectId)
        return try {
            val clipsSnap = projectRef.collection("clips").get().await()
            clipsSnap.documents.forEach { it.reference.delete().await() }
            projectRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, projectRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Google Sign-In required."))
        val projectsPath = "users/$uid/projects"
        return try {
            val projectsSnap = db.collection("users").document(uid)
                .collection("projects").get().await()
            projectsSnap.documents.forEach { projectDoc ->
                val clipsSnap = projectDoc.reference.collection("clips").get().await()
                clipsSnap.documents.forEach { it.reference.delete().await() }
                projectDoc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, projectsPath)
            Result.failure(e)
        }
    }
}
