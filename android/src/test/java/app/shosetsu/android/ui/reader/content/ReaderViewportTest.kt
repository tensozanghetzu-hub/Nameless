package app.shosetsu.android.ui.reader.content

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/* Nameless reader inset regression tests, 2026-10-06. GPL-3.0. */
class ReaderViewportTest {
    @Test fun visibleStatusBarKeepsTitleBelowClock() {
        val p = readerViewportPadding(PaddingValues(top = 28.dp, bottom = 24.dp), PaddingValues(bottom = 80.dp), LayoutDirection.Ltr)
        assertEquals(28.dp, p.calculateTopPadding())
        assertEquals(80.dp, p.calculateBottomPadding())
    }
    @Test fun navigationBarIsNotCountedTwiceWithToolbar() {
        val p = readerViewportPadding(PaddingValues(bottom = 24.dp), PaddingValues(bottom = 80.dp), LayoutDirection.Ltr)
        assertEquals(80.dp, p.calculateBottomPadding())
    }
    @Test fun hiddenToolbarStillRespectsNavigationBar() {
        val p = readerViewportPadding(PaddingValues(top = 28.dp, bottom = 24.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        assertEquals(24.dp, p.calculateBottomPadding())
        assertEquals(28.dp, p.calculateTopPadding())
    }
    @Test fun fullscreenWithoutBarsDoesNotLeaveFixedBlankSpace() {
        val p = readerViewportPadding(PaddingValues(0.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        assertEquals(0.dp, p.calculateTopPadding())
        assertEquals(0.dp, p.calculateBottomPadding())
    }
    @Test fun landscapeLeftCutoutProtectsText() {
        val p = readerViewportPadding(PaddingValues.Absolute(left = 40.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        assertEquals(40.dp, p.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, p.calculateRightPadding(LayoutDirection.Ltr))
    }
    @Test fun rightCutoutStaysOnPhysicalRightInRtl() {
        val p = readerViewportPadding(PaddingValues.Absolute(right = 40.dp), PaddingValues(0.dp), LayoutDirection.Rtl)
        assertEquals(0.dp, p.calculateLeftPadding(LayoutDirection.Rtl))
        assertEquals(40.dp, p.calculateRightPadding(LayoutDirection.Rtl))
    }
    @Test fun relativeFooterPaddingResolvesCorrectlyInRtl() {
        val p = readerViewportPadding(PaddingValues.Absolute(left = 5.dp, right = 25.dp), PaddingValues(start = 32.dp, end = 12.dp), LayoutDirection.Rtl)
        assertEquals(12.dp, p.calculateLeftPadding(LayoutDirection.Rtl))
        assertEquals(32.dp, p.calculateRightPadding(LayoutDirection.Rtl))
    }
    @Test fun changingVisibilityRecalculatesNativeViewportPadding() {
        val visible = readerViewportPadding(PaddingValues(top = 28.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        val hidden = readerViewportPadding(PaddingValues(0.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        val restored = readerViewportPadding(PaddingValues(top = 28.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        assertEquals(28.dp, visible.calculateTopPadding())
        assertEquals(0.dp, hidden.calculateTopPadding())
        assertEquals(28.dp, restored.calculateTopPadding())
    }
    @Test fun fractionalInsetsAreNotTruncatedToCssIntegers() {
        val p = readerViewportPadding(PaddingValues(top = 27.5.dp), PaddingValues(0.dp), LayoutDirection.Ltr)
        assertEquals(27.5.dp, p.calculateTopPadding())
    }
}
