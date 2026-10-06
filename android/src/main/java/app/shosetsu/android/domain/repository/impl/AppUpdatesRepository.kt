package app.shosetsu.android.domain.repository.impl

import android.content.Context
import android.os.Build
import app.shosetsu.android.BuildConfig
import app.shosetsu.android.common.ext.launchIO
import app.shosetsu.android.common.ext.onIO
import app.shosetsu.android.datasource.local.file.base.IFileCachedAppUpdateDataSource
import app.shosetsu.android.datasource.remote.base.IRemoteAppUpdateDataSource
import app.shosetsu.android.domain.model.local.AppUpdateEntity
import app.shosetsu.android.domain.repository.base.IAppUpdatesRepository
import app.shosetsu.android.domain.update.AndroidApkVerifier
import app.shosetsu.android.domain.update.NamelessUpdatePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/* Original Shosetsu app-update repository, GPL-3.0, Doomsdayrs, 2020.
 * Nameless, modified 2026-10-06: GitHub-only updates, mandatory SHA-256,
 * package/version and same-certificate validation. Android still asks consent.
 */
class AppUpdatesRepository(
    private val remote: IRemoteAppUpdateDataSource,
    private val cache: IFileCachedAppUpdateDataSource,
    private val context: Context,
) : IAppUpdatesRepository {
    override val appUpdate = MutableStateFlow<AppUpdateEntity?>(null)
    override val canSelfUpdate: Boolean =
        !BuildConfig.DEBUG && BuildConfig.APPLICATION_ID == NamelessUpdatePolicy.APPLICATION_ID &&
            remote is IRemoteAppUpdateDataSource.Downloadable
    private val downloadMutex = Mutex()
    private val checkMutex = Mutex()

    init {
        if (canSelfUpdate) launchIO {
            checkMutex.withLock {
                try {
                    val cached = cache.load()
                    NamelessUpdatePolicy.validateCandidate(cached, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
                    appUpdate.value = cached
                } catch (_: Exception) {
                    runCatching { cache.delete() }
                }
            }
        }
    }

    override suspend fun fetch(): AppUpdateEntity? = onIO {
        if (!canSelfUpdate) return@onIO null
        checkMutex.withLock {
            val update = remote.loadAppUpdate()
            if (update.versionCode <= BuildConfig.VERSION_CODE) {
                appUpdate.value = null
                cache.delete()
                return@withLock null
            }
            NamelessUpdatePolicy.validateCandidate(update, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
            cache.save(update)
            appUpdate.value = update
            update
        }
    }

    override suspend fun downloadAppUpdate(): String = onIO {
        downloadMutex.withLock {
            if (!canSelfUpdate) throw IOException("This build cannot install standard Nameless updates.")
            val update = appUpdate.value ?: fetch() ?: throw IOException("There is no newer ready Nameless release.")
            NamelessUpdatePolicy.validateCandidate(update, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
            val downloadable = remote as IRemoteAppUpdateDataSource.Downloadable
            var path: String? = null
            try {
                val savedPath = downloadable.downloadAppUpdate(update).use { cache.writeAPK(update, it) }
                path = savedPath
                AndroidApkVerifier.verify(context.applicationContext, File(savedPath), update)
                savedPath
            } catch (e: Exception) {
                path?.let { File(it).delete() }
                throw e
            }
        }
    }
}
