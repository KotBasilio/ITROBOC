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

    when (val screen = currentScreen) {
        is Screen.MainMenu -> {
            MainMenuScreen(onNavigate = { currentScreen = it })
        }
        is Screen.TdActions -> {
            StubActionScreen(
                title = "TD actions",
                message = "TD workflow will be implemented here.",
                onBack = { currentScreen = Screen.MainMenu }
            )
        }
        is Screen.AdminActions -> {
            StubActionScreen(
                title = "Admin actions",
                message = "Deck profile/admin workflow will be implemented here.",
                onBack = { currentScreen = Screen.MainMenu }
            )
        }
        is Screen.MockActions -> {
            MockTdScreen(onBack = { currentScreen = Screen.MainMenu })
        }
    }
}
