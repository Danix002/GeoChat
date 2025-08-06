package it.unibo.collektive.ui.components

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

/**
 * A composable UI component that provides a message input field and handles
 * sending messages based on user input and location availability.
 *
 * This function interacts with the [MessagesViewModel] to enqueue messages
 * and with the [FusedLocationProviderClient] to ensure the user has a valid
 * GPS position before sending.
 *
 * The UI includes:
 * - A text field to type the message.
 * - A send button that triggers message enqueuing and sending logic.
 * - Visual feedback (progress indicator, warning popup) in case of missing location.
 *
 * Permission Requirement:
 * Requires either ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION to access location.
 *
 * @param messagesViewModel The ViewModel managing the list of messages.
 * @param communicationSettingViewModel The ViewModel providing message distance and duration.
 * @param nearbyDevicesViewModel The ViewModel tracking the nearby devices and user name.
 * @param fusedLocationProviderClient Location provider for obtaining the user's last known location.
 */
@androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun SenderMessageBox(
    messagesViewModel: MessagesViewModel,
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    fusedLocationProviderClient: FusedLocationProviderClient,
    userName: String
){
    var messageText by remember { mutableStateOf("") }
    var messagingFlag by remember { mutableStateOf(false)}
    var errorPositionPopup by remember { mutableStateOf(false) }
    var isWaitingForLocation by remember { mutableStateOf(false) }
    var flagTimeout by remember { mutableStateOf(false) }

    LaunchedEffect(messagingFlag) {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                messagesViewModel.addSentMessageToList(
                    nearbyDevicesViewModel = nearbyDevicesViewModel,
                    userName = userName,
                    message = messageText,
                    time = LocalDateTime.now()
                )
                messageText = ""
                messagingFlag = false
                if(messagesViewModel.pendingMessages.isEmpty()) {
                    messagesViewModel.setSendFlag(flag = false)
                }
            } else {
                errorPositionPopup = true
            }
        }

    }
    LaunchedEffect(isWaitingForLocation) {
        var timeout = 25
        while(isWaitingForLocation && timeout > 0) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                if(location != null) {
                    isWaitingForLocation = false
                    messagingFlag = false
                    return@addOnSuccessListener
                }
                timeout--
            }
            delay(0.5.seconds)
        }
        if(timeout == 0){
            flagTimeout = true
        }
    }

    if (isWaitingForLocation && !flagTimeout) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Purple40)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            if(errorPositionPopup && messagingFlag){
                GeneralWarning(content = "You are in reading mode because your location is unavailable")
            }else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Center
                ) {
                    OutlinedTextField(
                        modifier = Modifier.padding(end = 10.dp).heightIn(50.dp, 100.dp).weight(1f),
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Write message...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple40,
                            unfocusedBorderColor = Purple40
                        )
                    )
                    IconButton(onClick = {
                        val time = LocalDateTime.now()
                        val distance = communicationSettingViewModel.getDistance()
                        val spreadingTime = communicationSettingViewModel.getTime().toInt()
                        if (messageText.isNotBlank()) {
                            messagesViewModel.enqueueMessage(messageText, time, distance, spreadingTime)
                            if (!messagingFlag) {
                                messagesViewModel.setSendFlag(flag = true)
                                messagingFlag = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = Purple40
                        )
                    }
                }
            }
        }
    }
}
