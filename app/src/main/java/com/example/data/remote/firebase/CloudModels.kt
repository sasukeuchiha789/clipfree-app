package com.example.data.remote.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sourceDurationMs: Long = 0L,
    val aspectRatio: String = "9:16",
    val status: String = "DRAFT",
    val clipCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudClip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val hook: String = "",
    val description: String = "",
    val reason: String = "",
    val viralityScore: Long = 0L,
    val viralityReason: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 1000L,
    val language: String = "English",
    val aspectRatio: String = "9:16",
    val hashtags: List<String> = emptyList(),
    val captionStyle: String = "BOLD_CREATOR",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
