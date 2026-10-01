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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prismde.core.datastore.SettingsRepository
import com.prismde.core.model.DefaultNdkCatalog
import com.prismde.core.model.Project
import com.prismde.core.theme.PrismTheme
import com.prismde.feature_build.BuildViewModel
import com.prismde.feature_editor.EditorScreen
import com.prismde.feature_editor.EditorViewModel
import com.prismde.feature_files.FileTreeScreen
import com.prismde.feature_ndk.NdkScreen
import com.prismde.feature_ndk.NdkViewModel
import com.prismde.feature_settings.SettingsScreen
import com.prismde.feature_setup.SetupWizardScreen
import com.prismde.feature_update.UpdateViewModel
import com.prismde.feature_update.components.UpdateBottomSheet
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

        // Load sample project by default
        val sampleProjectDir = File(filesDir, "projects/sample_jni_project")
        val sampleProject = Project(
            id = "sample_jni",
            name = "sample_jni",
            rootPath = sampleProjectDir.absolutePath
        )
        editorViewModel.setProject(sampleProject)

        val firstCppFile = File(sampleProjectDir, "jni/native-lib.cpp")
        if (firstCppFile.exists()) {
            editorViewModel.openFile(firstCppFile)
        }

        setContent {
            val isSetupCompleted by settingsRepo.isSetupCompletedFlow.collectAsState(initial = false)
            val darkMode by settingsRepo.darkModeFlow.collectAsState(initial = "system")
            val dynamicColor by settingsRepo.dynamicColorFlow.collectAsState(initial = true)
            val geminiKey by settingsRepo.geminiApiKeyFlow.collectAsState(initial = "")

            val ndkState by ndkViewModel.uiState.collectAsState()
            val updateState by updateViewModel.uiState.collectAsState()
            val editorState by editorViewModel.uiState.collectAsState()

            val activeNdk = ndkState.versions.find { it.versionTag == ndkState.activeTag }
                ?: DefaultNdkCatalog.AVAILABLE_VERSIONS.first()

            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            PrismTheme(darkTheme = isDark, dynamicColor = dynamicColor) {
                if (!isSetupCompleted) {
                    SetupWizardScreen(
                        settingsRepository = settingsRepo,
                        ndkViewModel = ndkViewModel,
                        onCompleteSetup = {
                            // Setup completed - transitions into main IDE
                        }
                    )
                } else {
                    var currentTab by remember { mutableIntStateOf(0) }

                    // Auto-check for updates on launch
                    LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdates(manual = false)
                    }

                    Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
                                geminiApiKey = geminiKey
                            )
                            1 -> FileTreeScreen(
                                currentProject = editorState.currentProject,
                                activeFile = editorState.activeFile,
                                onOpenFile = { file ->
                                    editorViewModel.openFile(file)
                                    currentTab = 0 // Switch to editor immediately
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
                                }
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
}
