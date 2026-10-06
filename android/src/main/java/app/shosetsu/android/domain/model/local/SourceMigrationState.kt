package app.shosetsu.android.domain.model.local

import app.shosetsu.android.domain.migration.ChapterMigrationPlan
import app.shosetsu.android.domain.migration.ChapterMigrationPlanner
import app.shosetsu.android.domain.migration.MigrationChapter
import app.shosetsu.android.domain.model.database.DBChapterEntity
import app.shosetsu.android.domain.model.database.DBNovelEntity
import app.shosetsu.lib.Novel

/* Nameless source migration, modified 2026-10-05. GPL-3.0. */

data class MigrationSourceNovel(val novel: DBNovelEntity, val sourceName: String) {
    val id: Int get() = requireNotNull(novel.id)
    val title: String get() = novel.title.ifBlank { "Untitled novel" }
}

data class MigrationTargetSource(val id: Int, val name: String, val language: String, val imageURL: String)

data class PreparedSourceMigration(
    val source: DBNovelEntity,
    val extensionId: Int,
    val requestedUrl: String,
    val target: Novel.Info,
    val sourceChapters: List<DBChapterEntity>,
    val targetAlreadyInLibrary: Boolean,
) {
    val downloads: Int get() = sourceChapters.count { it.isSaved }
    fun plan(usePosition: Boolean): ChapterMigrationPlan = ChapterMigrationPlanner.plan(
        sourceChapters.map { it.asMigrationChapter() },
        target.chapters.mapIndexed { index, chapter ->
            MigrationChapter(index + 1, chapter.title, chapter.order)
        },
        usePosition,
    )
}

fun DBChapterEntity.asMigrationChapter() = MigrationChapter(
    requireNotNull(id), title, order, readingStatus, readingPosition, bookmarked,
)

data class SourceMigrationOutcome(
    val sourceId: Int,
    val targetId: Int,
    val targetTitle: String,
    val plan: ChapterMigrationPlan,
    val originalKept: Boolean,
)

data class SourceMigrationState(
    val initialized: Boolean = false,
    val novels: List<MigrationSourceNovel> = emptyList(),
    val sources: List<MigrationTargetSource> = emptyList(),
    val currentNovelId: Int? = null,
    val currentSourceId: Int? = null,
    val query: String = "",
    val sourceFilter: String = "",
    val novelUrl: String = "",
    val results: List<Novel.Info> = emptyList(),
    val searched: Boolean = false,
    val searchedQuery: String = "",
    val nextPage: Int? = null,
    val prepared: PreparedSourceMigration? = null,
    val preview: ChapterMigrationPlan? = null,
    val keepOriginal: Boolean = true,
    val usePosition: Boolean = false,
    val busy: Boolean = false,
    val migrating: Boolean = false,
    val activity: String = "",
    val error: String? = null,
    val completed: Set<Int> = emptySet(),
    val outcome: SourceMigrationOutcome? = null,
) {
    val currentNovel get() = novels.find { it.id == currentNovelId }
    val currentSource get() = sources.find { it.id == currentSourceId }
    val pending get() = novels.filter { it.id !in completed }
}
