package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.domain.model.AccountSession
import com.example.domain.model.AiAvailabilityState
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipAdjustment
import com.example.domain.model.ClipDurationPreset
import com.example.domain.model.ClipProject
import com.example.domain.model.GeneratedClip
import com.example.domain.model.HighlightCategory
import com.example.domain.model.ProcessingStatus
import com.example.domain.model.ThemeMode
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.theme.ClipFreeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClipFreeUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `auth screen renders Google sign-in and guest mode triggers`() {
        var guestClicked = false
        composeRule.setContent {
            ClipFreeTheme(ThemeMode.LIGHT) {
                AuthScreen(
                    isLoading = false,
                    errorMessage = null,
                    onGoogleSignIn = {},
                    onContinueGuest = { guestClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("guest_mode_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(guestClicked)
    }

    @Test
    fun `home screen displays upload and auto create actions`() {
        var uploadClicked = false
        composeRule.setContent {
            ClipFreeTheme(ThemeMode.DARK) {
                HomeScreen(
                    session = AccountSession.Guest,
                    aiAvailability = AiAvailabilityState.AI_AVAILABLE,
                    projects = emptyList(),
                    recentClips = emptyList(),
                    exports = emptyList(),
                    onUploadVideoClick = { uploadClicked = true },
                    onAutoCreateClick = {},
                    onOpenProject = {},
                    onOpenClip = {},
                    onOpenDownloads = {},
                    onCancelProcessing = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_upload_video_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("home_auto_create_button").assertIsDisplayed()
        assertTrue(uploadClicked)
    }

    @Test
    fun `library clips tab lists clips and projects screen lists projects`() {
        val sampleClip = GeneratedClip(
            id = "clip_ui_1",
            projectId = "proj_ui_1",
            title = "Viral Hook Breakdown",
            description = "How to hook viewers fast",
            openingHook = "Watch the first 2 seconds",
            selectionReason = "High energy intro",
            category = HighlightCategory.STRONG_HOOK,
            viralityScore = 92,
            viralityExplanation = "Strong opening line",
            socialCaption = "Must watch",
            hashtags = listOf("#shorts"),
            language = "English",
            startMs = 0L,
            endMs = 20_000L,
            aspectRatio = AspectRatioPreset.VERTICAL_9_16,
            captions = emptyList(),
            adjustment = ClipAdjustment()
        )
        val sampleProject = ClipProject(
            id = "proj_ui_1",
            name = "Creator Podcast",
            sourceUri = "content://media/video/1",
            sourceFileName = "podcast.mp4",
            sourceDurationMs = 600_000L,
            sourceWidth = 1920,
            sourceHeight = 1080,
            thumbnailPath = null,
            aspectRatio = AspectRatioPreset.VERTICAL_9_16,
            durationPreset = ClipDurationPreset.SHORT_15_30,
            customMinSeconds = 15,
            customMaxSeconds = 30,
            status = ProcessingStatus.COMPLETED,
            statusMessage = "5 clips ready",
            progressPercent = 100,
            clipCount = 5,
            detectedLanguage = "English",
            cloudSynced = false,
            createdAtMs = 1700000000000L,
            updatedAtMs = 1700000000000L
        )

        composeRule.setContent {
            ClipFreeTheme(ThemeMode.LIGHT) {
                LibraryScreen(
                    clips = listOf(sampleClip),
                    exports = emptyList(),
                    thumbnails = emptyList(),
                    projects = listOf(sampleProject),
                    initialTab = 0,
                    onOpenClip = {},
                    onOpenProject = {},
                    onDeleteClip = {},
                    onDeleteExport = {},
                    onDeleteThumbnail = {},
                    onDeleteProject = {},
                    onMessage = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_search_input").assertIsDisplayed()
        composeRule.onNodeWithText("Viral Hook Breakdown").assertIsDisplayed()
    }

    @Test
    fun `projects screen shows saved project item`() {
        val sampleProject = ClipProject(
            id = "proj_ui_2",
            name = "Keynote Speech",
            sourceUri = "content://media/video/2",
            sourceFileName = "keynote.mp4",
            sourceDurationMs = 300_000L,
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
            clipCount = 4,
            detectedLanguage = "English",
            cloudSynced = false,
            createdAtMs = 1700000000000L,
            updatedAtMs = 1700000000000L
        )
        composeRule.setContent {
            ClipFreeTheme(ThemeMode.LIGHT) {
                ProjectsScreen(
                    projects = listOf(sampleProject),
                    onOpenProject = {},
                    onRenameProject = { _, _ -> },
                    onDeleteProject = {},
                    onCreateNew = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("projects_new_button").assertIsDisplayed()
        composeRule.onNodeWithText("Keynote Speech").assertIsDisplayed()
    }
}
