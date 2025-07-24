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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.time.Duration.Companion.seconds

/**
 * Composable UI component that enables the user to compose and send a message to nearby devices.
 *
 * This function coordinates user interaction for initiating message broadcasting. Upon sending,
 * the function retrieves the current device location, constructs the message, and triggers
 * a distributed propagation process using the Collektive framework via the associated
 * `MessagesViewModel`.
 *
 * The message is transmitted if location permissions are granted and a valid location is available.
 * The sending session is time-bound, during which the UI informs the user to wait until the
 * message dissemination is complete. If location retrieval fails or times out, an appropriate
 * error popup is shown.
 *
 * ### Parameters
 * - `messagesViewModel`: The `MessagesViewModel` responsible for managing message propagation,
 *   sending state, and received data.
 * - `communicationSettingViewModel`: ViewModel handling the communication parameters such as
 *   maximum message range and message lifetime.
 * - `nearbyDevicesViewModel`: ViewModel containing information about the local device and
 *   discovered nearby devices.
 * - `fusedLocationProviderClient`: Location provider used to fetch the device’s most recent
 *   known geographic location.
 *
 * ### Behavior
 * - Shows an input text field for the user to compose a message.
 * - When the user presses the send button:
 *   - It verifies if a message is non-empty.
 *   - It requests the device’s last known location.
 *   - If a valid location is retrieved:
 *     - It adds the message to the local message list.
 *     - It invokes `listenIntentions` to start propagating the message.
 *     - The user interface switches to a waiting state based on the message timeout duration.
 *   - If no location is available, an error popup is shown.
 * - If location retrieval takes too long (timeout after 25 attempts with 0.5s delay), it triggers a timeout state.
 * - While waiting for the sending session to end, it prevents repeated message sending.
 *
 * ### Permissions
 * Requires either `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`.
 *
 * @see MessagesViewModel
 * @see CommunicationSettingViewModel
 * @see NearbyDevicesViewModel
 */
@androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun SenderMessageBox(
    messagesViewModel: MessagesViewModel,
    communicationSettingViewModel: CommunicationSettingViewModel,
    nearbyDevicesViewModel: NearbyDevicesViewModel,
    fusedLocationProviderClient: FusedLocationProviderClient
){
    var messageText by remember { mutableStateOf("") }
    var messageTextToSend by remember { mutableStateOf("") }
    var messagingFlag by remember { mutableStateOf(false)}
    var errorPositionPopup by remember { mutableStateOf(false) }
    var isWaitingForLocation by remember { mutableStateOf(false) }
    var flagTimeout by remember { mutableStateOf(false) }
    var remainingTime by remember { mutableStateOf(0.seconds) }
    LaunchedEffect(messagingFlag) {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location : Location? ->
            if(location != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    messagesViewModel.setLocation(location)
                    if (messagesViewModel.sendFlag.value) {
                        messagesViewModel.addSentMessageToList(
                            nearbyDevicesViewModel = nearbyDevicesViewModel,
                            userName = nearbyDevicesViewModel.userName.value,
                            message = messageTextToSend,
                            time = LocalDateTime.now()
                        )
                        messagesViewModel.setMessageToSend(messageTextToSend)
                        messagesViewModel.send(
                            distance = communicationSettingViewModel.getDistance(),
                            nearbyDevicesViewModel = nearbyDevicesViewModel,
                            userName = nearbyDevicesViewModel.userName.value
                        )
                        val validationTime = communicationSettingViewModel.getTime().toInt().seconds
                        if (validationTime < messagesViewModel.MINIMUM_TIME_TO_SEND) {
                            throw IllegalStateException("The time to send the message is too short")
                        }
                        remainingTime = validationTime
                        while (remainingTime > 0.seconds) {
                            delay(1.seconds)
                            remainingTime = remainingTime.minus(1.seconds)
                        }
                        messagesViewModel.setSendFlag(flag = false)
                        messagesViewModel.setOnlineStatus(flag = true)
                        messagingFlag = false
                        messageTextToSend = ""
                        messagesViewModel.setMessageToSend(messageTextToSend)
                    }else{
                        messagesViewModel.listen(
                            distance = POSITIVE_INFINITY,
                            nearbyDevicesViewModel = nearbyDevicesViewModel,
                            userName = nearbyDevicesViewModel.userName.value,
                            time = LocalDateTime.now()
                        )
                    }
                }
            }else{
                errorPositionPopup = true
            }
        }
    }
    LaunchedEffect(isWaitingForLocation) {
        var timeout = 25
        while(isWaitingForLocation && timeout > 0) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                Log.i("SenderMessageBox", "Position: $location")
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
                    if (!messagingFlag) {
                        IconButton(onClick = {
                            messageTextToSend = messageText
                            if (messageTextToSend.isNotBlank()) {
                                messagesViewModel.setOnlineStatus(flag = false)
                                messagesViewModel.setSendFlag(flag = true)
                                messagingFlag = true
                                messageText = ""
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = Purple40
                            )
                        }
                    } else {
                        Text(
                            text = "Wait $remainingTime",
                            modifier = Modifier.padding(end = 10.dp),
                            color = Purple40
                        )
                    }
                }
            }
        }
    }
}
