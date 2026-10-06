package app.shosetsu.lib

import app.shosetsu.lib.json.RepoIndex
import app.shosetsu.lib.json.RepoIndex.Companion.repositoryJsonParser
import com.google.common.io.Resources
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlin.test.Test

/**
 *  @since 2024 / 06 / 06
 */
class IndexTest {

	@Test
	@OptIn(ExperimentalSerializationApi::class)
	fun parseTest() {
		val resource = Resources.getResource("example/index.json")
		val repo = repositoryJsonParser.decodeFromStream<RepoIndex>(resource.openStream())
		println(repo)
	}
}