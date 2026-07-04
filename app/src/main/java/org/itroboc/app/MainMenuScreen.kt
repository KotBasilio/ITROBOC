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
            modifier = Modifier.size(160.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "ITROBOC Main Menu",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { onNavigate(Screen.TdActions) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("TD actions")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onNavigate(Screen.AdminActions) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("Admin actions")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onNavigate(Screen.MockActions) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("Mock actions")
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
