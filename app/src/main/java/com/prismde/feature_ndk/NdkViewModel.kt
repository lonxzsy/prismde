package com.prismde.feature_ndk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prismde.core.datastore.SettingsRepository
import com.prismde.core.model.DefaultNdkCatalog
import com.prismde.core.model.NdkVersion
import com.prismde.feature_ndk.engine.NdkDownloader
import com.prismde.feature_ndk.engine.NdkExtractor
import com.prismde.feature_ndk.engine.NdkValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.prismde.R
import java.io.File

data class NdkUiState(
    val versions: List<NdkVersion> = emptyList(),
    val activeTag: String = "r26c",
    val customUrl: String = "",
    val downloadingTag: String? = null,
    val downloadPercent: Float = 0f,
    val downloadSpeed: Long = 0L,
    val statusMessage: String? = null
)

class NdkViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)
    private val httpClient = OkHttpClient()
    private val downloader = NdkDownloader(httpClient)
    private val extractor = NdkExtractor()

    private val _uiState = MutableStateFlow(NdkUiState())
    val uiState: StateFlow<NdkUiState> = _uiState.asStateFlow()

    private val ndkStorageDir: File
        get() = File(context.filesDir, "ndk").also { it.mkdirs() }

    init {
        refreshVersions()
        viewModelScope.launch {
            settingsRepo.activeNdkFlow.collect { tag ->
                _uiState.value = _uiState.value.copy(activeTag = tag)
            }
        }
        viewModelScope.launch {
            settingsRepo.customNdkUrlFlow.collect { url ->
                _uiState.value = _uiState.value.copy(customUrl = url)
            }
        }
    }

    fun refreshVersions() {
        val list = DefaultNdkCatalog.AVAILABLE_VERSIONS.map { version ->
            val installDir = File(ndkStorageDir, version.versionTag)
            val validation = NdkValidator.validate(installDir)
            version.copy(
                isInstalled = validation.isValid,
                installPath = if (validation.isValid) (validation.actualNdkDir ?: installDir).absolutePath else null
            )
        }
        _uiState.value = _uiState.value.copy(versions = list)
    }

    fun downloadNdk(ndk: NdkVersion, forceReinstall: Boolean = false) {
        if (_uiState.value.downloadingTag != null) return
        if (ndk.isInstalled && !forceReinstall) {
            _uiState.value = _uiState.value.copy(
                statusMessage = context.getString(R.string.ndk_already_installed, ndk.displayName)
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            downloadingTag = ndk.versionTag,
            downloadPercent = 0f,
            statusMessage = context.getString(R.string.ndk_preparing_download)
        )

        viewModelScope.launch {
            val extension = when {
                ndk.downloadUrl.contains(".tar.gz") || ndk.downloadUrl.contains(".tgz") -> "tar.gz"
                ndk.downloadUrl.contains(".tar.xz") -> "tar.xz"
                ndk.downloadUrl.contains(".zip") -> "zip"
                else -> "tar.gz"
            }
            val archiveFile = File(context.cacheDir, "ndk_${ndk.versionTag}.$extension")
            val targetDir = File(ndkStorageDir, ndk.versionTag)

            try {
                // Download
                downloader.download(
                    url = ndk.downloadUrl,
                    destinationFile = archiveFile,
                    onProgress = { current, total, percent, speed ->
                        _uiState.value = _uiState.value.copy(
                            downloadPercent = percent,
                            downloadSpeed = speed,
                            statusMessage = context.getString(R.string.ndk_downloading_progress, current / (1024 * 1024), total / (1024 * 1024))
                        )
                    }
                )

                // Extract
                _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.ndk_unpacking))
                val success = extractor.extract(archiveFile, targetDir) { msg ->
                    _uiState.value = _uiState.value.copy(statusMessage = msg)
                }

                if (success) {
                    archiveFile.delete()
                    setActiveNdk(ndk.versionTag)
                    refreshVersions()
                    _uiState.value = _uiState.value.copy(
                        downloadingTag = null,
                        statusMessage = context.getString(R.string.ndk_installed_success, ndk.versionTag)
                    )
                } else {
                    archiveFile.delete()
                    targetDir.deleteRecursively()
                    _uiState.value = _uiState.value.copy(
                        downloadingTag = null,
                        statusMessage = context.getString(R.string.ndk_unpack_error)
                    )
                }
            } catch (e: Exception) {
                archiveFile.delete()
                _uiState.value = _uiState.value.copy(
                    downloadingTag = null,
                    statusMessage = context.getString(R.string.ndk_download_error, e.message ?: "")
                )
            }
        }
    }

    fun setActiveNdk(tag: String) {
        viewModelScope.launch {
            settingsRepo.setActiveNdk(tag)
            _uiState.value = _uiState.value.copy(activeTag = tag)
        }
    }

    fun setCustomUrl(url: String) {
        viewModelScope.launch {
            settingsRepo.setCustomNdkUrl(url)
            _uiState.value = _uiState.value.copy(customUrl = url)
        }
    }
}
