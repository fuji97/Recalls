package it.federicorapetti.recalls.ui.detail

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.federicorapetti.recalls.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {

    @get:Rule
    val rule = createComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    private var dismissCount = 0

    private fun bitmap(color: Int): Bitmap =
        Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    private val photos: List<Any> = listOf(
        bitmap(Color.RED),
        bitmap(Color.GREEN),
        bitmap(Color.BLUE)
    )

    private fun photoDescription(position: Int, total: Int = 3) =
        targetContext.getString(R.string.photo_viewer_photo_description, position, total)

    private fun position(position: Int, total: Int = 3) =
        targetContext.getString(R.string.photo_viewer_position, position, total)

    private fun page(position: Int): SemanticsNodeInteraction =
        rule.onNodeWithContentDescription(photoDescription(position))

    private fun show(
        photos: List<Any> = this.photos,
        initialPage: Int = 0,
        pdfUrl: String? = null
    ) {
        rule.setContent {
            PhotoViewer(
                photos = photos,
                initialPage = initialPage,
                attribution = ATTRIBUTION,
                pdfUrl = pdfUrl,
                onDismiss = { dismissCount++ }
            )
        }
        rule.waitForIdle()
    }

    private fun swipeLeftOn(position: Int) {
        page(position).performTouchInput { swipeLeft() }
        rule.waitForIdle()
    }

    private fun doubleTap(position: Int) {
        page(position).performTouchInput { doubleClick() }
        rule.waitForIdle()
    }

    @Test
    fun opensOnInitialPageWithPositionIndicator() {
        show(initialPage = 1)

        page(2).assertIsDisplayed()
        rule.onNodeWithText(position(2)).assertIsDisplayed()
    }

    @Test
    fun swipeLeftAdvancesAndSwipeRightFromFirstPageStays() {
        show(initialPage = 0)

        page(1).performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText(position(1)).assertIsDisplayed()

        swipeLeftOn(1)
        swipeLeftOn(2)
        rule.onNodeWithText(position(3)).assertIsDisplayed()
    }

    @Test
    fun singlePhotoShowsNoPositionIndicator() {
        show(photos = photos.take(1))

        rule.onNodeWithContentDescription(photoDescription(1, 1)).assertIsDisplayed()
        rule.onNodeWithText(position(1, 1)).assertDoesNotExist()
    }

    @Test
    fun closeButtonDismissesOnce() {
        show()

        rule.onNodeWithContentDescription(targetContext.getString(R.string.photo_viewer_close))
            .performClick()
        rule.waitForIdle()

        assertEquals(1, dismissCount)
    }

    @Test
    fun systemBackDismisses() {
        show()

        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(1, dismissCount)
    }

    @Test
    fun zoomedPageKeepsSwipeUntilZoomedOut() {
        show(initialPage = 1)

        doubleTap(2)
        swipeLeftOn(2)
        rule.onNodeWithText(position(2)).assertIsDisplayed()

        doubleTap(2)
        swipeLeftOn(2)
        rule.onNodeWithText(position(3)).assertIsDisplayed()
    }

    @Test
    fun pinchOutKeepsSwipeOnSamePage() {
        show(initialPage = 1)

        page(2).performTouchInput {
            pinch(
                center - Offset(50f, 0f),
                center - Offset(300f, 0f),
                center + Offset(50f, 0f),
                center + Offset(300f, 0f)
            )
        }
        rule.waitForIdle()
        swipeLeftOn(2)

        rule.onNodeWithText(position(2)).assertIsDisplayed()
    }

    @Test
    fun attributionAndPdfButtonShownWithPdfUrl() {
        show(pdfUrl = "https://example.com/recall.pdf")

        rule.onNodeWithText(ATTRIBUTION).assertIsDisplayed()
        rule.onNodeWithText(targetContext.getString(R.string.detail_pdf_button)).assertIsDisplayed()
    }

    @Test
    fun attributionShownWithoutPdfButtonWhenNoPdfUrl() {
        show(pdfUrl = null)

        rule.onNodeWithText(ATTRIBUTION).assertIsDisplayed()
        rule.onNodeWithText(targetContext.getString(R.string.detail_pdf_button)).assertDoesNotExist()
    }

    private companion object {
        const val ATTRIBUTION = "Source: Test attribution"
    }
}
