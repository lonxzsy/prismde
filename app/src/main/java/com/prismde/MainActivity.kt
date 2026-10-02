package com.prismde

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.prismde.core.datastore.SettingsRepository
import com.prismde.core.model.DefaultNdkCatalog
import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import com.prismde.core.theme.PrismTheme
import com.prismde.feature_build.BuildViewModel
import com.prismde.feature_build.engine.AiConfig
import com.prismde.feature_build.engine.ProjectDetector
import com.prismde.feature_editor.EditorScreen
import com.prismde.feature_editor.EditorViewModel
import com.prismde.feature_files.FileTreeScreen
import com.prismde.feature_files.project.ProjectManager
import com.prismde.feature_ndk.NdkScreen
import com.prismde.feature_ndk.NdkViewModel
import com.prismde.feature_settings.SettingsScreen
import com.prismde.feature_setup.SetupWizardScreen
import com.prismde.feature_update.UpdateViewModel
import com.prismde.feature_update.components.UpdateBottomSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()
    private val buildViewModel: BuildViewModel by viewModels()
    private val ndkViewModel: NdkViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(applicationContext)

        // If NDK is already installed, mark setup completed to prevent any flash
        if (!settingsRepo.isSetupCompletedSync) {
            val ndkDir = File(applicationContext.filesDir, "ndk/r26c")
            if (ndkDir.exists() && ndkDir.isDirectory) {
                settingsRepo.markSetupCompletedSync()
            }
        }

        // Restore last project or pick / create starter project
        lifecycleScope.launch {
            val lastPath = settingsRepo.lastProjectPathFlow.first()
            val project = if (lastPath != null && File(lastPath).exists()) {
                val dir = File(lastPath)
                Project(name = dir.name, rootPath = dir.absolutePath, detectedType = ProjectDetector.detect(dir))
            } else {
                val existing = ProjectManager.listProjects(applicationContext)
                if (existing.isNotEmpty()) {
                    existing.first()
                } else {
                    ProjectManager.createProject(applicationContext, "sample_jni_project", ProjectType.PURE_JNI_SO)
                }
            }
            editorViewModel.setProject(project)
            settingsRepo.setLastProjectPath(project.rootPath)

            val firstFile = project.rootDir.walkTopDown().firstOrNull { it.isFile && !it.name.startsWith(".") }
            if (firstFile != null) {
                editorViewModel.openFile(firstFile)
            }
        }

        setContent {
            val isSetupCompleted by settingsRepo.isSetupCompletedFlow.collectAsState(initial = settingsRepo.isSetupCompletedSync)
            val darkMode by settingsRepo.darkModeFlow.collectAsState(initial = "system")
            val dynamicColor by settingsRepo.dynamicColorFlow.collectAsState(initial = true)
            val aiConfig by settingsRepo.aiConfigFlow.collectAsState(initial = AiConfig())

            val ndkState by ndkViewModel.uiState.collectAsState()
            val updateState by updateViewModel.uiState.collectAsState()
            val editorState by editorViewModel.uiState.collectAsState()

            val activeNdk = ndkState.versions.find { it.versionTag == ndkState.activeTag }
                ?: ndkState.versions.find { it.versionTag == DefaultNdkCatalog.DEFAULT_ACTIVE_TAG }
                ?: DefaultNdkCatalog.AVAILABLE_VERSIONS.first()

            val darkTheme = when (darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            var currentTab by remember { mutableIntStateOf(0) }

            val handleSelectProject: (Project) -> Unit = { newProj ->
                editorViewModel.setProject(newProj)
                lifecycleScope.launch {
                    settingsRepo.setLastProjectPath(newProj.rootPath)
                }
                val firstFile = newProj.rootDir.walkTopDown().firstOrNull { it.isFile && !it.name.startsWith(".") }
                if (firstFile != null) {
                    editorViewModel.openFile(firstFile)
                }
            }

            PrismTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor
            ) {
                if (!isSetupCompleted) {
                    SetupWizardScreen(
                        settingsRepository = settingsRepo,
                        ndkViewModel = ndkViewModel,
                        onCompleteSetup = {
                            currentTab = 0
                        }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    icon = { Icon(Icons.Rounded.Code, contentDescription = "Редактор") },
                                    label = { Text("Редактор") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(Icons.Rounded.Folder, contentDescription = "Файлы") },
                                    label = { Text("Файлы") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    icon = { Icon(Icons.Rounded.Memory, contentDescription = "NDK") },
                                    label = { Text("NDK") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Настройки") },
                                    label = { Text("Настройки") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            label = "tabTransition"
                        ) { tabIndex ->
                            when (tabIndex) {
                                0 -> EditorScreen(
                                    editorViewModel = editorViewModel,
                                    buildViewModel = buildViewModel,
                                    activeNdk = activeNdk,
                                    aiConfig = aiConfig,
                                    onSelectProject = handleSelectProject
                                )
                                1 -> FileTreeScreen(
                                    currentProject = editorState.currentProject,
                                    activeFile = editorState.activeFile,
                                    onOpenFile = { file ->
                                        editorViewModel.openFile(file)
                                        currentTab = 0
                                    },
                                    onCreateFile = { name, content, isFolder ->
                                        val project = editorState.currentProject ?: return@FileTreeScreen
                                        val target = File(project.rootDir, name)
                                        if (isFolder) {
                                            target.mkdirs()
                                        } else {
                                            target.parentFile?.mkdirs()
                                            target.writeText(content)
                                            editorViewModel.openFile(target)
                                            currentTab = 0
                                        }
                                    },
                                    onSelectProject = handleSelectProject
                                )
                                2 -> NdkScreen(
                                    viewModel = ndkViewModel
                                )
                                3 -> SettingsScreen(
                                    settingsRepository = settingsRepo,
                                    onNavigateNdkManager = { currentTab = 2 },
                                    onCheckUpdates = { updateViewModel.checkForUpdates(manual = true) }
                                )
                            }
                        }
                    }

                    // GitHub Releases App Update Dialog
                    UpdateBottomSheet(
                        releaseInfo = updateState.releaseInfo,
                        isDownloading = updateState.isDownloading,
                        downloadPercent = updateState.downloadPercent,
                        onDismiss = { updateViewModel.dismissUpdate() },
                        onConfirmUpdate = { updateViewModel.downloadAndInstall() }
                    )
                }
            }
        }
    }
}
