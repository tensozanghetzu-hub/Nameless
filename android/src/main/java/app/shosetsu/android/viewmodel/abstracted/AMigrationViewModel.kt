package app.shosetsu.android.viewmodel.abstracted

import app.shosetsu.android.domain.model.local.SourceMigrationState
import app.shosetsu.android.viewmodel.base.ShosetsuViewModel
import app.shosetsu.lib.Novel
import kotlinx.coroutines.flow.StateFlow

/* This file is part of Shosetsu, distributed under the GNU GPL-3.0.
 * Original migration scaffold: Doomsdayrs, 2021.
 * Nameless: implemented source migration, modified 2026-10-05.
 * Original licensing and attribution are retained; see LICENSE.
 */

abstract class AMigrationViewModel : ShosetsuViewModel() {
    abstract val state: StateFlow<SourceMigrationState>
    abstract fun setNovels(array: List<Int>)
    abstract fun setWorkingOn(novelId: Int)
    abstract fun selectSource(sourceId: Int)
    abstract fun setQuery(query: String)
    abstract fun setSourceFilter(query: String)
    abstract fun setNovelUrl(url: String)
    abstract fun search(loadMore: Boolean = false)
    abstract fun prepareTarget(target: Novel.Info)
    abstract fun prepareUrl()
    abstract fun setKeepOriginal(keep: Boolean)
    abstract fun setUsePosition(use: Boolean)
    abstract fun migrate()
    abstract fun backStep(): Boolean
    abstract fun nextNovel()
    abstract fun refreshSources()
}
