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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    
    val builtInDemo = remember { BuiltInDeckProfiles.demoBridge52() }
    val builtInDemoMetadata = remember { builtInDemo.metadata.toProfileListItem() }
    
    // In-memory app state for profiles
    var profileState by remember {
        mutableStateOf(
            AdminProfileUiState(
                profiles = listOf(builtInDemoMetadata),
                activeProfileId = builtInDemoMetadata.id,
            )
        )
    }
    
    // Map profile IDs to their in-memory edited versions
    var editedProfiles by remember { mutableStateOf(mapOf(builtInDemoMetadata.id to builtInDemo)) }

    val activeProfileItem = profileState.activeProfile ?: builtInDemoMetadata
    val activeDeckProfile = editedProfiles[activeProfileItem.id] ?: builtInDemo

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
                    )
                    profileState = profileState.copy(
                        profiles = profileState.profiles + newProfile,
                        activeProfileId = newProfile.id,
                    )
                    // Initialize new profile as a copy of demo for now, but with new metadata
                    editedProfiles = editedProfiles + (newProfile.id to builtInDemo.withMetadata(newProfile.toDeckProfileMetadata()))
                },
                onDeleteActiveProfile = {
                    val toDelete = profileState.activeProfileId
                    val newProfiles = profileState.profiles.filter { it.id != toDelete }
                    profileState = profileState.copy(
                        profiles = newProfiles,
                        activeProfileId = newProfiles.firstOrNull()?.id ?: builtInDemoMetadata.id,
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
