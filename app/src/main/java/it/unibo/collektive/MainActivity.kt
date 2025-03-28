@file:Suppress("FunctionNaming")

package it.unibo.collektive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import it.unibo.collektive.navigation.NavigationInitializer
import it.unibo.collektive.navigation.Pages
import it.unibo.collektive.ui.theme.CollektiveExampleAndroidTheme
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel

/**
 * Main entry point for the Android app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CollektiveExampleAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Initialization(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun Initialization(modifier: Modifier,
                           nearbyDevicesViewModel: NearbyDevicesViewModel = viewModel(),
                           communicationSettingViewModel: CommunicationSettingViewModel = viewModel(),
                           messagesViewModel: MessagesViewModel = viewModel()) {
    NavigationInitializer(
        communicationSettingViewModel,
        nearbyDevicesViewModel,
        messagesViewModel,
        Pages.Home.route,
        modifier
    )
}

@Preview(showBackground = true)
@Suppress("UnusedPrivateMember")
@Composable
private fun DefaultPreview() {
    CollektiveExampleAndroidTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Initialization(modifier = Modifier.padding(innerPadding))
        }
    }
}
