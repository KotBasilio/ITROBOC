package org.itroboc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.itroboc.core.BuiltInDeckProfiles
import org.itroboc.core.DeckProfile
import org.itroboc.core.DeckProfileSignatureModels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminEditScanDebugLogStartup.clearOnce(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    var barcodeOrientationMode by remember { mutableStateOf(BarcodeOrientationMode.BFM) }
    
    val builtInDefault = remember { BuiltInDeckProfiles.defaultProfile() }
    val builtInDefaultMetadata = remember { builtInDefault.metadata.toProfileListItem() }
    val builtInDemo = remember { BuiltInDeckProfiles.demoBridge52() }
    val builtInDemoMetadata = remember { builtInDemo.metadata.toProfileListItem() }
    
    // In-memory app state for profiles
    var profileState by remember {
        mutableStateOf(
            AdminProfileUiState(
                profiles = listOf(builtInDefaultMetadata, builtInDemoMetadata),
                activeProfileId = builtInDefaultMetadata.id,
            )
        )
    }
    
    // Map profile IDs to their in-memory edited versions
    var editedProfiles by remember {
        mutableStateOf(
            mapOf(
                builtInDefaultMetadata.id to builtInDefault,
                builtInDemoMetadata.id to builtInDemo,
            )
        )
    }

    val activeProfileItem = profileState.activeProfile ?: builtInDefaultMetadata
    val activeDeckProfile = editedProfiles[activeProfileItem.id] ?: builtInDefault

    when (val screen = currentScreen) {
        is Screen.MainMenu -> {
            MainMenuScreen(onNavigate = { currentScreen = it })
        }
        is Screen.TdActions -> {
            TdOverviewScreen(
                activeProfile = activeProfileItem,
                onNavigateToBoard = { boardNumber ->
                    currentScreen = Screen.BoardScan(boardNumber)
                },
                onBack = { currentScreen = Screen.MainMenu }
            )
        }
        is Screen.BoardScan -> {
            BoardScanScreen(
                boardNumber = screen.boardNumber,
                orientationMode = barcodeOrientationMode,
                onOrientationModeChange = { barcodeOrientationMode = it },
                onBack = { currentScreen = Screen.TdActions }
            )
        }
        is Screen.AdminActions -> {
            AdminActionsScreen(
                uiState = profileState,
                onSelectProfile = { profileId ->
                    profileState = profileState.copy(activeProfileId = profileId)
                },
                onAddProfile = { profileName ->
                    val newProfile = ProfileListItem(
                        id = profileState.nextCustomProfileId(),
                        displayName = profileName,
                        isBuiltIn = false,
                        isDemo = false,
                        signatureModel = DeckProfileSignatureModels.GRID13_V1,
                    )
                    profileState = profileState.copy(
                        profiles = profileState.profiles + newProfile,
                        activeProfileId = newProfile.id,
                    )
                    editedProfiles = editedProfiles + (newProfile.id to newProfile.toEmptyDeckProfile())
                },
                onDeleteActiveProfile = {
                    val toDelete = profileState.activeProfileId
                    val newProfiles = profileState.profiles.filter { it.id != toDelete }
                    profileState = profileState.copy(
                        profiles = newProfiles,
                        activeProfileId = newProfiles.firstOrNull()?.id ?: builtInDefaultMetadata.id,
                    )
                    editedProfiles = editedProfiles - toDelete
                },
                onEdit = { currentScreen = Screen.AdminEdit },
                onBack = { currentScreen = Screen.MainMenu },
            )
        }
        is Screen.AdminEdit -> {
            val editor = remember(activeProfileItem.id) { activeDeckProfile.toEditor() }
            AdminEditScreen(
                editor = editor,
                onSave = { updatedProfile ->
                    editedProfiles = editedProfiles + (activeProfileItem.id to updatedProfile)
                    currentScreen = Screen.AdminActions
                },
                onBack = { currentScreen = Screen.AdminActions }
            )
        }
        is Screen.MockActions -> {
            MockTdScreen(
                deckProfile = activeDeckProfile,
                onBack = { currentScreen = Screen.MainMenu },
            )
        }
    }
}
