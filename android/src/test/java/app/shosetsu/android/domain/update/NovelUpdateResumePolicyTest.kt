package app.shosetsu.android.domain.update

import org.junit.Assert.assertEquals
import org.junit.Test

class NovelUpdateResumePolicyTest {
    @Test fun noCheckpointStartsAtBeginning() {
        assertEquals(0, NovelUpdateResumePolicy.nextIndex(listOf(11, 22, 33), null))
    }

    @Test fun resumesImmediatelyAfterLastCompletedNovel() {
        assertEquals(2, NovelUpdateResumePolicy.nextIndex(listOf(11, 22, 33), 22))
    }

    @Test fun staleCheckpointStartsAtBeginning() {
        assertEquals(0, NovelUpdateResumePolicy.nextIndex(listOf(11, 22, 33), 99))
    }

    @Test fun completedLastNovelResumesAtEnd() {
        assertEquals(3, NovelUpdateResumePolicy.nextIndex(listOf(11, 22, 33), 33))
        assertEquals(0, NovelUpdateResumePolicy.nextIndex(emptyList(), 33))
    }
}
