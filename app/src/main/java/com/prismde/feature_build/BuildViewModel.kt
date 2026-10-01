package com.prismde.feature_build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.Diagnostic
import com.prismde.core.model.DiagnosticSeverity
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import com.prismde.feature_build.engine.BuildOutputEvent
import com.prismde.feature_build.engine.BuildProcessRunner
import com.prismde.feature_build.engine.GeminiExplainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

data class BuildUiState(
    val isBuilding: Boolean = false,
    val buildSuccess: Boolean? = null,
    val showBottomSheet: Boolean = false,
    val diagnostics: List<Diagnostic> = emptyList(),
    val logs: List<BuildOutputEvent.LogLine> = emptyList(),
    val artifactFile: File? = null,
    val showExportDialog: Boolean = false,
    val aiExplanations: Map<String, String> = emptyMap(),
    val aiLoadingMap: Map<String, Boolean> = emptyMap()
)

class BuildViewModel : ViewModel() {

    private val runner = BuildProcessRunner()
    private val httpClient = OkHttpClient()
    private val geminiExplainer = GeminiExplainer(httpClient)

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runner.events.collect { event ->
                when (event) {
                    is BuildOutputEvent.LogLine -> {
                        _uiState.value = _uiState.value.copy(
                            logs = _uiState.value.logs + event
                        )
                    }
                    is BuildOutputEvent.DiagnosticFound -> {
                        val current = _uiState.value.diagnostics.toMutableList()
                        val existingIndex = current.indexOfFirst { 
                            it.id == event.diagnostic.id || 
                            (it.filePath == event.diagnostic.filePath && it.line == event.diagnostic.line && it.column == event.diagnostic.column && it.severity == event.diagnostic.severity)
                        }
                        if (existingIndex >= 0) {
                            current[existingIndex] = event.diagnostic
                        } else {
                            current.add(event.diagnostic)
                        }
                        // Sort so errors appear first, then warnings, then notes
                        val sorted = current.sortedBy { diag ->
                            when (diag.severity) {
                                DiagnosticSeverity.FATAL -> 0
                                DiagnosticSeverity.ERROR -> 1
                                DiagnosticSeverity.WARNING -> 2
                                DiagnosticSeverity.NOTE -> 3
                            }
                        }
                        _uiState.value = _uiState.value.copy(diagnostics = sorted)
                    }
                    is BuildOutputEvent.Completed -> {
                        _uiState.value = _uiState.value.copy(
                            isBuilding = false,
                            buildSuccess = event.success,
                            artifactFile = event.artifactFile,
                            showExportDialog = event.success && event.artifactFile != null && event.artifactFile.name.endsWith(".so")
                        )
                    }
                }
            }
        }
    }

    fun startBuild(project: Project, ndk: NdkVersion, config: BuildConfiguration) {
        _uiState.value = _uiState.value.copy(
            isBuilding = true,
            buildSuccess = null,
            showBottomSheet = true,
            diagnostics = emptyList(),
            logs = emptyList(),
            artifactFile = null,
            showExportDialog = false
        )

        viewModelScope.launch {
            runner.runBuild(project, ndk, config)
        }
    }

    fun askAiExplanation(diagnostic: Diagnostic, sourceContext: String, apiKey: String) {
        val currentLoading = _uiState.value.aiLoadingMap.toMutableMap()
        currentLoading[diagnostic.id] = true
        _uiState.value = _uiState.value.copy(aiLoadingMap = currentLoading)

        viewModelScope.launch {
            val result = geminiExplainer.explainDiagnostic(diagnostic, sourceContext, apiKey)
            val updatedLoading = _uiState.value.aiLoadingMap.toMutableMap()
            updatedLoading[diagnostic.id] = false

            val updatedExplanations = _uiState.value.aiExplanations.toMutableMap()
            updatedExplanations[diagnostic.id] = result.getOrElse { "Ошибка AI: ${it.message}" }

            _uiState.value = _uiState.value.copy(
                aiLoadingMap = updatedLoading,
                aiExplanations = updatedExplanations
            )
        }
    }

    fun hideBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = false)
    }

    fun showBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = true)
    }

    fun dismissExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = false)
    }
}
