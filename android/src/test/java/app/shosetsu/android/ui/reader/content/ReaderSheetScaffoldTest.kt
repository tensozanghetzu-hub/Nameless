package app.shosetsu.android.ui.reader.content

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/* Nameless reader sheet regression tests, 2026-10-06. GPL-3.0.
 * Runs the actual Compose ReaderSheetScaffold and ReaderViewport under
 * Robolectric with a representative toolbar/settings list and synthetic insets.
 * This is not a physical-device or full-app/emulator visual test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSheetScaffoldTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private lateinit var state: BottomSheetScaffoldState
    private lateinit var scope: CoroutineScope
    private val padding = mutableStateOf<PaddingValues>(PaddingValues(0.dp))
    private val focused = mutableStateOf(false)

    @Before fun setup() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        activity.setup()
    }

    @After fun tearDown() {
        activity.pause().stop().destroy()
    }

    private fun show(insets: PaddingValues, isFocused: Boolean = false) {
        padding.value = insets
        focused.value = isFocused
        activity.get().setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("window")) {
                    state = rememberBottomSheetScaffoldState()
                    scope = rememberCoroutineScope()
                    ReaderSheetScaffold(
                        scaffoldState = state,
                        windowPadding = padding.value,
                        isFocused = focused.value,
                        sheetContent = {
                            Box(
                                Modifier.fillMaxWidth().height(ReaderToolbarHeight).testTag("toolbar"),
                                contentAlignment = Alignment.Center,
                            ) { Text("Reader toolbar") }
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().testTag("settings"),
                                contentPadding = PaddingValues(vertical = 16.dp),
                            ) {
                                item { Text("Paragraph spacing", Modifier.fillMaxWidth().height(48.dp)) }
                                items((0..19).toList()) { Text("Setting $it", Modifier.fillMaxWidth().height(64.dp)) }
                            }
                        },
                        content = { toolbarPadding ->
                            // Matches ChapterReaderContent: outer viewport already owns system insets.
                            ReaderViewport(PaddingValues(0.dp), toolbarPadding) {
                                Box(Modifier.fillMaxSize().testTag("chapter")) { Text("Chapter text") }
                            }
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun px(value: Dp): Float = with(compose.density) { value.toPx() }
    private fun expand() {
        compose.runOnIdle { scope.launch { state.bottomSheetState.expand() } }
        compose.waitForIdle()
    }

    @Test fun threeButtonNavigationDoesNotExposeParagraphSpacingWhenCollapsed() {
        show(PaddingValues(top = 28.dp, bottom = 48.dp))
        compose.onNodeWithTag("toolbar").assertIsDisplayed()
        compose.onNodeWithText("Paragraph spacing").assertIsNotDisplayed()
        assertEquals(bounds("window").bottom - px(48.dp), bounds("toolbar").bottom, 1f)
        assertEquals(px(ReaderToolbarHeight), bounds("toolbar").height, 1f)
    }

    @Test fun gestureNavigationKeepsToolbarAboveTheGestureArea() {
        show(PaddingValues(top = 24.dp, bottom = 24.dp))
        compose.onNodeWithText("Paragraph spacing").assertIsNotDisplayed()
        assertEquals(bounds("window").bottom - px(24.dp), bounds("toolbar").bottom, 1f)
        assertEquals(bounds("toolbar").top, bounds("chapter").bottom, 1f)
    }

    @Test fun expandedToolbarStaysBelowClockAndSettingsAboveNavigation() {
        show(PaddingValues(top = 28.dp, bottom = 48.dp))
        expand()
        compose.onNodeWithText("Paragraph spacing").assertIsDisplayed()
        assertEquals(bounds("window").top + px(28.dp), bounds("toolbar").top, 1f)
        assertTrue(bounds("settings").bottom <= bounds("window").bottom - px(48.dp) + 1f)
    }

    @Test fun settingsCanScrollToLastItemWithoutDrawingBehindNavigation() {
        show(PaddingValues(top = 28.dp, bottom = 48.dp))
        expand()
        compose.onNodeWithTag("settings").performScrollToNode(hasText("Setting 19"))
        compose.onNodeWithText("Setting 19").assertIsDisplayed()
        val last = compose.onNodeWithText("Setting 19").fetchSemanticsNode().boundsInRoot
        assertTrue(last.bottom <= bounds("window").bottom - px(48.dp) + 1f)
        compose.runOnIdle { scope.launch { state.bottomSheetState.partialExpand() } }
        compose.waitForIdle()
        compose.onNodeWithText("Setting 19").assertIsNotDisplayed()
        assertEquals(bounds("window").bottom - px(48.dp), bounds("toolbar").bottom, 1f)
    }

    @Test fun focusHidesWholeSheetAndDoesNotCountSystemInsetsTwice() {
        show(PaddingValues(top = 28.dp, bottom = 48.dp), isFocused = true)
        compose.onNodeWithTag("toolbar").assertIsNotDisplayed()
        compose.onNodeWithText("Paragraph spacing").assertIsNotDisplayed()
        assertEquals(bounds("window").top + px(28.dp), bounds("chapter").top, 1f)
        assertEquals(bounds("window").bottom - px(48.dp), bounds("chapter").bottom, 1f)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-mdpi")
    fun landscapeCutoutsAndSideNavigationProtectToolbarAndChapter() {
        show(PaddingValues.Absolute(left = 32.dp, top = 24.dp, right = 48.dp))
        expand()
        val window = bounds("window")
        assertTrue(bounds("toolbar").left >= window.left + px(32.dp) - 1f)
        assertTrue(bounds("toolbar").right <= window.right - px(48.dp) + 1f)
        assertEquals(window.top + px(24.dp), bounds("toolbar").top, 1f)
    }

    @Test fun fullscreenWithHiddenBarsHasNoArtificialSystemGap() {
        show(PaddingValues(0.dp), isFocused = true)
        assertEquals(bounds("window").top, bounds("chapter").top, 1f)
        assertEquals(bounds("window").bottom, bounds("chapter").bottom, 1f)
    }

    @Test fun showingAndHidingSystemBarsRepositionsSheetWithoutExposingSettings() {
        show(PaddingValues(top = 28.dp, bottom = 48.dp))
        compose.runOnIdle { padding.value = PaddingValues(0.dp) }
        compose.waitForIdle()
        assertEquals(bounds("window").bottom, bounds("toolbar").bottom, 1f)
        compose.onNodeWithText("Paragraph spacing").assertIsNotDisplayed()
        compose.runOnIdle { padding.value = PaddingValues(top = 28.dp, bottom = 48.dp) }
        compose.waitForIdle()
        assertEquals(bounds("window").bottom - px(48.dp), bounds("toolbar").bottom, 1f)
        compose.onNodeWithText("Paragraph spacing").assertIsNotDisplayed()
    }
}
