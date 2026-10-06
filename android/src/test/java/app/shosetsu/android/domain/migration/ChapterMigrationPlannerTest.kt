package app.shosetsu.android.domain.migration

import app.shosetsu.android.common.enums.ReadingStatus
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/* Nameless migration regression tests, 2026-10-05. GPL-3.0. */
class ChapterMigrationPlannerTest {
    private fun chapter(id: Int, title: String, read: Boolean = true, order: Double = id.toDouble()) =
        MigrationChapter(id, title, order, if (read) ReadingStatus.READ else ReadingStatus.UNREAD)

    @Test fun matchesNumberAcrossDifferentTitlesAndMissingChapters() {
        val old = listOf(chapter(1, "Chapter 1: Start"), chapter(2, "Chapter 2: Missing"), chapter(3, "Chapter 3: End"))
        val new = listOf(chapter(101, "Ch. 1 Another translation", false), chapter(103, "Chapter 3", false))
        val plan = ChapterMigrationPlanner.plan(old, new)
        assertEquals(mapOf(1 to 101, 3 to 103), plan.matches)
        assertEquals(listOf(2), plan.unmatchedProgress.map { it.id })
    }

    @Test fun doesNotUseReadCountAsAFrontier() {
        val old = listOf(chapter(1, "Chapter 1"), chapter(2, "Chapter 2", false), chapter(3, "Chapter 3"))
        val new = (1..4).map { chapter(it + 100, "Chapter $it", false) }
        val plan = ChapterMigrationPlanner.plan(old, new)
        val oldById = old.associateBy { it.id }
        val newById = new.associateBy { it.id }
        val readIds = plan.matches.filter { (a, b) -> ChapterMigrationPlanner.merge(oldById.getValue(a), newById.getValue(b)).readingStatus == ReadingStatus.READ }.values
        assertEquals(setOf(101, 103), readIds.toSet())
    }

    @Test fun rejectsAmbiguousRepeatedNumbers() {
        val plan = ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 5: A"), chapter(2, "Chapter 5: B")), listOf(chapter(105, "Chapter 5", false)))
        assertTrue(plan.matches.isEmpty())
        assertEquals(2, plan.unmatchedProgress.size)
    }

    @Test fun repeatedVolumesCannotMatchOneUnlabelledChapter() {
        val plan = ChapterMigrationPlanner.plan(listOf(chapter(1, "Volume 1 Chapter 1"), chapter(2, "Volume 2 Chapter 1")), listOf(chapter(101, "Chapter 1", false)))
        assertTrue(plan.matches.isEmpty())
    }

    @Test fun knownDifferentVolumesNeverCrossMatch() {
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Volume 1 Chapter 4")), listOf(chapter(104, "Volume 2 Chapter 4", false))).matches.isEmpty())
    }

    @Test fun romanAndNumericVolumeLabelsMatch() {
        assertEquals(mapOf(1 to 104), ChapterMigrationPlanner.plan(listOf(chapter(1, "Volume II Chapter 4")), listOf(chapter(104, "Vol. 2 Ch. 4", false))).matches)
    }

    @Test fun globallyUniqueMissingVolumeLabelCanMatch() {
        assertEquals(mapOf(1 to 104), ChapterMigrationPlanner.plan(listOf(chapter(1, "Volume 2 Chapter 4")), listOf(chapter(104, "Chapter 4", false))).matches)
    }

    @Test fun fractionalChaptersAreNotTruncated() {
        val plan = ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 1"), chapter(2, "Chapter 1.5")), listOf(chapter(101, "Ch 1.50", false)))
        assertEquals(mapOf(2 to 101), plan.matches)
        assertEquals(listOf(1), plan.unmatchedProgress.map { it.id })
    }

    @Test fun leadingZeroesAndBareNumbersAreNormalized() {
        assertEquals(mapOf(1 to 103), ChapterMigrationPlanner.plan(listOf(chapter(1, "0003 — Hello")), listOf(chapter(103, "Chapter 3", false))).matches)
    }

    @Test fun partsAreNotSilentlyCombined() {
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 4 Part 1")), listOf(chapter(104, "Chapter 4 Part 2", false))).matches.isEmpty())
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 4 Part 1")), listOf(chapter(104, "Chapter 4", false))).matches.isEmpty())
    }

    @Test fun implicitPartsCannotMatchWholeOrFractionalChapters() {
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 4 (1)")), listOf(chapter(104, "Chapter 4", false))).matches.isEmpty())
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 4 (1)")), listOf(chapter(104, "Chapter 4.1", false))).matches.isEmpty())
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter_4_Part_1")), listOf(chapter(104, "Chapter 4", false))).matches.isEmpty())
    }

    @Test fun uniqueTitlesAndUnicodeNumbersMatch() {
        assertEquals(mapOf(1 to 101), ChapterMigrationPlanner.plan(listOf(chapter(1, "  Prologue  ")), listOf(chapter(101, "PROLOGUE!", false))).matches)
        assertEquals(mapOf(2 to 103), ChapterMigrationPlanner.plan(listOf(chapter(2, "第３章 开始")), listOf(chapter(103, "Chapter 3", false))).matches)
    }

