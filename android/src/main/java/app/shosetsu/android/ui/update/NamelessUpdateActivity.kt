package app.shosetsu.android.ui.update

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.BuildConfig
import app.shosetsu.android.common.consts.APK_MIME
import app.shosetsu.android.common.enums.AppThemes
import app.shosetsu.android.common.ext.getUriCompat
import app.shosetsu.android.domain.model.local.AppUpdateEntity
import app.shosetsu.android.domain.repository.base.IAppUpdatesRepository
import app.shosetsu.android.domain.update.AndroidApkVerifier
import app.shosetsu.android.domain.update.NamelessUpdatePolicy
import app.shosetsu.android.ui.theme.ShosetsuTheme
import app.shosetsu.android.view.compose.NavigateBackButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import java.io.File

/* Nameless consent-based, verified GitHub updater, 2026-10-06. GPL-3.0. */
class NamelessUpdateActivity : ComponentActivity(), DIAware {
    override val di: DI by closestDI()
    private val repository: IAppUpdatesRepository by instance()
    private lateinit var model: NamelessUpdateViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NamelessUpdateViewModel(repository) as T
        })[NamelessUpdateViewModel::class.java]
        setContent {
            val state by model.state.collectAsState()
            ShosetsuTheme(AppThemes.FOLLOW_SYSTEM) {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Nameless updates") }, navigationIcon = { NavigateBackButton { finish() } })
                }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Installed: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                        Text("GitHub: ${NamelessUpdatePolicy.REPOSITORY}", style = MaterialTheme.typography.bodyMedium)
                        Text("Stable releases only. Your library stays in place. Android will ask you to confirm installation.")
                        if (state.busy) CircularProgressIndicator()
                        Text(state.message, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        state.update?.let { update ->
                            Text("Available: ${update.version}", style = MaterialTheme.typography.titleLarge)
                            Text("${update.sizeBytes / (1024 * 1024)} MB · version code ${update.versionCode}")
                            if (update.notes.isNotEmpty()) Text(update.notes.joinToString("\n"))
                            if (state.apkPath == null) {
                                Button(onClick = model::download, enabled = !state.busy) { Text("Download update") }
                            } else {
                                Button(onClick = { install(state.apkPath!!, update) }, enabled = !state.busy) { Text("Install verified update") }
                                Text("If Android asks, allow Nameless to install updates, return here, and tap Install again.")
                            }
                        }
                        OutlinedButton(onClick = model::check, enabled = !state.busy) { Text("Check now") }
                        TextButton(onClick = {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, NamelessUpdatePolicy.RELEASES_URL.toUri())) }
                        }) { Text("Open GitHub releases") }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        if (savedInstanceState == null) model.check()
    }

    private fun install(path: String, update: AppUpdateEntity) {
        // Revalidate immediately before handing the private cached APK to Android.
        model.verifyForInstall(applicationContext, path, update) {
            try {
                if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(this, "Allow Nameless to install updates, then return and tap Install.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(File(path).getUriCompat(this@NamelessUpdateActivity), APK_MIME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            } catch (e: Exception) {
                model.showError("Android could not open the installer: ${e.message ?: "check device restrictions"}")
            }
        }
    }
}

data class NamelessUpdateUiState(
    val busy: Boolean = false,
    val error: Boolean = false,
    val message: String = "Ready to check GitHub releases.",
    val update: AppUpdateEntity? = null,
    val apkPath: String? = null,
)

class NamelessUpdateViewModel(private val repository: IAppUpdatesRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(NamelessUpdateUiState())
    val state = mutableState.asStateFlow()

    fun check() {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = NamelessUpdateUiState(busy = true, message = "Checking GitHub…")
            try {
                val update = repository.fetch()
                mutableState.value = NamelessUpdateUiState(
                    update = update,
                    message = if (update == null) "No newer, ready stable release is available. For a new repository, publish a release and wait for its build to finish." else "A signed Nameless update is available.",
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e.message ?: "Could not check for updates. Try again when online.") }
        }
    }

    fun download() {
        if (mutableState.value.busy || mutableState.value.update == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = false, message = "Downloading and verifying APK…")
            try {
                val path = repository.downloadAppUpdate()
                mutableState.value = mutableState.value.copy(busy = false, apkPath = path, message = "SHA-256, app identity, newer version and original signing certificate verified. Ready to install.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e.message ?: "Download failed. No installed data was changed.") }
        }
    }

    fun verifyForInstall(context: android.content.Context, path: String, update: AppUpdateEntity, install: () -> Unit) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = false, message = "Verifying before installation…")
            try {
                withContext(Dispatchers.IO) { AndroidApkVerifier.verify(context, File(path), update) }
                mutableState.value = mutableState.value.copy(busy = false, message = "Ready. Confirm the update in Android's installer.")
                install()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e.message ?: "The cached APK is no longer valid. Download again.") }
        }
    }

    fun showError(message: String) { mutableState.value = mutableState.value.copy(busy = false, error = true, message = message) }
}
