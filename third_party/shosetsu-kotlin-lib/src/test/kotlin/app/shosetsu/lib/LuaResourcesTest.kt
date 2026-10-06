package app.shosetsu.lib

import app.shosetsu.lib.lua.coerceLuaToJava
import app.shosetsu.lib.lua.shosetsuGlobals
import org.junit.Test

/**
 * 15 / 01 / 2023
 */
class LuaResourcesTest {
	private val globals = shosetsuGlobals()

	@Test
	fun chapterTypeTest() {
		Novel.ChapterType.entries.forEach { type ->
			val result = globals.load("return ChapterType.${type.name}", "chapterTypeTest").call()
			assert(coerceLuaToJava<Novel.ChapterType>(result) == type)
		}
	}

	@Test
	fun filterTableTest() {
		val result =
			globals.load(
				"""
				return filter(
					{1,2,3,4}, 
					function(v) 
						return v % 2 == 0 
					end
				)
				""".trimIndent(), "filterTableTest"
			).call()

		assert(coerceLuaToJava<Array<Int>>(result).contentEquals(arrayOf(2, 4)))
	}

	// TODO filter elements
	// TODO first

	@Test
	fun flattenTest() {
		val result = globals.load(
			"""
			return flatten({{1,2},{1,2}})
			""".trimIndent(),
			"flattenTest"
		).call()
		assert(coerceLuaToJava<Array<Int>>(result).contentEquals(arrayOf(1, 2, 1, 2)))
	}

	// TODO GET

	@Test
	fun mapTest() {
		val result =
			globals.load(
				"""
				return map(
					{1,2,3,4}, 
					function(v) 
						return v * 2
					end
				)
				""".trimIndent(), "mapTest"
			).call()

		assert(coerceLuaToJava<Array<Int>>(result).contentEquals(arrayOf(2, 4, 6, 8)))
	}

	// TODO map2flat

	fun mapNotNilTest() {
		// TODO mapNotNil
		val result =
			globals.load(
				"""
				return mapNotNil(
					{1,2,3,4}, 
					function(v) 
						if ( v % 2 == 0 ) then
							return nil
						end
						return v
					end
				)
				""".trimIndent(), "mapNotNilTest"
			).call()

		assert(coerceLuaToJava<Array<Int>>(result).contentEquals(arrayOf(1, 3)))
	}

	// TODO NodeVisitor

	private fun Novel.Info.novelEquals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Novel.Info

		if (title != other.title) return false
		if (!alternativeTitles.contentEquals(other.alternativeTitles)) return false
		if (link != other.link) return false
		if (imageURL != other.imageURL) return false
		if (language != other.language) return false
		if (description != other.description) return false
		if (status != other.status) return false
		if (!tags.contentEquals(other.tags)) return false
		if (!genres.contentEquals(other.genres)) return false
		if (!authors.contentEquals(other.authors)) return false
		if (!artists.contentEquals(other.artists)) return false
		if (chapterCount != other.chapterCount) return false
		if (wordCount != other.wordCount) return false
		if (commentCount != other.commentCount) return false
		if (viewCount != other.viewCount) return false
		if (favoriteCount != other.favoriteCount) return false
		return chapters.contentEquals(other.chapters)
	}

	@Test
	fun chapterTest() {
		val title = "a"
		val link = "c"
		val release = "2023-01-14"
		val order = 0.0

		val chapter = Novel.Chapter(
			release = release,
			title = title,
			link = link,
			order = order
		)

		val luaChapter = coerceLuaToJava<Novel.Chapter>(
			globals.load(
				"""
					return NovelChapter {
						title = "$title",
						link = "$link",
						release = "$release",
						order = $order
					}
				""".trimIndent(),
				"chapterTest"
			).call()
		)
		assert(chapter == luaChapter)
	}
	// TODO chapter without table

	@Test
	fun infoTest() {
		val title = "a"
		val alternativeTitles = arrayOf("b", "c")
		val link = "link"
		val imageURL = "link"
		val language = "eng"
		val description = ""
		val status = Novel.Status.COMPLETED
		val tags = arrayOf("d", "e", "f")
		val genres = arrayOf("g", "h", "i")
		val authors = arrayOf("j", "k", "l")
		val artists = arrayOf("m", "n", "o")
		val chapters = arrayOf<Novel.Chapter>()
		val chapterCount = 0
		val wordCount = 500
		val commentCount = null
		val viewCount = 500
		val favoriteCount = 100

		val info = Novel.Info(
			title = title,
			alternativeTitles = alternativeTitles,
			link = link,
			imageURL = imageURL,
			language = language,
			description = description,
			status = status,
			tags = tags,
			genres = genres,
			authors = authors,
			artists = artists,
			chapters = chapters,
			chapterCount = chapterCount,
			wordCount = wordCount,
			commentCount = commentCount,
			viewCount = viewCount,
			favoriteCount = favoriteCount,
		)
		val script = """
			return NovelInfo {
				title = "$title",
				alternativeTitles = {"b","c"},
				link = "$link",
				imageURL = "$imageURL",
				language = "$language",
				description = "$description",
				status = NovelStatus.COMPLETED,
				tags = {"d","e","f"},
				genres = {"g","h","i"},
				authors = {"j","k","l"},
				artists = {"m","n","o"},
				chapters = {},
				chapterCount = $chapterCount,
				wordCount = $wordCount,
				commentCount = nil,
				viewCount = $viewCount,
				favoriteCount = $favoriteCount
			}
		""".trimIndent()
		val luaInfo = coerceLuaToJava<Novel.Info>(
			globals.load(
				script,
				"infoTest"
			).call()
		)
		val luaNovel = coerceLuaToJava<Novel.Info>(
			globals.load(
				script.replace("NovelInfo", "Novel"),
				"infoTest"
			).call()
		)
		println(info)
		println(luaInfo)
		println(luaNovel)
		assert(info.novelEquals(luaInfo))
		assert(info.novelEquals(luaNovel))
	}
	// TODO info without table

	@Test
	fun novelStatusTest() {
		Novel.Status.entries.forEach { type ->
			val result = globals.load("return NovelStatus.${type.name}", "novelStatusTest").call()
			assert(coerceLuaToJava<Novel.Status>(result) == type)
		}
	}

	// TODO pageOfElem
	// TODO pipeline

	// TODO POST
	// TODO wrap
}