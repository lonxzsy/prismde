package com.prismde.feature_update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.prismde.R
import java.io.File

data class UpdateUiState(
    val isChecking: Boolean = false,
    val releaseInfo: AppReleaseInfo? = null,
    val isDownloading: Boolean = false,
    val downloadPercent: Float = 0f,
    val statusMessage: String? = null
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val httpClient = OkHttpClient()
    private val updateManager = AppUpdateManager(context, httpClient)

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(manual: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            isChecking = true,
            statusMessage = if (manual) context.getString(R.string.update_checking) else null
        )

        viewModelScope.launch {
            val result = updateManager.checkForUpdates(owner = "lonxzsy", repo = "prismde")
            result.onSuccess { info ->
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    releaseInfo = info,
                    statusMessage = if (info == null && manual) context.getString(R.string.update_latest_version) else null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    statusMessage = if (manual) context.getString(R.string.update_check_error, err.message ?: "") else null
                )
            }
        }
    }

    fun downloadAndInstall() {
        val info = _uiState.value.releaseInfo ?: return
        _uiState.value = _uiState.value.copy(isDownloading = true, downloadPercent = 0f)

        viewModelScope.launch {
            val apkFile = File(context.cacheDir, "prismde_${info.versionName}.apk")
            val success = updateManager.downloadApk(info.apkDownloadUrl, apkFile) { percent, _, _ ->
                _uiState.value = _uiState.value.copy(downloadPercent = percent)
            }

            _uiState.value = _uiState.value.copy(isDownloading = false)
            if (success) {
                updateManager.launchApkInstaller(apkFile)
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = context.getString(R.string.update_download_error))
            }
        }
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(releaseInfo = null)
    }
}
