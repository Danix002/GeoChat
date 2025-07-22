package it.unibo.collektive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.FusedLocationProviderClient
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel

@androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun NavigationInitializer(
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    messagesViewModel: MessagesViewModel,
    startDestination: String,
    modifier: Modifier,
    fusedLocationProviderClient: FusedLocationProviderClient
) {
    val navigationController = rememberNavController()
    SetupNavigationGraph(
        navigationController,
        communicationSettingViewModel,
        nearbyDevicesViewModel,
        messagesViewModel,
        startDestination,
        modifier,
        fusedLocationProviderClient
    )
}
