package it.unibo.collektive.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import it.unibo.collektive.ui.pages.ChatPage
import it.unibo.collektive.ui.pages.HomePage
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel

@Composable
fun SetupNavigationGraph(
    navigationController: NavHostController,
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    messagesViewModel: MessagesViewModel,
    startDestination: String,
    modifier: Modifier
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
                modifier
            )
        }
        composable(
            route = Pages.Home.route
        ) {
            HomePage(nearbyDevicesViewModel, navigationController, modifier)
        }
    }
}
