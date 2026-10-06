package app.shosetsu.android.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import app.shosetsu.android.domain.catalog.ExtensionIconPolicy
import app.shosetsu.android.view.compose.ImageLoadingError
import app.shosetsu.android.view.compose.placeholder
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/** Network-backed extension avatar with a targeted fallback for retired catalog
 * icon links and conventional site favicons; source/library rows are not changed.
 */
@Composable
internal fun ExtensionAvatar(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier.size(64.dp),
) {
    val context = LocalContext.current
    val candidates = remember(imageUrl) { ExtensionIconPolicy.candidates(imageUrl) }
    var candidateIndex by remember(imageUrl) { mutableIntStateOf(0) }
    val imageUrlCandidate = candidates.getOrNull(candidateIndex)

    if (imageUrlCandidate == null) {
        AvatarFallback(modifier)
        return
    }

    val request = remember(context, imageUrlCandidate) {
        ImageRequest.Builder(context)
            .data(imageUrlCandidate)
            .crossfade(true)
            .apply {
                ExtensionIconPolicy.refererFor(imageUrlCandidate)?.let { addHeader("Referer", it) }
            }
            .build()
    }
    val attemptIndex = candidateIndex
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        error = { AvatarFallback(Modifier.fillMaxSize()) },
        loading = { Box(Modifier.fillMaxSize().placeholder(true)) },
        onError = {
            // Attempt the replacement URL only once; a stale request completion
            // cannot skip a later candidate after the list is scrolled/rebound.
            if (candidateIndex == attemptIndex && attemptIndex < candidates.lastIndex) {
                candidateIndex = attemptIndex + 1
            }
        },
    )
}

@Composable
private fun AvatarFallback(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        ImageLoadingError(
            Modifier.size(52.dp).clip(MaterialTheme.shapes.extraSmall)
        )
    }
}
