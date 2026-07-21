package org.itroboc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val viewModel: ItrobocMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminEditScanDebugLogStartup.clearOnce(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: ItrobocMainViewModel) {
    val activeProfileItem = viewModel.activeProfileItem
    val activeDeckProfile = viewModel.activeDeckProfile

    when (val screen = viewModel.currentScreen) {
        is Screen.MainMenu -> {
            MainMenuScreen(onNavigate = { viewModel.navigateTo(it) })
        }
        is Screen.TdActions -> {
            TdOverviewScreen(
                sessionState = viewModel.sessionState,
                activeProfile = activeProfileItem,
                autosaveEnabled = viewModel.autosaveEnabled,
                autosavePrefix = viewModel.autosavePrefix,
                autosavePath = viewModel.autosavePath,
                oldFilesCount = viewModel.oldAutosaveFiles.size,
                onSessionStateChange = { viewModel.updateSession(it) },
                onAutosaveEnabledChange = { viewModel.updateAutosaveEnabled(it) },
                onAutosavePrefixChange = { viewModel.updateAutosavePrefix(it) },
                onClearOldFiles = { viewModel.clearOldFiles(it) },
                onNavigateToBoard = { viewModel.navigateTo(Screen.EditBoard(it)) },
                onBack = { viewModel.navigateTo(Screen.MainMenu) }
            )
        }
        is Screen.EditBoard -> {
            val boardEditState = viewModel.sessionState.getOrInitBoard(screen.boardNumber)
            EditBoardScreen(
                boardEditState = boardEditState,
                deckProfile = activeDeckProfile,
                orientationMode = viewModel.barcodeOrientationMode,
                requiredConsensusFrames = viewModel.sessionState.requiredConsensusFrames,
                onOrientationModeChange = { viewModel.updateOrientation(it) },
                onBoardEditStateChange = { updatedState ->
                    viewModel.updateSession(viewModel.sessionState.updateBoard(updatedState))
                },
                onBack = { viewModel.navigateTo(Screen.TdActions) }
            )
        }
        is Screen.AdminActions -> {
            AdminActionsScreen(
                uiState = viewModel.profileState,
                onSelectProfile = { viewModel.selectProfile(it) },
                onAddProfile = { viewModel.addProfile(it) },
                onDeleteActiveProfile = { viewModel.deleteActiveProfile() },
                onExportActiveProfile = { activeDeckProfile },
                onImportProfile = { viewModel.importProfile(it) },
                onEdit = { viewModel.navigateTo(Screen.AdminEdit) },
                onBack = { viewModel.navigateTo(Screen.MainMenu) },
            )
        }
        is Screen.AdminEdit -> {
            val editor = remember(activeProfileItem.id) { activeDeckProfile.toEditor() }
            AdminEditScreen(
                editor = editor,
                onSave = { updatedProfile ->
                    viewModel.saveEditedProfile(updatedProfile)
                },
                onBack = { viewModel.navigateTo(Screen.AdminActions) }
            )
        }
        is Screen.MockActions -> {
            MockTdScreen(
                deckProfile = activeDeckProfile,
                onBack = { viewModel.navigateTo(Screen.MainMenu) },
            )
        }
    }
}
