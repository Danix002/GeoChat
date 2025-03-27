package it.unibo.collektive.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import androidx.navigation.NavHostController
import it.unibo.collektive.navigation.Pages
import it.unibo.collektive.ui.theme.Purple40

@Composable
fun HomePage(nearbyDevicesViewModel: NearbyDevicesViewModel,
             navigationController: NavHostController,
             modifier: Modifier) {
    val dataFlow by nearbyDevicesViewModel.dataFlow.collectAsState()
    val connectionFlow by nearbyDevicesViewModel.connectionFlow.collectAsState()
    val connectionColor = when (connectionFlow) {
        NearbyDevicesViewModel.ConnectionState.CONNECTED -> Color.Green
        NearbyDevicesViewModel.ConnectionState.DISCONNECTED -> Color.Red
    }

    LaunchedEffect(Unit) {
        nearbyDevicesViewModel.startCollektiveProgram()
    }

    Column(modifier = modifier.then(Modifier.padding(20.dp)), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Collektive MQTT", modifier = Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
            Box(modifier = Modifier.size(24.dp).background(color = connectionColor, shape = CircleShape))
        }
        Text("ID: ${nearbyDevicesViewModel.deviceId}", style = MaterialTheme.typography.bodyLarge)
        if (dataFlow.isEmpty()) {
            Text("No nearby devices found", style = MaterialTheme.typography.bodyMedium)
        }
        dataFlow.forEach { uuid ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device ID", style = MaterialTheme.typography.bodyMedium)
                    Text(uuid.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(onClick = { navigationController.navigate(Pages.Chat.route) },
                colors = ButtonDefaults.buttonColors(containerColor = Purple40, contentColor = Color.White)
            ) {
                Text(text = "Start chatting")
            }
        }
    }
}