    @Test fun blankOrDuplicateTitlesAreNotGuessed() {
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "")), listOf(chapter(101, "", false))).matches.isEmpty())
        assertTrue(ChapterMigrationPlanner.plan(listOf(chapter(1, "Extra"), chapter(2, "Extra")), listOf(chapter(101, "Extra", false))).matches.isEmpty())
    }

    @Test fun positionalMatchingRequiresExplicitOptIn() {
        val old = listOf(chapter(1, "Opening", order = 0.0), chapter(2, "A new day", order = 1.0))
        val new = listOf(chapter(102, "Daybreak", false, 1.0), chapter(101, "Introduction", false, 0.0))
        assertTrue(ChapterMigrationPlanner.plan(old, new).matches.isEmpty())
        val plan = ChapterMigrationPlanner.plan(old, new, true)
        assertEquals(mapOf(1 to 101, 2 to 102), plan.matches)
        assertEquals(2, plan.positionalMatches)
    }

    @Test fun positionalMatchingDoesNotStealKnownChapterMatches() {
        val old = listOf(chapter(1, "Chapter 1"), chapter(2, "Chapter 2"), chapter(3, "Chapter 3"))
        val new = listOf(chapter(101, "Chapter 1", false), chapter(103, "Chapter 3", false))
        val plan = ChapterMigrationPlanner.plan(old, new, true)
        assertEquals(mapOf(1 to 101, 3 to 103), plan.matches)
        assertEquals(listOf(2), plan.unmatchedProgress.map { it.id })
    }

    @Test fun existingTargetProgressAndBookmarksCannotBeDowngraded() {
        val old = MigrationChapter(1, "Chapter 1", 1.0, ReadingStatus.READING, .25, false)
        val new = MigrationChapter(101, "Chapter 1", 1.0, ReadingStatus.READ, .90, true)
        val result = ChapterMigrationPlanner.merge(old, new)
        assertEquals(ReadingStatus.READ, result.readingStatus)
        assertEquals(.90, result.readingPosition, .0001)
        assertTrue(result.bookmarked)
    }

    @Test fun copiesReadingPositionAndBookmarks() {
        val old = MigrationChapter(1, "Chapter 1", 1.0, ReadingStatus.READING, .45, true)
        val result = ChapterMigrationPlanner.merge(old, chapter(101, "Chapter 1", false))
        assertEquals(ReadingStatus.READING, result.readingStatus)
        assertEquals(.45, result.readingPosition, .0001)
        assertTrue(result.bookmarked)
    }

    @Test fun invalidPositionsDoNotPoisonProgress() {
        val a = MigrationChapter(1, "A", 0.0, readingPosition = Double.NaN)
        val b = MigrationChapter(2, "B", 0.0, readingPosition = -.5)
        assertEquals(0.0, ChapterMigrationPlanner.merge(a, b).readingPosition, 0.0)
    }

    @Test fun countsReadReadingBookmarksAndUnmatchedProgressSeparately() {
        val old = listOf(MigrationChapter(1, "Chapter 1", 1.0, ReadingStatus.READ),
            MigrationChapter(2, "Chapter 2", 2.0, ReadingStatus.READING, .25),
            MigrationChapter(3, "Chapter 3", 3.0, bookmarked = true),
            MigrationChapter(4, "A missing extra", 4.0, bookmarked = true))
        val plan = ChapterMigrationPlanner.plan(old, (1..3).map { chapter(100+it, "Chapter $it", false) })
        assertEquals(1, plan.readMatched); assertEquals(1, plan.readingMatched); assertEquals(1, plan.bookmarksMatched)
        assertEquals(listOf(4), plan.unmatchedProgress.map { it.id })
    }

    @Test fun emptyListsAreSafe() {
        assertTrue(ChapterMigrationPlanner.plan(emptyList(), emptyList()).matches.isEmpty())
        assertEquals(1, ChapterMigrationPlanner.plan(listOf(chapter(1, "Chapter 1")), emptyList()).unmatchedProgress.size)
    }

    @Test fun randomizedPlansAlwaysRemainOneToOne() {
        val random = Random(20261005)
        repeat(500) {
            val old = List(random.nextInt(1, 25)) { i -> chapter(i+1, "Chapter ${random.nextInt(1, 12)}", random.nextBoolean()) }
            val new = List(random.nextInt(1, 25)) { i -> chapter(i+101, "Chapter ${random.nextInt(1, 12)}", false) }
            for (positional in listOf(false, true)) {
                val plan = ChapterMigrationPlanner.plan(old, new, positional)
                assertEquals(plan.matches.size, plan.matches.values.toSet().size)
                assertTrue(plan.matches.keys.all { id -> old.any { it.id == id } })
                assertTrue(plan.matches.values.all { id -> new.any { it.id == id } })
                assertEquals(old.count { it.hasProgress && it.id !in plan.matches }, plan.unmatchedProgress.size)
            }
        }
    }
}
