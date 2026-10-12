package app.shosetsu.android.domain.update

/**
 * Chooses where a restarted bulk library update should resume.
 *
 * A missing or stale checkpoint is safe: start over rather than skip novels.
 */
internal object NovelUpdateResumePolicy {
    fun nextIndex(orderedNovelIds: List<Int>, lastCompletedNovelId: Int?): Int {
        if (lastCompletedNovelId == null) return 0
        val lastIndex = orderedNovelIds.indexOf(lastCompletedNovelId)
        return if (lastIndex < 0) 0 else lastIndex + 1
    }
}
