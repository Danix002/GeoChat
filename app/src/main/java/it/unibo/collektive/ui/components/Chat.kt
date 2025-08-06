package it.unibo.collektive.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel

@Composable
fun Chat(
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    messagesViewModel: MessagesViewModel,
    modifier: Modifier,
    communicationSettingViewModel: CommunicationSettingViewModel
){
    val devicesInChat by messagesViewModel.devicesInChat.collectAsState()
    val messageList by messagesViewModel.messages.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .border(5.dp, Purple40, shape = RoundedCornerShape(16.dp))
            .padding(7.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = " [$devicesInChat] Online")
            }
        }
        items(messageList) { message ->
            Message(message, message.sender == nearbyDevicesViewModel.deviceId, communicationSettingViewModel)
        }
    }
}

