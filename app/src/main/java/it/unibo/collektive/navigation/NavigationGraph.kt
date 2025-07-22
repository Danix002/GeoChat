package it.unibo.collektive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.android.gms.location.FusedLocationProviderClient
import it.unibo.collektive.ui.pages.ChatPage
import it.unibo.collektive.ui.pages.HomePage
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel

@androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun SetupNavigationGraph(
    navigationController: NavHostController,
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    messagesViewModel: MessagesViewModel,
    startDestination: String,
    modifier: Modifier,
    fusedLocationProviderClient: FusedLocationProviderClient,
) {
    NavHost(navigationController, startDestination) {
        composable(
            route = Pages.Chat.route
        ) {
            ChatPage(
                communicationSettingViewModel,
                nearbyDevicesViewModel,
                messagesViewModel,
                navigationController,
                modifier,
                fusedLocationProviderClient
            )
        }
        composable(
            route = Pages.Home.route
        ) {
            HomePage(
                nearbyDevicesViewModel,
                messagesViewModel,
                navigationController,
                modifier
            )
        }
    }
}
