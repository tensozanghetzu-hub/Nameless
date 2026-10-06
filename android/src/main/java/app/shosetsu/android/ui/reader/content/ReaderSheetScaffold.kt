package app.shosetsu.android.ui.reader.content

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/* Nameless reader sheet fix, modified 2026-10-06. GPL-3.0.
 * Original Shosetsu licensing and attribution are preserved.
 */

// The peek and actual toolbar MUST use the same height. System bars are outside
// the scaffold, not extra peek space in which the next settings row can appear.
internal val ReaderToolbarHeight = 56.dp

/**
 * Reserve and clip the entire reader/sheet to the safe window, not just chapters.
 * A partially expanded sheet is taller than its visible peek and otherwise can
 * paint settings underneath the navigation bar. Expanded controls also need to
 * stay below the status bar and above the navigation bar.
 *
 * Child chapter viewports receive only the scaffold's toolbar padding; window
 * insets are already reserved here and must not be counted for a second time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSheetScaffold(
    scaffoldState: BottomSheetScaffoldState,
    windowPadding: PaddingValues,
    isFocused: Boolean,
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
) {
    ReaderViewport(windowPadding, PaddingValues(0.dp)) {
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            sheetContent = sheetContent,
            sheetPeekHeight = if (isFocused) 0.dp else ReaderToolbarHeight,
            sheetShape = RectangleShape,
            sheetDragHandle = null,
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}
