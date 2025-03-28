package it.unibo.collektive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel

@Composable
fun NavigationInitializer(communicationSettingViewModel: CommunicationSettingViewModel,
                          nearbyDevicesViewModel: NearbyDevicesViewModel,
                          messagesViewModel: MessagesViewModel,
                          startDestination: String,
                          modifier: Modifier) {
    val navigationController = rememberNavController()
    SetupNavigationGraph(
        navigationController,
        communicationSettingViewModel,
        nearbyDevicesViewModel,
        messagesViewModel,
        startDestination,
        modifier)
}
