@file:Suppress("FunctionNaming")

package it.unibo.collektive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.unibo.collektive.ui.theme.CollektiveExampleAndroidTheme
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

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
                    CollektiveNearbyDevices(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun CollektiveNearbyDevices(
    modifier: Modifier = Modifier,
    viewModel: NearbyDevicesViewModel = viewModel(factory = NearbyDevicesViewModel.Factory),
) {
    Box(
        modifier = Modifier.fillMaxSize().then(modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Collektive Nearby Devices")
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Button(onClick = {}) {
                    Text("Start Scanning")
                }
                Button(onClick = {}) {
                    Text("Stop Scanning")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    CollektiveExampleAndroidTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            CollektiveNearbyDevices(modifier = Modifier.padding(innerPadding))
        }
    }
}
