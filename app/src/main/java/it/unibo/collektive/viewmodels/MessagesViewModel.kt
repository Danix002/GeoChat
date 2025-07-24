package it.unibo.collektive.viewmodels

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.model.Message
import it.unibo.collektive.model.Params
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.uuid.Uuid
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel responsible for handling message sending, receiving, and processing
 * in a proximity-based distributed chat system using Collektive aggregation.
 *
 * It manages:
 * - Sending messages with spatial gradient spreading
 * - Listening to messages and intentions from nearby devices
 * - Keeping track of known devices and senders
 * - Maintaining a list of received messages
 *
 * @param dispatcher Coroutine dispatcher to perform asynchronous tasks (default: Dispatchers.IO)
 */
class MessagesViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val IP_HOST = "192.168.1.6"

    // Flow holding the current data pair of (device ID, message triple)
    private val _dataFlow = MutableStateFlow<Pair<Uuid?, Triple<Float, String, String>?>>(null to null)

    // Map of senders currently detected (deviceId -> (distance, username, message))
    private val _senders = MutableStateFlow<MutableMap<Uuid, Triple<Float, String, String>>>(mutableMapOf())

    // Map of devices discovered nearby (deviceId -> (distance, username, message))
    private val _devices = MutableStateFlow<MutableMap<Uuid, Triple<Float, String, String>>>(mutableMapOf())

    // Flow indicating if the device is online in the chat network
    private val _online = MutableStateFlow(false)

    // Flow indicating if the user is currently sending messages
    private val _sendFlag = MutableStateFlow(false)

    // Flow holding the list of messages received and sent locally
    private val _messages = MutableStateFlow<MutableList<Message>>(mutableListOf())

    // Map holding messages received from each sender
    private val _received = MutableStateFlow<MutableMap<Uuid, List<Params>>>(mutableMapOf())

    // Number of devices in chat
    private val _counterDevices = MutableStateFlow(0)

    // Message to send
    private val _messageToSend = MutableStateFlow("")

    private val _position = MutableStateFlow<Location?>(null)
    private val _coordinates = MutableStateFlow<Point3D?>(null)

    /**
     * The number of devices in the chat.
     */
    val devicesInChat: StateFlow<Int> = _counterDevices.asStateFlow()

    /**
     * Minimum time required for a message to propagate through the network.
     */
    val MINIMUM_TIME_TO_SEND = 5.seconds

    /**
     * Delay before deleting messages from local cache.
     */
    val TIME_FOR_DELETE_MESSAGES = 2.seconds

    /**
     * Public immutable flows exposed to UI.
     */
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    val sendFlag: StateFlow<Boolean> get() = _sendFlag

    /**
     * Integrates newly received messages into the current local message list,
     * ensuring that duplicates and previously deleted messages are not re-added.
     *
     * This function processes a map where each key corresponds to a unique sender
     * identifier (UUID), and each value is a list of message parameters (`Params`)
     * associated with that sender. Each `Params` object is transformed into a
     * `Message` instance with properly formatted timestamp information.
     *
     * The function performs the following operations:
     * 1. Flattens the collection of message parameter lists from all senders into a single list.
     * 2. Converts each `Params` instance into a `Message` object, formatting the minute component
     *    of the timestamp to always include two digits (e.g., "09" instead of "9").
     * 3. Filters out any message that already exists in the current message list by matching
     *    the sender, receiver, and text content, thereby preventing duplicate entries.
     * 4. Excludes messages that are present in the internal deleted messages list to avoid
     *    resurrecting messages that the user has explicitly removed.
     * 5. Appends the filtered new messages to the existing message list.
     *
     * Finally, the updated message list is published to the `_messages` state flow,
     * ensuring that observers are notified of the change.
     *
     * @param received A map from sender UUIDs to their respective lists of message parameters.
     */
    private fun addNewMessagesToList(received: Map<Uuid, List<Params>>) {
        val tmp = this._messages.value.toMutableList()
        tmp += received.values.flatten().map { newMessage ->
            var minute = newMessage.timestamp.minute.toString()
            if(newMessage.timestamp.minute < 10){
                minute = "0$minute"
            }
            Message(
                text = newMessage.message,
                userName = newMessage.to.second,
                sender = newMessage.to.first,
                receiver = newMessage.from.first,
                time = "${newMessage.timestamp.hour}:$minute",
                distance = newMessage.distance.toFloat(),
                timestamp = newMessage.timestamp
            )
        }.filterNot { msg ->
            tmp.any { existing ->
                existing.sender == msg.sender &&
                    existing.receiver == msg.receiver &&
                    existing.text == msg.text
            }
        }
        this._messages.value = tmp
    }

    /**
     * Adds a single new message to the list if not already present or deleted.
     *
     * @param msg The message to add
     */
    private fun addNewMessage(msg: Message) {
        val currentMessages = _messages.value.toMutableList()
        if (msg.text.isNotEmpty() && currentMessages.none { it.id == msg.id }) {
            currentMessages.add(msg)
            _messages.value = currentMessages
        }
    }

    /**
     * Clears all current messages by marking them as deleted and emptying the list.
     * This is useful for resetting the UI or clearing chat history.
     */
    suspend fun clearMessages() {
        _messages.value.clear()
        delay(TIME_FOR_DELETE_MESSAGES)
    }

    /**
     * Initializes a coroutine that runs indefinitely in the ViewModel scope,
     * periodically clearing the list of messages every five minutes.
     *
     * This mechanism helps control memory usage by regularly purging
     * stored messages from the internal cache, preventing unbounded growth.
     * The coroutine is launched on the provided dispatcher and will be
     * automatically cancelled when the ViewModel is cleared.
     */
    init {
        viewModelScope.launch(dispatcher) {
            while (true) {
                _messages.value.clear()
                delay(5.minutes)
            }
        }
    }

    /**
     * Sets the online status of the device in the chat network.
     * When online, the device participates in message propagation.
     *
     * @param flag true to set online, false to go offline
     */
    fun setOnlineStatus(flag: Boolean) {
        _online.value = flag
    }

    /**
     * Sets the messaging flag indicating whether the user is sending messages.
     *
     * @param flag true if sending messages, false otherwise
     */
    fun setSendFlag(flag: Boolean) {
        _sendFlag.value = flag
    }

    /**
     * Updates the message text that is intended to be sent.
     *
     * @param text The new message content to set for sending.
     */
    fun setMessageToSend(text: String) {
        _messageToSend.value = text
    }

    /**
     * Updates the current geographic location of the device.
     *
     * This function sets the internal state variable [_position] to the specified [location],
     * which represents the current geographical coordinates (latitude, longitude, altitude).
     *
     * @param location the new [Location] object to be assigned, or `null` if no location is available.
     */
    fun setLocation(location: Location?) {
        _position.value = location
        _coordinates.value = Point3D(Triple(_position.value!!.latitude, _position.value!!.longitude, _position.value!!.altitude))
    }

    /**
     * Adds a message sent by the local device to the message list.
     * This allows the UI to immediately reflect sent messages.
     *
     * @param nearbyDevicesViewModel Reference to NearbyDevicesViewModel for device ID and username
     * @param userName Username of the sender
     * @param message The message text
     * @param time The LocalDateTime timestamp of the message
     */
    fun addSentMessageToList(
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        userName: String,
        message: String,
        time: LocalDateTime
    ) {
        if (_sendFlag.value) {
            val minuteFormatted = time.minute.toString().padStart(2, '0')
            addNewMessage(
                Message(
                    text = message,
                    userName = userName,
                    sender = nearbyDevicesViewModel.deviceId,
                    receiver = nearbyDevicesViewModel.deviceId,
                    time = "${time.hour}:$minuteFormatted",
                    distance = 0f,
                    timestamp = time
                )
            )
        }
    }

    /**
     * Starts a coroutine that continuously listens for receive messages
     * and processes incoming message broadcasts in the network.
     *
     * This function combines message broadcasting and listening. If the current device
     * is marked as a sender (`_messaging.value == true`), it spreads its own message via
     * a gradient-based mechanism. At the same time, it listens for messages from other
     * nearby devices using the Collektive framework.
     *
     * During each cycle:
     * - It computes and updates the gradient-based broadcast program.
     * - It updates the list of known senders using message propagation (`listenOtherSources`).
     * - It updates the list of all nearby devices and their message intentions.
     * - It saves any new messages received from nearby nodes into `_received`.
     * - If new messages are received, they are added to the main message list.
     *
     * @param distance The maximum communication distance for message propagation.
     * @param nearbyDevicesViewModel ViewModel containing device ID and user identity.
     * @param userName The user name of the local device, used for display and attribution.
     * @param time The timestamp associated with the start of the current message session.
     */
    fun listen(
        distance: Float,
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        userName: String,
        time: LocalDateTime
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val program = spreadAndListen(
                isSender = _sendFlag.value,
                deviceId = nearbyDevicesViewModel.deviceId,
                userName = userName,
                distance = distance,
                position = _coordinates.value!!,
                message = _messageToSend.value
            )
            flow {
                while (_online.value) {
                    emit(Unit)
                    delay(1.seconds)
                }
            }.onEach {
                _senders.value.clear()
                _devices.value.clear()
                _received.value.clear()
                val newResult = program.cycle()
                _dataFlow.value = newResult
                if(newResult.second.first != POSITIVE_INFINITY) {
                    val allSender = listenOtherSources(nearbyDevicesViewModel.deviceId, newResult).cycle()
                    _senders.value.putAll(allSender.map { it.value.first to it.value.second })
                }
                _devices.value.putAll(getListOfDevices(nearbyDevicesViewModel).cycle())
                _received.value.putAll(
                    saveNewMessages(
                        nearbyDevicesViewModel = nearbyDevicesViewModel,
                        position = _coordinates.value!!,
                        time = time,
                        userName = nearbyDevicesViewModel.userName.value,
                        devices = _devices.value,
                        senders = _senders.value
                    ).cycle()
                )
                if(_received.value.isNotEmpty()) {
                    addNewMessagesToList(_received.value.toMap())
                }
            }.flowOn(Dispatchers.Default).launchIn(this)
        }
    }

    /**
     * Starts sending a message and propagates it to nearby devices using a Collektive program.
     *
     * This function launches a coroutine that continuously runs a gradient-based
     * propagation program, broadcasting the message from the local device to
     * other devices within the specified distance.
     *
     * @param distance The maximum distance (in meters) that the message should propagate.
     * @param nearbyDevicesViewModel The ViewModel containing context about the local device, including its unique ID.
     * @param userName The name of the user sending the message.
     *
     * The function creates a Collektive program which spreads the message from the sender
     * and repeatedly executes a cycle to propagate and update message state while the
     * messaging flag (_messaging.value) remains true.
     *
     * During each cycle, the new propagated data is saved into the `_dataFlow` state,
     * and the coroutine delays for 1 second before continuing.
     */
    fun send(
        distance: Float,
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        userName: String
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val program = spreadAndListen(
                isSender = _sendFlag.value,
                deviceId = nearbyDevicesViewModel.deviceId,
                userName = userName,
                distance = distance,
                position = _coordinates.value!!,
                message = _messageToSend.value
            )
            flow {
                while (_sendFlag.value) {
                    emit(Unit)
                    delay(1.seconds)
                }
            }.onEach {
                val newResult = program.cycle()
                _dataFlow.value = newResult
            }.flowOn(Dispatchers.Default).launchIn(this)
        }
    }

    /**
     * Collects message intentions propagated by nearby devices, as perceived by the local device.
     *
     * This suspending function instantiates a Collektive program that leverages MQTT-based communication
     * to observe and aggregate message-related data from neighboring devices. Each neighbor's data includes
     * a unique identifier and a triple representing its distance, username, and message content.
     *
     * The function assumes that the given `sender` represents the local device's current messaging state,
     * which is then used to query nearby devices via the `neighboring` construct.
     *
     * @param deviceId The UUID of the local device that is executing the data collection.
     * @param sender A pair consisting of the local device UUID and a triple containing:
     *               - the distance used for message propagation,
     *               - the local user's name,
     *               - the message content being broadcast.
     *
     * @return A Collektive program that produces a map where each entry corresponds to a neighboring device UUID,
     *         associated with a pair consisting of:
     *         - the sender’s UUID (source of the message),
     *         - a triple with (distance, username, message) information.
     */
    private suspend fun listenOtherSources(
        deviceId: Uuid,
        sender:  Pair<Uuid, Triple<Float, String, String>>
    ): Collektive<Uuid, Map<Uuid, Pair<Uuid, Triple<Float, String, String>>>> =
        Collektive(deviceId, MqttMailbox(deviceId, host = IP_HOST, dispatcher = dispatcher)) {
            neighboring(sender).toMap()
        }

    /**
     * Starts a Collektive program that uses a gradient to propagate a message
     * (containing a distance, username, and message string) from a sender node to all nearby devices.
     *
     * @param isSender whether the current device is the source of the message.
     * @param deviceId the unique identifier of the current device.
     * @param userName the name of the user sending or receiving the message.
     * @param distance the maximum propagation distance allowed for the message.
     * @param position the 3D position of the current device.
     * @param message the content of the message to be propagated.
     * @return a [Collektive] instance that computes a mapping from each device in the network
     *         to the closest sender device and the associated message parameters as a [Triple]
     *         (propagation distance, sender name, message text).
     *
     * This function internally builds a distance-based gradient rooted at the sender(s) and
     * propagates only data that is within the given `distance` from the source. Devices beyond
     * the specified distance threshold receive a marker with `POSITIVE_INFINITY` and empty message.
     */
    private suspend fun spreadAndListen(
        isSender: Boolean,
        deviceId: Uuid,
        userName: String,
        distance: Float,
        position: Point3D,
        message: String
    ): Collektive<Uuid, Pair<Uuid, Triple<Float, String, String>>> =
        Collektive(deviceId, MqttMailbox(deviceId, IP_HOST, dispatcher = dispatcher)) {
            gradientCast(
                source = isSender,
                local = deviceId to Triple(distance, userName, message),
                metric = euclideanDistance3D(position),
                accumulateData = { fromSource, toNeighbor, dist ->
                    if (fromSource + toNeighbor <= distance.toDouble()) {
                        dist
                    } else {
                        deviceId to Triple(POSITIVE_INFINITY, userName, "")
                    }
                }
            )
        }

    /**
     * Retrieves the current map of devices detected in the network vicinity,
     * combining the known senders and the local neighborhood information.
     *
     * This suspending function creates and runs a Collektive program which
     * aggregates data about nearby devices. It merges the existing senders'
     * information with dynamically gathered neighborhood data to form a
     * comprehensive device map.
     *
     * The size of the resulting device map is used to update the internal
     * count of devices currently present in the chat network.
     *
     * @param nearbyDevicesViewModel The ViewModel instance providing
     *                               contextual device information such as
     *                               the local device ID and username.
     *
     * @return A Collektive program that produces a map from device UUIDs
     *         to a Triple containing the distance (Float), username (String),
     *         and current message (String) associated with each device.
     */
    private suspend fun getListOfDevices(nearbyDevicesViewModel: NearbyDevicesViewModel): Collektive<Uuid, Map<Uuid, Triple<Float, String, String>>> {
        val program = Collektive(nearbyDevicesViewModel.deviceId, MqttMailbox(nearbyDevicesViewModel.deviceId, IP_HOST, dispatcher = dispatcher)) {
            val neighborhoodMap = mapNeighborhood { id ->
                _senders.value[id] ?: Triple(-1f, nearbyDevicesViewModel.userName.value, "")
            }.toMap()
            val combined = _senders.value.toMutableMap()
            combined.putAll(neighborhoodMap)
            combined.toMap()
        }

        val size = program.cycle().size
        _counterDevices.value = size
        return program
    }

    /**
     * Saves and processes new message information from nearby devices within a geospatial network.
     *
     * This suspending function creates a new instance of [Collektive] representing the aggregate
     * network centered on the current device. It calculates spatial relationships and message
     * propagation details among devices based on their 3D positions and message metadata.
     *
     * For each neighboring device, the function computes a list of [Params] objects that encapsulate
     * message-related information, including:
     * - The sender device's ID and message content.
     * - The current device's ID and username.
     * - The sender's reported distance metric.
     * - The Euclidean distance between the sender and the current device.
     * - Additional sender-specific parameters.
     * - The timestamp representing when the message was generated or observed.
     * - A Boolean flag indicating whether the sender is considered valid and active in the current context.
     *
     * The resulting map filters entries to only include those where the sender is active (indicated
     * by [Params.isSenderValues]) to focus on relevant message propagations.
     *
     * @param nearbyDevicesViewModel The ViewModel managing information about nearby devices, including
     *                               the current device's ID and network mailbox configuration.
     * @param position The 3D spatial coordinates of the current device, used for distance calculations.
     * @param time The timestamp representing the current simulation or message observation time.
     * @param userName The username or identifier associated with the current device.
     * @param senders A map associating sender device IDs ([Uuid]) with triples containing:
     *                - Float: The sender's reported distance or metric.
     *                - String: The message content sent by the device.
     *                - String: Additional parameters or metadata about the sender/message.
     * @param devices A map of all detected device IDs ([Uuid]) to triples similar to [senders], representing
     *                message and parameter information for nearby devices.
     *
     * @return A [Collektive] instance parametrized by device ID ([Uuid]) and a map that associates each
     *         sender ID with a filtered list of [Params], representing valid message data relevant
     *         for the current device's spatial context and message propagation state.
     *
     * @throws Exception Propagates any exceptions raised during mailbox creation or network operations.
     */
    private suspend fun saveNewMessages(
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        position: Point3D,
        time: LocalDateTime,
        userName: String,
        senders: Map<Uuid, Triple<Float, String, String>>,
        devices: Map<Uuid, Triple<Float, String, String>>,
    ) : Collektive<Uuid, Map<Uuid, List<Params>>> =
        Collektive(nearbyDevicesViewModel.deviceId, MqttMailbox(nearbyDevicesViewModel.deviceId, IP_HOST, dispatcher = dispatcher)) {
            neighboring(devices).alignedMap(euclideanDistance3D(position)) { _: Uuid, deviceValues: Map<Uuid, Triple<Float, String, String>>, distance: Double ->
                deviceValues.entries.map { (sender, messagingParams) ->
                    Params(
                        sender to messagingParams.second,
                        localId to userName,
                        messagingParams.first,
                        distance,
                        messagingParams.third,
                        time,
                        senders.containsKey(sender) && messagingParams.first != -1f && sender != localId
                    )
                }
            }.toMap()
                .mapValues { (key, list) ->
                    list.filter { it.isSenderValues }
                }
        }
}
