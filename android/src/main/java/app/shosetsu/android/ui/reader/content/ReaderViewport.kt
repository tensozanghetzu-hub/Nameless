package app.shosetsu.android.ui.reader.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/* Nameless reader viewport fix, modified 2026-10-06. GPL-3.0.
 * Original Shosetsu licensing and attribution are preserved.
 */

/** Independently supplied reserved regions can overlap. Reserve their union,
 * not their sum, and return physical left/right edges for landscape/RTL cutouts.
 * The reader scaffold reserves system insets once; chapter children receive
 * only its toolbar padding so those insets are not applied a second time.
 */
internal fun readerViewportPadding(
    window: PaddingValues,
    footer: PaddingValues,
    direction: LayoutDirection,
): PaddingValues = PaddingValues.Absolute(
    left = maxOf(window.calculateLeftPadding(direction), footer.calculateLeftPadding(direction)),
    top = maxOf(window.calculateTopPadding(), footer.calculateTopPadding()),
    right = maxOf(window.calculateRightPadding(direction), footer.calculateRightPadding(direction)),
    bottom = maxOf(window.calculateBottomPadding(), footer.calculateBottomPadding()),
)

/** Keep the SCROLLING viewport itself outside visible system bars. A CSS body
 * padding alone scrolls away, and source/user CSS can override it. Clip the
 * viewport so overscroll or large/negative HTML margins cannot paint the clock.
 */
@Composable
internal fun ReaderViewport(
    windowPadding: PaddingValues,
    footerPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    val padding = readerViewportPadding(windowPadding, footerPadding, LocalLayoutDirection.current)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .consumeWindowInsets(padding)
            .clipToBounds(),
        content = content,
    )
}
