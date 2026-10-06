package app.shosetsu.android.domain.catalog

import app.shosetsu.android.common.enums.DownloadStatus
import app.shosetsu.android.domain.model.local.BrowseExtensionEntity
import app.shosetsu.android.domain.repository.base.IExtensionDownloadRepository
import app.shosetsu.android.domain.repository.base.IExtensionsRepository
import app.shosetsu.android.domain.usecases.load.LoadBrowseExtensionsUseCase
import app.shosetsu.lib.Version
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/* Exercise the production catalog use case. Repository mutations are forbidden
 * by the fixture so filtering cannot accidentally uninstall/delete source data. */
class LoadBrowseEnglishCatalogTest {
    private fun entry(id: Int, language: String, installed: Boolean = false) = BrowseExtensionEntity(
        id = id, name = "Source $id", imageURL = "", lang = language,
        isInstalled = installed, installedVersion = if (installed) Version(1, 0, 0) else null,
        installedRepo = if (installed) 1 else -1, isUpdateAvailable = installed,
        updateVersion = if (installed) Version(1, 1, 0) else null,
        isInstalling = false, isObsolete = false,
    )
    @Suppress("UNCHECKED_CAST")
    private fun <T> fake(type: Class<T>, handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        } as T

    private fun catalog(
        entries: Flow<List<BrowseExtensionEntity>>, observedIds: MutableList<Int> = mutableListOf(),
        status: DownloadStatus = DownloadStatus.WAITING,
    ): LoadBrowseExtensionsUseCase {
        val sources = fake(IExtensionsRepository::class.java) { name, _ ->
            if (name == "loadBrowseExtensions") entries else error("Unexpected source-repository call: $name")
        }
        val downloads = fake(IExtensionDownloadRepository::class.java) { name, args ->
            if (name != "getStatusFlow") error("Unexpected download-repository call: $name")
            observedIds.add(args[0] as Int)
            flowOf(status)
        }
        return LoadBrowseExtensionsUseCase(sources, downloads)
    }

    @Test fun newCatalogIsEnglishOnly() = runBlocking<Unit> {
        val result = withTimeout(2000) {
            catalog(flowOf(listOf(entry(1, "en"), entry(2, "es"), entry(3, "ru"), entry(4, "en-GB")))).invoke().first()
        }
        assertEquals(listOf(1, 4), result.map { it.id })
    }
    @Test fun installedNonEnglishSourcesAndUpdateMetadataArePreserved() = runBlocking<Unit> {
        val original = entry(2, "es", true)
        val result = withTimeout(2000) { catalog(flowOf(listOf(entry(1, "en"), original))).invoke().first() }
        val installed = result.single { it.id == 2 }
        assertTrue(installed.isInstalled)
        assertEquals(original.lang, installed.lang)
        assertEquals(original.installedVersion, installed.installedVersion)
        assertEquals(original.installedRepo, installed.installedRepo)
        assertEquals(original.updateVersion, installed.updateVersion)
        assertTrue(installed.isUpdateAvailable)
    }
    @Test fun allForeignNewSourcesEmitAnEmptyListInsteadOfHanging() = runBlocking<Unit> {
        val result = withTimeout(2000) { catalog(flowOf(listOf(entry(2, "es"), entry(3, "ru")))).invoke().first() }
        assertTrue(result.isEmpty())
    }
    @Test fun emptyCatalogEmitsAnEmptyList() = runBlocking<Unit> {
        val result = withTimeout(2000) { catalog(flowOf(emptyList())).invoke().first() }
        assertTrue(result.isEmpty())
    }
    @Test fun installedStatusChangesReevaluateVisibilityWithoutDeletingRows() = runBlocking<Unit> {
        val source = MutableStateFlow(listOf(entry(2, "es", true)))
        val results = withTimeout(2000) {
            catalog(source).invoke().onEach { if (it.isNotEmpty()) source.value = listOf(entry(2, "es", false)) }.take(2).toList()
        }
        assertEquals(listOf(2), results[0].map { it.id })
        assertTrue(results[1].isEmpty())
        assertEquals(1, source.value.size) // Underlying repository data is retained.
    }
    @Test fun visibleSourceInstallationStatusStillUpdates() = runBlocking<Unit> {
        val result = withTimeout(2000) { catalog(flowOf(listOf(entry(1, "en"))), status = DownloadStatus.DOWNLOADING).invoke().first() }
        assertTrue(result.single().isInstalling)
    }
    @Test fun inputRepositoryListIsUnmodified() = runBlocking<Unit> {
        val original = listOf(entry(1, "en"), entry(2, "es"), entry(3, "unknown", true))
        val result = withTimeout(2000) { catalog(flowOf(original)).invoke().first() }
        assertEquals(listOf(1, 3), result.map { it.id })
        assertEquals(listOf(1, 2, 3), original.map { it.id })
    }
    @Test fun hiddenUninstalledRowsDoNotSubscribeToDownloadState() = runBlocking<Unit> {
        val observed = mutableListOf<Int>()
        withTimeout(2000) { catalog(flowOf(listOf(entry(1, "en"), entry(2, "es"), entry(3, "es", true))), observed).invoke().first() }
        assertEquals(listOf(1, 3), observed)
    }
}
