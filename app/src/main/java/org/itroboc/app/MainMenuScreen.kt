package org.itroboc.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MainMenuScreen(
    onNavigate: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_small),
            contentDescription = "ITROBOC Logo",
            modifier = Modifier.weight(4f).fillMaxSize()
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Button(
                onClick = { onNavigate(Screen.AdminActions) },
                modifier = Modifier.weight(1f).aspectRatio(4f)
            ) {
                Text("Admin", style = ItrobocTextStyles.MainMenuAction)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { onNavigate(Screen.TdActions) },
                modifier = Modifier.weight(1f).aspectRatio(4f)
            ) {
                Text("TD eye", style = ItrobocTextStyles.MainMenuAction)
            }
        }
        
    }
}

@Preview(showBackground = true)
@Composable
fun MainMenuPreview() {
    MaterialTheme {
        MainMenuScreen(onNavigate = {})
    }
}
