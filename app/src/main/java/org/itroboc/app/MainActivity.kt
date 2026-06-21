package org.itroboc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.itroboc.core.BuiltInDeckProfiles

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
    val builtInDemoProfile = remember { BuiltInDeckProfiles.demoBridge52().metadata.toProfileListItem() }
    var profileState by remember {
        mutableStateOf(
            AdminProfileUiState(
                profiles = listOf(builtInDemoProfile),
                activeProfileId = builtInDemoProfile.id,
            ),
        )
    }
    val activeProfile = profileState.activeProfile ?: builtInDemoProfile
    val activeDeckProfile = activeProfile.toMockDeckProfile()

    when (val screen = currentScreen) {
        is Screen.MainMenu -> {
            MainMenuScreen(onNavigate = { currentScreen = it })
        }
        is Screen.TdActions -> {
            TdOverviewScreen(
                activeProfile = activeProfile,
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
                },
                onDeleteActiveProfile = {
                    val newProfiles = profileState.profiles.filter { it.id != profileState.activeProfileId }
                    profileState = profileState.copy(
                        profiles = newProfiles,
                        activeProfileId = newProfiles.firstOrNull()?.id ?: builtInDemoProfile.id,
                    )
                },
                onBack = { currentScreen = Screen.MainMenu },
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
