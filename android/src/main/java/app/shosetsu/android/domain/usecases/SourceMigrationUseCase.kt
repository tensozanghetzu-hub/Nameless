package app.shosetsu.android.domain.usecases

import androidx.room.withTransaction
import app.shosetsu.android.common.ext.asEntity
import app.shosetsu.android.common.ext.convertTo
import app.shosetsu.android.common.ext.entity
import app.shosetsu.android.common.ext.toDB
import app.shosetsu.android.domain.migration.ChapterMigrationPlanner
import app.shosetsu.android.domain.model.database.DBNovelCategoryEntity
import app.shosetsu.android.domain.model.database.DBNovelReaderSettingEntity
import app.shosetsu.android.domain.model.local.PreparedSourceMigration
import app.shosetsu.android.domain.model.local.SourceMigrationOutcome
import app.shosetsu.android.domain.model.local.asMigrationChapter
import app.shosetsu.android.domain.repository.base.INovelsRepository
import app.shosetsu.android.domain.usecases.get.GetExtensionUseCase
import app.shosetsu.android.providers.database.ShosetsuDatabase
import app.shosetsu.lib.IExtension.Companion.KEY_NOVEL_URL
import app.shosetsu.lib.Novel
import java.net.URI

/* Nameless source-migration implementation, modified 2026-10-05.
 * GPL-3.0; based on the original Shosetsu data model and repositories.
 * Network preparation is read-only. All library/progress changes commit together.
 */

