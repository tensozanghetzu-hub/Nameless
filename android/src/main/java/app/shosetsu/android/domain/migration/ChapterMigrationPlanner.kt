package app.shosetsu.android.domain.migration

import app.shosetsu.android.common.enums.ReadingStatus
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

/* Nameless source-migration implementation, modified 2026-10-05.
 * GPL-3.0; original Shosetsu licensing and attribution are preserved.
 */

/** Pure, deterministic matching logic; no Android or database side effects. */
data class MigrationChapter(
    val id: Int,
    val title: String,
    val order: Double,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val readingPosition: Double = 0.0,
    val bookmarked: Boolean = false,
) {
    val hasProgress: Boolean get() = readingStatus == ReadingStatus.READ ||
        readingStatus == ReadingStatus.READING || bookmarked ||
        (readingPosition.isFinite() && readingPosition > 0.0)
}

data class ChapterMigrationPlan(
    val matches: Map<Int, Int>,
    val readMatched: Int,
    val readingMatched: Int,
    val bookmarksMatched: Int,
    val unmatchedProgress: List<MigrationChapter>,
    val positionalMatches: Int,
    val sourceCount: Int,
    val targetCount: Int,
)

data class MigratedChapterProgress(
    val readingStatus: ReadingStatus,
    val readingPosition: Double,
    val bookmarked: Boolean,
)

object ChapterMigrationPlanner {
    private data class ChapterNumber(val volume: String?, val number: String, val part: String?)
    private val numberLabel = Regex("\\b(?:chapter|chap|ch|episode|ep)\\.?\\s*#?\\s*(\\d+(?:\\.\\d+)?)(?=\\s|[:：,;()\\-—–.]|$)")
    private val bareNumber = Regex("^\\s*(\\d+(?:\\.\\d+)?)(?=\\s|[:：()\\-—–.]|$)")
    private val chineseNumber = Regex("第\\s*(\\d+(?:\\.\\d+)?)\\s*[章节話话]")
    private val volume = Regex("\\b(?:vol(?:ume)?|book|arc)\\.?\\s*(\\d+|[ivxlcdm]+)\\b")
    private val part = Regex("\\b(?:part|pt)\\.?\\s*(\\d+|[a-z])\\b")

    private fun normalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun decimal(value: String): String = BigDecimal(value).stripTrailingZeros().toPlainString()

    private fun volumeNumber(value: String): String {
        if (value.all { it.isDigit() }) return decimal(value)
        val values = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)
        var total = 0
        var previous = 0
        value.reversed().forEach { char ->
            val n = values[char] ?: 0
            total += if (n < previous) -n else n
            previous = n
        }
        return total.toString()
    }

    private fun chapterNumber(title: String): ChapterNumber? {
        val text = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('_', ' ')
        val found = numberLabel.find(text) ?: chineseNumber.find(text) ?: bareNumber.find(text) ?: return null
        return ChapterNumber(
            volume.find(text)?.groupValues?.get(1)?.let(::volumeNumber),
            decimal(found.groupValues[1]),
            (part.find(text)?.groupValues?.get(1)
                ?: Regex("^\\s*\\((\\d+)\\)").find(text.substring(found.range.last + 1))?.groupValues?.get(1))
                ?.let { if (it.all(Char::isDigit)) decimal(it) else it },
        )
    }

    /** Never guess from a partial title, a read-count frontier, or a duplicate number.
     * Matching by list position is strictly opt-in and is counted in the preview.
     */
    fun plan(source: List<MigrationChapter>, target: List<MigrationChapter>, usePosition: Boolean = false): ChapterMigrationPlan {
        require(source.map { it.id }.distinct().size == source.size) { "Duplicate source chapter IDs" }
        require(target.map { it.id }.distinct().size == target.size) { "Duplicate target chapter IDs" }
        val matches = linkedMapOf<Int, Int>()
        val usedTargets = hashSetOf<Int>()
        val sourceNumbers = source.associate { it.id to chapterNumber(it.title) }
        val targetNumbers = target.associate { it.id to chapterNumber(it.title) }

        fun <K : Any> matchUnique(key: (MigrationChapter, Boolean) -> K?, compatible: (MigrationChapter, MigrationChapter) -> Boolean = { _, _ -> true }) {
            // Group the COMPLETE lists, not the remaining lists: earlier matches must
            // not make an ambiguous duplicated chapter number appear unique.
            val old = source.mapNotNull { c -> key(c, true)?.let { it to c } }.groupBy({ it.first }, { it.second })
            val new = target.mapNotNull { c -> key(c, false)?.let { it to c } }.groupBy({ it.first }, { it.second })
            for ((k, olds) in old) {
                val news = new[k] ?: continue
                if (olds.size != 1 || news.size != 1) continue
                val a = olds.single()
                val b = news.single()
                if (a.id !in matches && b.id !in usedTargets && compatible(a, b)) {
                    matches[a.id] = b.id
                    usedTargets += b.id
                }
            }
        }

        matchUnique({ c, old -> if (old) sourceNumbers[c.id] else targetNumbers[c.id] })
        // Permit missing volume labels only if the chapter+part is globally unique
        // on BOTH sources. Two explicitly different volumes never cross-match.
        matchUnique({ c, old -> (if (old) sourceNumbers[c.id] else targetNumbers[c.id])?.let { it.number to it.part } }) { a, b ->
            val av = sourceNumbers[a.id]?.volume
            val bv = targetNumbers[b.id]?.volume
            av == null || bv == null || av == bv
        }
        matchUnique({ c, _ -> normalize(c.title).takeIf { it.isNotEmpty() } }) { a, b ->
            val av = sourceNumbers[a.id]
            val bv = targetNumbers[b.id]
            av == null || bv == null || (av.number == bv.number && av.part == bv.part &&
                (av.volume == null || bv.volume == null || av.volume == bv.volume))
        }

        var byPosition = 0
        if (usePosition) {
            val ordering = compareBy<MigrationChapter> { if (it.order.isFinite()) it.order else Double.MAX_VALUE }.thenBy { it.id }
            val old = source.sortedWith(ordering)
            val new = target.sortedWith(ordering)
            old.forEachIndexed { index, a ->
                val b = new.getOrNull(index)
                if (b != null && a.id !in matches && b.id !in usedTargets) {
                    matches[a.id] = b.id
                    usedTargets += b.id
                    byPosition++
                }
            }
        }
        val matched = source.filter { it.id in matches }
        return ChapterMigrationPlan(
            matches.toMap(),
            matched.count { it.readingStatus == ReadingStatus.READ },
            matched.count { it.readingStatus == ReadingStatus.READING },
            matched.count { it.bookmarked },
            source.filter { it.hasProgress && it.id !in matches },
            byPosition, source.size, target.size,
        )
    }

    /** Merge rather than replace: never turn an already-read target chapter unread. */
    fun merge(source: MigrationChapter, target: MigrationChapter): MigratedChapterProgress {
        fun rank(s: ReadingStatus) = when (s) { ReadingStatus.READ -> 2; ReadingStatus.READING -> 1; else -> 0 }
        fun position(p: Double) = if (p.isFinite() && p >= 0.0) p else 0.0
        return MigratedChapterProgress(
            if (rank(source.readingStatus) > rank(target.readingStatus)) source.readingStatus else target.readingStatus,
            maxOf(position(source.readingPosition), position(target.readingPosition)),
            source.bookmarked || target.bookmarked,
        )
    }
}
