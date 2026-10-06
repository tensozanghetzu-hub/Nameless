package app.shosetsu.android.domain.migration

import android.app.Application
import androidx.room.Room
import app.shosetsu.android.common.enums.ChapterSortType
import app.shosetsu.android.common.enums.ReadingStatus
import app.shosetsu.android.domain.model.database.*
import app.shosetsu.android.domain.model.local.PreparedSourceMigration
import app.shosetsu.android.domain.repository.base.IExtensionEntitiesRepository
import app.shosetsu.android.domain.repository.base.IExtensionsRepository
import app.shosetsu.android.domain.repository.base.INovelsRepository
import app.shosetsu.android.domain.usecases.SourceMigrationUseCase
import app.shosetsu.android.domain.usecases.get.GetExtensionUseCase
import app.shosetsu.android.providers.database.ShosetsuDatabase
import app.shosetsu.lib.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/* Nameless migration database regression tests, 2026-10-05. GPL-3.0.
 * Uses the REAL generated Room DAOs and SQLite, with synthetic fixtures only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class SourceMigrationTransactionTest {
    private lateinit var db: ShosetsuDatabase
    private lateinit var migration: SourceMigrationUseCase
    private lateinit var source: DBNovelEntity
    private lateinit var oldChapters: List<DBChapterEntity>

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected network/repository call during a database-only transaction: ${method.name}")
    } as T

    @Before fun setup() = runBlocking(Dispatchers.IO) {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ShosetsuDatabase::class.java).allowMainThreadQueries().build()
        migration = SourceMigrationUseCase(db, unused<INovelsRepository>(), GetExtensionUseCase(unused<IExtensionsRepository>(), unused<IExtensionEntitiesRepository>()))
        val sourceId = db.novelsDao.insertAbort(DBNovelEntity(url = "old-book", extensionID = 1, bookmarked = true, loaded = true, title = "Fixture story")).toInt()
        source = requireNotNull(db.novelsDao.getNovel(sourceId))
        db.chaptersDao.insertAllAbort(listOf(
            DBChapterEntity(url = "old-1", novelID = sourceId, extensionID = 1, title = "Chapter 1 Start", releaseDate = "", order = 1.0, readingStatus = ReadingStatus.READ, readingPosition = 1.0, isSaved = true),
            DBChapterEntity(url = "old-2", novelID = sourceId, extensionID = 1, title = "Chapter 2 Continue", releaseDate = "", order = 2.0, readingStatus = ReadingStatus.READING, readingPosition = .45, bookmarked = true),
        ))
        oldChapters = db.chaptersDao.getChapters(sourceId).sortedBy { it.order }
        db.chapterHistoryDao.insert(sourceId, oldChapters[1].id!!, 1000, 2000)
    }
    @After fun close() { db.close() }

    private fun prepared(targetUrl: String = "new-book", extensionId: Int = 2, chapters: Array<Novel.Chapter> = arrayOf(
        Novel.Chapter(title = "Ch. 1 Other wording", link = "new-1", order = 1.0),
        Novel.Chapter(title = "Chapter 2", link = "new-2", order = 2.0),
    )) = PreparedSourceMigration(source, extensionId, targetUrl,
        Novel.Info(title = "Fixture story — alternate source", link = targetUrl, chapters = chapters), oldChapters, false)

    @Test fun copiesProgressCategoriesPinsReaderSettingsAndHistoryWithoutMovingDownloads() = runBlocking(Dispatchers.IO) {
        val sourceId = source.id!!
        val categoryId = db.categoriesDao.insertAbort(DBCategoryEntity(name = "Reading", order = 1)).toInt()
        db.novelCategoriesDao.insertAbort(DBNovelCategoryEntity(novelID = sourceId, categoryID = categoryId))
        db.novelPinsDao.insertAbort(DBNovelPinEntity(sourceId, true))
        db.novelReaderSettingsDao.insertAbort(DBNovelReaderSettingEntity(sourceId, 2, 1.5f))
        val outcome = migration.migrate(prepared(), keepOriginal = true, usePosition = false)
        val migrated = db.chaptersDao.getChapters(outcome.targetId).sortedBy { it.order }
        assertTrue(db.novelsDao.getNovel(sourceId)!!.bookmarked)
        assertTrue(db.novelsDao.getNovel(outcome.targetId)!!.bookmarked)
        assertEquals(ReadingStatus.READ, migrated[0].readingStatus)
        assertEquals(ReadingStatus.READING, migrated[1].readingStatus)
        assertEquals(.45, migrated[1].readingPosition, .00001)
        assertTrue(migrated[1].bookmarked)
        assertFalse(migrated[0].isSaved)
        assertTrue(db.chaptersDao.getChapter(oldChapters[0].id!!)!!.isSaved)
        assertEquals(categoryId, db.novelCategoriesDao.getNovelCategoriesFromNovel(outcome.targetId).single().categoryID)
        assertTrue(db.novelPinsDao.get(outcome.targetId)!!.pinned)
        assertEquals(2, db.novelReaderSettingsDao.get(outcome.targetId)!!.paragraphIndentSize)
        val history = db.chapterHistoryDao.get(migrated[1].id!!)!!
        assertEquals(outcome.targetId, history.novelId)
        assertEquals(1000L, history.startedReadingAt)
        assertEquals(2000L, history.endedReadingAt)
        assertNotNull(db.chapterHistoryDao.get(oldChapters[1].id!!))
        assertEquals(11, db.openHelper.readableDatabase.version)
    }

    @Test fun replacingLibraryEntryDoesNotDeleteOriginalDataAndCanBeRepeated() = runBlocking(Dispatchers.IO) {
        val first = migration.migrate(prepared(), keepOriginal = false, usePosition = false)
        assertFalse(db.novelsDao.getNovel(source.id!!)!!.bookmarked)
        assertEquals(2, db.chaptersDao.getChapters(source.id!!).size)
        assertNotNull(db.chapterHistoryDao.get(oldChapters[1].id!!))
        val second = migration.migrate(prepared(), keepOriginal = false, usePosition = false)
        assertEquals(first.targetId, second.targetId)
        assertEquals(2, db.chaptersDao.getChapters(first.targetId).size)
        assertEquals(1, db.chaptersDao.getChapters(first.targetId).count { it.bookmarked })
    }

    @Test fun existingDestinationProgressCategoriesAndArchivedDownloadsAreNotReset() = runBlocking(Dispatchers.IO) {
        val targetId = db.novelsDao.insertAbort(DBNovelEntity(url = "new-book", extensionID = 2, bookmarked = true, loaded = true, title = "Existing target")).toInt()
        db.chaptersDao.insertAllAbort(listOf(
            DBChapterEntity(url = "new-2", novelID = targetId, extensionID = 2, title = "Chapter 2", releaseDate = "", order = 2.0, readingStatus = ReadingStatus.READ, readingPosition = .9, isSaved = true),
            DBChapterEntity(url = "archived-extra", novelID = targetId, extensionID = 2, title = "Old extra", releaseDate = "", order = 9.0, readingStatus = ReadingStatus.READ, isSaved = true),
        ))
        val categoryId = db.categoriesDao.insertAbort(DBCategoryEntity(name = "Existing category", order = 2)).toInt()
        db.novelCategoriesDao.insertAbort(DBNovelCategoryEntity(novelID = targetId, categoryID = categoryId))
        val outcome = migration.migrate(prepared(), keepOriginal = true, usePosition = false)
        assertEquals(targetId, outcome.targetId)
        val chapters = db.chaptersDao.getChapters(targetId)
        assertEquals(3, chapters.size)
        val second = chapters.single { it.url == "new-2" }
        assertEquals(ReadingStatus.READ, second.readingStatus)
        assertEquals(.9, second.readingPosition, .00001)
        assertTrue(second.isSaved)
        assertTrue(second.bookmarked)
        assertTrue(chapters.single { it.url == "archived-extra" }.isSaved)
        assertEquals(categoryId, db.novelCategoriesDao.getNovelCategoriesFromNovel(targetId).single().categoryID)
    }

    @Test fun failureRollsBackAllTargetWritesAndKeepsOriginal() = runBlocking(Dispatchers.IO) {
        val different = prepared(chapters = arrayOf(Novel.Chapter(title = "Unrelated extra", link = "other-chapter", order = 1.0)))
        try {
            migration.migrate(different, keepOriginal = false, usePosition = false)
            fail("Unmatched reading progress must forbid removing the original from the library")
        } catch (_: IllegalArgumentException) { }
        assertNull(db.novelsDao.loadNovelID("new-book", 2))
        assertEquals(1, db.novelsDao.loadNovels().size)
        assertTrue(db.novelsDao.getNovel(source.id!!)!!.bookmarked)
        assertEquals(2, db.chaptersDao.getChapters(source.id!!).size)
        assertTrue(db.chaptersDao.getChapter(oldChapters[1].id!!)!!.bookmarked)
    }

    @Test fun sameSourceAndNovelCannotMigrateOntoItself() = runBlocking(Dispatchers.IO) {
        try {
            migration.migrate(prepared("old-book", 1), keepOriginal = false, usePosition = false)
            fail("A self-migration must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, db.novelsDao.loadNovels().size)
        assertTrue(db.novelsDao.getNovel(source.id!!)!!.bookmarked)
        assertEquals(2, db.chaptersDao.getChapters(source.id!!).size)
    }
}
