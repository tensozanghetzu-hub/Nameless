package app.shosetsu.android.viewmodel.impl

import app.shosetsu.android.domain.model.local.MigrationSourceNovel
import app.shosetsu.android.domain.model.local.MigrationTargetSource
import app.shosetsu.android.domain.model.local.SourceMigrationState
import app.shosetsu.android.domain.repository.base.IExtensionsRepository
import app.shosetsu.android.domain.repository.base.INovelsRepository
import app.shosetsu.android.domain.usecases.SourceMigrationUseCase
import app.shosetsu.android.domain.usecases.get.GetExtensionUseCase
import app.shosetsu.android.providers.database.ShosetsuDatabase
import app.shosetsu.android.viewmodel.abstracted.AMigrationViewModel
import app.shosetsu.lib.Novel
import app.shosetsu.lib.PAGE_INDEX
import app.shosetsu.lib.mapify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/* This file is part of Shosetsu, distributed under the GNU GPL-3.0.
 * Original migration scaffold: Doomsdayrs, 2021.
 * Nameless: implemented source migration, modified 2026-10-05.
 * Original licensing and attribution are retained; see LICENSE.
 */

class MigrationViewModel(
    private val database: ShosetsuDatabase,
    private val novelsRepository: INovelsRepository,
    private val extensionsRepository: IExtensionsRepository,
    private val getExtension: GetExtensionUseCase,
) : AMigrationViewModel() {
    private val data = MutableStateFlow(SourceMigrationState())
    override val state = data.asStateFlow()
    private val migration = SourceMigrationUseCase(database, novelsRepository, getExtension)
    private var initializedIds: List<Int>? = null
    private var work: Job? = null
    @Volatile private var generation = 0L

    private fun blocked() = data.value.busy || data.value.migrating

    /** Stale/cancelled network calls must not update a newer selection's state. */
    private fun runWork(message: String, saving: Boolean = false, block: suspend (Long) -> Unit) {
        val token = ++generation
        work?.cancel()
        data.update { it.copy(busy = true, migrating = saving, activity = message, error = null) }
        work = viewModelScopeIO.launch {
            try {
                if (saving) block(token) else withTimeout(90_000) { block(token) }
            } catch (e: TimeoutCancellationException) {
                if (token == generation) data.update { it.copy(error = "The source took too long to respond. Try again, use a direct novel URL, or choose another source.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) data.update { it.copy(error = (e.message?.takeIf(String::isNotBlank) ?: "The operation failed. Check the source and connection, then try again.").take(600)) }
            } finally {
                if (token == generation) data.update { it.copy(busy = false, migrating = false, activity = "") }
            }
        }
    }

    override fun setNovels(array: List<Int>) {
        val ids = array.distinct().filter { it > 0 }
        if ((ids == initializedIds && (data.value.initialized || blocked())) || data.value.migrating) return
        initializedIds = ids
        data.value = SourceMigrationState()
        runWork("Loading selected novels and installed sources…") { token ->
            val installed = extensionsRepository.loadExtensionsFLow().first()
            val sourceNames = installed.associate { it.id to it.name }
            val selected = ids.mapNotNull { id -> database.novelsDao.getNovel(id)?.let {
                MigrationSourceNovel(it, sourceNames[it.extensionID] ?: "Unavailable source (ID ${it.extensionID})")
            } }
            if (token == generation) data.update { it.copy(
                initialized = true, novels = selected,
                sources = installed.filter { e -> e.enabled }.map { e -> MigrationTargetSource(e.id, e.name, e.lang, e.imageURL) }.sortedBy { s -> s.name.lowercase() },
                currentNovelId = selected.firstOrNull()?.id,
                query = selected.firstOrNull()?.title ?: "",
                error = if (selected.isEmpty()) "No novel was selected. Open Migrate source from a novel or a library selection." else null,
            ) }
        }
    }

    override fun refreshSources() {
        if (blocked()) return
        runWork("Loading installed sources…") { token ->
            val installed = extensionsRepository.loadExtensionsFLow().first().filter { it.enabled }
            val sources = installed.map { MigrationTargetSource(it.id, it.name, it.lang, it.imageURL) }.sortedBy { it.name.lowercase() }
            if (token == generation) data.update { old ->
                val stillInstalled = sources.any { it.id == old.currentSourceId }
                old.copy(sources = sources, currentSourceId = if (stillInstalled) old.currentSourceId else null,
                    results = if (stillInstalled) old.results else emptyList(), prepared = if (stillInstalled) old.prepared else null,
                    preview = if (stillInstalled) old.preview else null)
            }
        }
    }

    override fun setWorkingOn(novelId: Int) {
        if (blocked() || novelId in data.value.completed) return
        val novel = data.value.novels.find { it.id == novelId } ?: return
        ++generation
        work?.cancel()
        data.update { it.copy(currentNovelId = novelId, currentSourceId = null, query = novel.title, novelUrl = "", results = emptyList(),
            searched = false, searchedQuery = "", nextPage = null, prepared = null, preview = null,
            usePosition = false, keepOriginal = true, outcome = null, error = null, busy = false) }
    }

    override fun selectSource(sourceId: Int) {
        if (blocked() || data.value.sources.none { it.id == sourceId }) return
        data.update { it.copy(currentSourceId = sourceId, results = emptyList(), searched = false, nextPage = null,
            prepared = null, preview = null, usePosition = false, keepOriginal = true, outcome = null, novelUrl = "", error = null) }
        if (data.value.query.isNotBlank()) search()
    }

    override fun setQuery(query: String) { if (!blocked()) data.update { it.copy(query = query, error = null) } }
    override fun setSourceFilter(query: String) { if (!blocked()) data.update { it.copy(sourceFilter = query) } }
    override fun setNovelUrl(url: String) { if (!blocked()) data.update { it.copy(novelUrl = url, error = null) } }

    override fun search(loadMore: Boolean) {
        if (blocked()) return
        val before = data.value
        val id = before.currentSourceId ?: return
        val query = if (loadMore) before.searchedQuery else before.query.trim()
        if (query.isBlank()) { data.update { it.copy(error = "Enter a novel title to search for.") }; return }
        if (loadMore && before.nextPage == null) return
        runWork("Searching ${before.currentSource?.name ?: "source"}…") { token ->
            val extension = requireNotNull(getExtension(id)) { "The selected source is no longer installed." }
            require(extension.hasSearch) { "This source does not support searching. Paste the full novel URL below instead." }
            val page = if (loadMore) requireNotNull(before.nextPage) else extension.startIndex
            val filters = extension.searchFiltersModel.toList().mapify().toMutableMap().apply { put(PAGE_INDEX, page) }
            val result = novelsRepository.getCatalogueSearch(extension, query, filters)
                .filter { it.link.isNotBlank() }.distinctBy { it.link }
                .filterNot { it.link == before.currentNovel?.novel?.url && id == before.currentNovel?.novel?.extensionID }
            val prior = if (loadMore) before.results else emptyList()
            val seen = prior.map { it.link }.toHashSet()
            val additions = result.filterNot { it.link in seen }
            if (token == generation) data.update { it.copy(
                results = prior + additions, searched = true, searchedQuery = query,
                nextPage = if (extension.isSearchIncrementing && additions.isNotEmpty()) page + 1 else null,
            ) }
        }
    }

    override fun prepareTarget(target: Novel.Info) {
        if (blocked()) return
        val before = data.value
        val sourceId = before.currentNovelId ?: return
        val destination = before.currentSourceId ?: return
        runWork("Loading destination chapters and checking progress…") { token ->
            val prepared = migration.prepare(sourceId, destination, target)
            if (token == generation) data.update { it.copy(prepared = prepared, preview = prepared.plan(false), keepOriginal = true, usePosition = false) }
        }
    }

    override fun prepareUrl() {
        if (blocked()) return
        val before = data.value
        val sourceId = before.currentNovelId ?: return
        val destination = before.currentSourceId ?: return
        runWork("Loading the novel URL and checking chapters…") { token ->
            val prepared = migration.prepareUrl(sourceId, destination, before.novelUrl)
            if (token == generation) data.update { it.copy(prepared = prepared, preview = prepared.plan(false), keepOriginal = true, usePosition = false) }
        }
    }

    override fun setKeepOriginal(keep: Boolean) {
        if (blocked()) return
        if (!keep && data.value.preview?.unmatchedProgress?.isNotEmpty() == true) return
        data.update { it.copy(keepOriginal = keep) }
    }

    override fun setUsePosition(use: Boolean) {
        if (blocked()) return
        val prepared = data.value.prepared ?: return
        val preview = prepared.plan(use)
        data.update { it.copy(usePosition = use, preview = preview,
            keepOriginal = it.keepOriginal || preview.unmatchedProgress.isNotEmpty()) }
    }

    override fun migrate() {
        if (blocked()) return
        val before = data.value
        val prepared = before.prepared ?: return
        runWork("Saving the migration safely…", saving = true) { token ->
            require(extensionsRepository.getInstalledExtension(prepared.extensionId)?.enabled == true) { "The destination source is not installed or enabled anymore." }
            val result = migration.migrate(prepared, before.keepOriginal, before.usePosition)
            if (token == generation) data.update { it.copy(outcome = result, completed = it.completed + result.sourceId, error = null) }
        }
    }

    override fun nextNovel() {
        if (blocked()) return
        data.value.pending.firstOrNull()?.let { setWorkingOn(it.id) }
    }

    override fun backStep(): Boolean {
        val before = data.value
        if (before.migrating) return true
        if (before.outcome != null) return false
        if (before.prepared == null && before.currentSourceId == null) return false
        ++generation
        work?.cancel()
        data.update { it.copy(
            prepared = null, preview = null, error = null, busy = false, migrating = false, activity = "",
            currentSourceId = if (before.prepared != null) before.currentSourceId else null,
            usePosition = false, keepOriginal = true,
        ) }
        return true
    }
}
