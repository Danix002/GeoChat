package it.unibo.collektive.ui.components

import android.location.Location
import android.util.Log
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

@androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun SenderMessageBox(
    messagesViewModel: MessagesViewModel,
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    fusedLocationProviderClient: FusedLocationProviderClient
){
    var messageText by remember { mutableStateOf("") }
    var messagingFlag by remember { mutableStateOf(false)}
    var errorPositionPopup by remember { mutableStateOf(false) }
    var isWaitingForLocation by remember { mutableStateOf(false) }
    var flagTimeout by remember { mutableStateOf(false) }
    val pendingMessages by remember { derivedStateOf { messagesViewModel.pendingMessages } }

    LaunchedEffect(pendingMessages.size) {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    messagesViewModel.setLocation(location)
                    coroutineScope {
                        messagesViewModel.pendingMessages.forEach { message ->
                            launch {
                                messagesViewModel.listenAndSend(
                                    nearbyDevicesViewModel,
                                    nearbyDevicesViewModel.userName.value,
                                    message
                                )
                                val validationTime = message.spreadingTime.seconds
                                if (validationTime < messagesViewModel.MINIMUM_TIME_TO_SEND) {
                                    throw IllegalStateException("The time to send the message is too short")
                                }
                                delay(validationTime)
                            }
                            messagesViewModel.dequeueMessage()
                        }
                    }
                    messagingFlag = false
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
                            messagesViewModel.addSentMessageToList(
                                nearbyDevicesViewModel = nearbyDevicesViewModel,
                                userName = nearbyDevicesViewModel.userName.value,
                                message = messageText,
                                time = time
                            )
                            messageText = ""
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