class SourceMigrationUseCase(
    private val database: ShosetsuDatabase,
    private val novels: INovelsRepository,
    private val getExtension: GetExtensionUseCase,
) {
    suspend fun prepareUrl(sourceId: Int, extensionId: Int, fullUrl: String): PreparedSourceMigration {
        val uri = runCatching { URI(fullUrl.trim()) }.getOrNull()
        require(uri != null && uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank()) {
            "Paste a full http or https novel URL from the selected source."
        }
        val extension = requireNotNull(getExtension(extensionId)) { "The destination source is not installed." }
        val url = extension.shrinkURL(fullUrl.trim(), KEY_NOVEL_URL)
        val expanded = runCatching { URI(extension.expandURL(url, KEY_NOVEL_URL)) }.getOrNull()
        require(expanded?.host?.removePrefix("www.")?.equals(uri.host.removePrefix("www."), ignoreCase = true) == true) {
            "This URL does not belong to the selected source. Select the matching source and try again."
        }
        return prepare(sourceId, extensionId, Novel.Info(link = url))
    }

    suspend fun prepare(sourceId: Int, extensionId: Int, listing: Novel.Info): PreparedSourceMigration {
        val source = requireNotNull(database.novelsDao.getNovel(sourceId)) { "The original novel is no longer available." }
        require(listing.link.isNotBlank()) { "The source returned a novel without a URL." }
        val extension = requireNotNull(getExtension(extensionId)) { "The destination source is not installed." }
        val info = novels.retrieveNovelInfo(extension, listing.convertTo(extensionId), loadChapters = true)
        val chapters = info.chapters.filter { it.link.isNotBlank() }.distinctBy { it.link }.mapIndexed { i, c ->
            c.copy(order = if (c.order.isFinite()) c.order else i.toDouble())
        }.toTypedArray()
        require(chapters.isNotEmpty()) {
            "The destination returned no chapters. Check the URL, sign in or verify Cloudflare in Browse/WebView, then try again."
        }
        val target = info.copy(
            link = info.link.ifBlank { listing.link },
            title = info.title.ifBlank { listing.title },
            chapters = chapters,
        )
        require(target.title.isNotBlank()) { "The destination did not return a novel title. Check the source and URL." }
        val existing = database.novelsDao.loadNovelID(target.link, extensionId)
            ?: database.novelsDao.loadNovelID(listing.link, extensionId)
        require(existing != sourceId) { "That is already this novel on its current source. Choose a different result or source." }
        return PreparedSourceMigration(
            source, extensionId, listing.link, target,
            database.chaptersDao.getChapters(sourceId),
            existing?.let { database.novelsDao.getNovel(it)?.bookmarked } == true,
        )
    }

    /** A single Room transaction. No network, destructive delete, or file move. */
    suspend fun migrate(prepared: PreparedSourceMigration, keepOriginal: Boolean, usePosition: Boolean): SourceMigrationOutcome =
        database.withTransaction {
            val oldId = requireNotNull(prepared.source.id)
            val old = requireNotNull(database.novelsDao.getNovel(oldId)) { "The original novel no longer exists." }
            val existingId = database.novelsDao.loadNovelID(prepared.target.link, prepared.extensionId)
                ?: database.novelsDao.loadNovelID(prepared.requestedUrl, prepared.extensionId)
            require(existingId != oldId) { "The destination is the original novel. Choose another result." }
            val metadata = prepared.target.asEntity(prepared.target.link, prepared.extensionId).toDB()
            val targetId = if (existingId == null) {
                val row = database.novelsDao.insertAbort(metadata.copy(bookmarked = true))
                require(row in 1..Int.MAX_VALUE.toLong()) { "Unable to save the destination novel." }
                row.toInt()
            } else {
                // UPDATE, not INSERT REPLACE: replacing a parent cascades to chapters.
                database.novelsDao.update(metadata.copy(id = existingId, bookmarked = true))
                existingId
            }

            // Upsert by URL only. An old target chapter's order must never cause its
            // progress to be assigned to a different URL. Retain obsolete target
            // chapters/history/downloads instead of deleting them during migration.
            val oldTargetChapters = database.chaptersDao.getChapters(targetId).associateBy { it.url }
            val newChapters = prepared.target.chapters.mapNotNull { chapter ->
                val found = oldTargetChapters[chapter.link]
                if (found == null) {
                    chapter.entity(targetId, prepared.extensionId).toDB()
                } else {
                    database.chaptersDao.update(found.copy(title = chapter.title, releaseDate = chapter.release, order = chapter.order))
                    null
                }
            }
            if (newChapters.isNotEmpty()) database.chaptersDao.insertAllAbort(newChapters)
            val activeUrls = prepared.target.chapters.map { it.link }.toHashSet()
            val destination = database.chaptersDao.getChapters(targetId).filter { it.url in activeUrls }
            val original = database.chaptersDao.getChapters(oldId)
            val plan = ChapterMigrationPlanner.plan(original.map { it.asMigrationChapter() }, destination.map { it.asMigrationChapter() }, usePosition)
            require(keepOriginal || plan.unmatchedProgress.isEmpty()) {
                "Some progress or bookmarks cannot be matched. Keep the original in your library and try again."
            }
            val originalById = original.associateBy { requireNotNull(it.id) }
            val destinationById = destination.associateBy { requireNotNull(it.id) }
            for ((from, to) in plan.matches) {
                val a = originalById.getValue(from)
                val b = destinationById.getValue(to)
                val merged = ChapterMigrationPlanner.merge(a.asMigrationChapter(), b.asMigrationChapter())
                database.chaptersDao.update(b.copy(
                    readingStatus = merged.readingStatus,
                    readingPosition = merged.readingPosition,
                    bookmarked = merged.bookmarked,
                    // isSaved intentionally remains the target's OWN state. Its
                    // different URL/file/type cannot inherit an original download.
                ))
                val history = database.chapterHistoryDao.get(from)
                if (history != null) {
                    val existingHistory = database.chapterHistoryDao.get(to)
                    if (existingHistory == null) {
                        database.chapterHistoryDao.insertAbort(history.copy(id = null, novelId = targetId, chapterId = to))
                    } else if ((history.endedReadingAt ?: history.startedReadingAt) >
                        (existingHistory.endedReadingAt ?: existingHistory.startedReadingAt)) {
                        database.chapterHistoryDao.update(existingHistory.copy(
                            startedReadingAt = history.startedReadingAt, endedReadingAt = history.endedReadingAt,
                        ))
                    }
                }
            }

            val targetCategories = database.novelCategoriesDao.getNovelCategoriesFromNovel(targetId).map { it.categoryID }.toHashSet()
            val missingCategories = database.novelCategoriesDao.getNovelCategoriesFromNovel(oldId)
                .distinctBy { it.categoryID }.filter { it.categoryID !in targetCategories }
                .map { DBNovelCategoryEntity(novelID = targetId, categoryID = it.categoryID) }
            if (missingCategories.isNotEmpty()) database.novelCategoriesDao.insertAllAbort(missingCategories)
            if (database.novelPinsDao.get(oldId)?.pinned == true) database.novelPinsDao.setPinned(listOf(targetId), true)
            if (database.novelSettingsDao.get(targetId) == null) {
                database.novelSettingsDao.get(oldId)?.let {
                    database.novelSettingsDao.insertAbort(it.copy(novelID = targetId, showOnlyDownloaded = false, showOnlyString = null, showOnlyReadingStatusOf = null, showOnlyBookmarked = false))
                }
            }
            if (database.novelReaderSettingsDao.get(targetId) == null) {
                database.novelReaderSettingsDao.get(oldId)?.let {
                    database.novelReaderSettingsDao.insertAbort(DBNovelReaderSettingEntity(targetId, it.paragraphIndentSize, it.paragraphSpacingSize))
                }
            }
            if (!keepOriginal) database.novelsDao.update(old.copy(bookmarked = false))
            SourceMigrationOutcome(oldId, targetId, prepared.target.title, plan, keepOriginal)
        }
}
