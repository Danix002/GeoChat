package it.unibo.collektive.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.ui.components.CommunicationSettingSelector
import it.unibo.collektive.ui.components.EchoLogo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Delete
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import it.unibo.collektive.navigation.Pages
import it.unibo.collektive.ui.components.Chat
import it.unibo.collektive.ui.components.SenderMessageBox
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import java.time.LocalDateTime

@androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun ChatPage(
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    messagesViewModel: MessagesViewModel,
    navigationController: NavHostController,
    modifier: Modifier,
    fusedLocationProviderClient: FusedLocationProviderClient
) {
    val devicesInChat by messagesViewModel.devicesInChat.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var isReadyToShowChat by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        messagesViewModel.listenAndSend(
            nearbyDevicesViewModel = nearbyDevicesViewModel,
            userName = nearbyDevicesViewModel.userName.value,
            time = LocalDateTime.now()
        )
        delay(1.seconds)
        isReadyToShowChat = true
    }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            messagesViewModel.clearMessages()
            isLoading = false
        }
    }

    Box(modifier = modifier.then(Modifier.padding(20.dp))) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
                    .border(5.dp, Purple40, shape = RoundedCornerShape(16.dp))
                    .padding(7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EchoLogo()
                    CommunicationSettingSelector(communicationSettingViewModel)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(50.dp)
            ) {
                IconButton(onClick = {
                    navigationController.navigate(Pages.Home.route)
                    messagesViewModel.setOnlineStatus(flag = false)
                    nearbyDevicesViewModel.setOnlineStatus(flag = true)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = Purple40
                    )
                }
                Text(text = "Back to Home")
                IconButton(
                    onClick = { isLoading = true }
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = "Clear Chat",
                        tint = Purple40
                    )
                }
                Text(text = "Clear Chat")
                Box(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(text = " [$devicesInChat] Online")
                }
            }
            if (isLoading || !isReadyToShowChat) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Purple40)
                }
            } else {
                Chat(
                    nearbyDevicesViewModel,
                    messagesViewModel,
                    Modifier.weight(1f),
                    communicationSettingViewModel
                )
                SenderMessageBox(
                    messagesViewModel,
                    communicationSettingViewModel,
                    nearbyDevicesViewModel,
                    fusedLocationProviderClient
                )
            }
        }
    }
}
