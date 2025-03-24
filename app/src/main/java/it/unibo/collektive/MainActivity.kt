@file:Suppress("FunctionNaming")

package it.unibo.collektive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.unibo.collektive.ui.theme.CollektiveExampleAndroidTheme
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlin.uuid.ExperimentalUuidApi

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

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun CollektiveNearbyDevices(modifier: Modifier = Modifier, viewModel: NearbyDevicesViewModel = viewModel()) {
    val dataFlow by viewModel.dataFlow.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.startCollektiveProgram()
    }

    Column(modifier = modifier.then(Modifier.padding(20.dp)), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Collektive MQTT", style = MaterialTheme.typography.displaySmall)
        dataFlow.forEach { uuid ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device ID", style = MaterialTheme.typography.bodyMedium)
                    Text(uuid.toString(), style = MaterialTheme.typography.bodySmall)
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
