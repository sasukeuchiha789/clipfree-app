package com.example

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiCaptionSegmentDto
import com.example.data.ai.AiProviderRouter
import com.example.data.ai.AiSuggestedClipDto
import com.example.data.ai.AiVideoAnalysisResult
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipAdjustment
import com.example.domain.model.ClipDurationPreset
import com.example.domain.model.ClipProject
import com.example.domain.model.ExportResolution
import com.example.domain.model.GeneratedClip
import com.example.domain.model.HighlightCategory
import com.example.domain.model.ProcessingStatus
import com.example.media.ClipTransformerEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name from context`() {
        assertEquals("ClipFree.ai", context.getString(R.string.app_name))
    }

    @Test
    @androidx.annotation.OptIn(UnstableApi::class)
    fun `output dimensions are aligned to multiples of 16`() {
        AspectRatioPreset.entries.forEach { ratio ->
            ExportResolution.entries.forEach { res ->
                val (w, h) = ClipTransformerEngine.computeOutputDimensions(ratio, res)
                assertEquals("Width $w not multiple of 16", 0, w % 16)
                assertEquals("Height $h not multiple of 16", 0, h % 16)
                assertTrue(w >= 16 && h >= 16)
            }
        }
    }

    @Test
    @androidx.annotation.OptIn(UnstableApi::class)
    fun `crop effect reframes within valid NDC bounds`() {
        val crop = ClipTransformerEngine.computeCropEffect(
            sourceWidth = 1920,
            sourceHeight = 1080,
            targetAspect = AspectRatioPreset.VERTICAL_9_16,
            adjustment = ClipAdjustment(subjectFocusX = 0.7f, cropOffsetX = 0.2f, zoomScale = 1.4f)
        )
        assertNotNull(crop)
    }

    @Test
    fun `validateAndClampResult enforces bounds and yields minimum 4 clips`() {
        val prefs = PreferencesManager(context)
        val router = AiProviderRouter(context, repository, prefs)

        val raw = AiVideoAnalysisResult(
            detectedLanguage = "English",
            summary = "Podcast discussion on creator hooks and retention.",
            clips = listOf(
                AiSuggestedClipDto(
                    title = "Best Hook",
                    startMs = -500L, // out of bounds
                    endMs = 999_000L, // beyond video duration
                    description = "Hook segment",
                    openingHook = "Stop doing this",
                    selectionReason = "Strong opener",
                    viralityScore = 150, // clamped to 100
                    viralityExplanation = "Clear opening hook",
                    socialCaption = "Try this hook",
                    captions = listOf(
                        AiCaptionSegmentDto("First caption line", 1000L, 4000L)
                    )
                )
            )
        )

        val clamped = router.validateAndClampResult(raw, videoDurationMs = 60_000L)
        assertTrue("Expected 4 to 5 clips, got ${clamped.clips.size}", clamped.clips.size in 4..5)
        clamped.clips.forEach { c ->
            assertTrue(c.startMs >= 0L)
            assertTrue(c.endMs <= 60_000L)
            assertTrue(c.endMs > c.startMs)
            assertTrue(c.viralityScore in 0..100)
        }
    }

    @Test
    fun `room repository persists project and child clips locally`() = runBlocking {
        val now = System.currentTimeMillis()
        val project = ClipProject(
            id = "proj_test_1",
            name = "Demo Talk",
            sourceUri = "file:///tmp/demo.mp4",
            sourceFileName = "demo.mp4",
            sourceDurationMs = 90_000L,
            sourceWidth = 1920,
            sourceHeight = 1080,
            thumbnailPath = null,
            aspectRatio = AspectRatioPreset.VERTICAL_9_16,
            durationPreset = ClipDurationPreset.SHORT_15_30,
            customMinSeconds = 15,
            customMaxSeconds = 30,
            status = ProcessingStatus.COMPLETED,
            statusMessage = "Ready",
            progressPercent = 100,
            clipCount = 1,
            detectedLanguage = "English",
            cloudSynced = false,
            createdAtMs = now,
            updatedAtMs = now
        )
        repository.upsertProject(project)
        repository.upsertClip(
            GeneratedClip(
                id = "clip_test_1",
                projectId = "proj_test_1",
                title = "Top Insight",
                description = "Key takeaway",
                openingHook = "Here is the rule",
                selectionReason = "Clear tip",
                category = HighlightCategory.EDUCATIONAL,
                viralityScore = 88,
                viralityExplanation = "Concise tip",
                socialCaption = "Watch this",
                hashtags = listOf("#shorts"),
                language = "English",
                startMs = 5_000L,
                endMs = 25_000L,
                aspectRatio = AspectRatioPreset.VERTICAL_9_16,
                captions = emptyList(),
                adjustment = ClipAdjustment()
            )
        )

        val loadedProject = repository.getProject("proj_test_1")
        val loadedClips = repository.getClipsForProject("proj_test_1")
        assertEquals("Demo Talk", loadedProject?.name)
        assertEquals(1, loadedClips.size)
        assertEquals("Top Insight", loadedClips.first().title)
    }
}
