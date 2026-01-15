package it.unibo.collektive.viewmodels

import android.location.Location
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.model.Message
import it.unibo.collektive.model.Params
import it.unibo.collektive.viewmodels.utils.SystemTimeProvider
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.networking.Mailbox
import it.unibo.collektive.stdlib.fields.fold
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.spreading.multiGradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import it.unibo.collektive.viewmodels.utils.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import java.time.LocalDateTime
import kotlin.Float.Companion.MAX_VALUE
import kotlin.math.ceil
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
class MessagesViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    providedScope: CoroutineScope? = null,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val mailboxFactory: suspend (Uuid) -> Mailbox<Uuid> = { id ->
        MqttMailbox(id, IP_HOST, dispatcher = dispatcher)
    }
) : ViewModel() {
    val debugHandler = CoroutineExceptionHandler { ctx, ex ->
        println("Coroutine failed in $ctx: $ex")
    }

    private val externalScope = if (providedScope == null) {
        viewModelScope
    } else {
        CoroutineScope(SupervisorJob() + dispatcher + debugHandler)
    }

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

    /**
     * Internal list of messages that are pending for transmission.
     * These messages will be processed and sent out according to their
     * specified spreading time.
     */
    private val _pendingMessages = mutableStateListOf<EnqueueMessage>()
    val pendingMessages: List<EnqueueMessage> get() = _pendingMessages

    /**
     * A state flow holding the list of currently active Collektive programs,
     * each paired with its expiration timestamp in milliseconds.
     *
     * Each element of the list is a pair:
     * - The first component is a `Collektive<Uuid, Unit>` instance representing
     *   an aggregate program (e.g., for message propagation).
     * - The second component is a `Long` value representing the expiration time
     *   (in epoch milliseconds). Once the current system time exceeds this value,
     *   the program is considered expired and is removed from the list.
     *
     * This structure allows concurrent execution of multiple aggregate programs,
     * including message broadcasts, for a limited time window.
     *
     * It is updated by `sendHeartbeatPulse()` and consumed by `listenAndSend()`,
     * which periodically filters out expired programs and cycles the active ones.
     *
     * Related: `sendHeartbeatPulse()`, `listenAndSend()`, `Collektive`
     */
    private val _programs = MutableStateFlow<List<Pair<Collektive<Uuid, Unit>, Long>>>(emptyList())
    val programs: StateFlow<List<Pair<Collektive<Uuid, Unit>, Long>>> get() = _programs

    // Current position of the device
    private val _position = MutableStateFlow<Location?>(null)
    private val _coordinates = MutableStateFlow<Point3D?>(null)

    /**
     * The number of devices in the chat.
     */
    val devicesInChat: StateFlow<Int> = _counterDevices.asStateFlow()

    /**
     * Public immutable flows exposed to UI.
     */
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    /**
     * Epoch time (in milliseconds) indicating when this device became a source.
     *
     * This is `null` if the device is not currently a source.
     * Used exclusively for testing purposes.
     */
    private val _sourceSince = MutableStateFlow<Long?>(null)
    val sourceSince: StateFlow<Long?> get() = _sourceSince

    /**
     * Marks the entity as a message source by recording the current timestamp.
     *
     * This function sets the backing property `_sourceSince` to the specified time,
     * or to the current time obtained from [timeProvider] if no argument is provided.
     * Primarily intended for testing purposes to simulate source activation.
     *
     * @param now the timestamp to set as the source start time, defaulting to the current time.
     */
    fun markAsSource(now: Long = timeProvider.currentTimeMillis()) {
        _sourceSince.value = now
    }

    /**
     * Clears the source status of the entity.
     *
     * This function resets the backing property `_sourceSince` to `null`, effectively
     * marking the entity as no longer being a message source.
     * Primarily intended for testing purposes to simulate source deactivation.
     */
    fun clearSourceStatus() {
        _sourceSince.value = null
    }

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
                userName = newMessage.sender.second,
                sender = newMessage.sender.first,
                receiver = newMessage.receiver.first,
                time = "${newMessage.timestamp.hour}:$minute",
                distance = ceil(newMessage.distance).toFloat(),
                timestamp = newMessage.timestamp
            )
        }.filterNot { msg ->
            tmp.any { existing ->
                existing.id == msg.id
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
        externalScope.launch(dispatcher) {
            while (coroutineContext.isActive) {
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
     * Retrieves the current state of the send flag.
     *
     * This function returns the current value of the backing property `_sendFlag`,
     * indicating whether sending is currently enabled (`true`) or disabled (`false`).
     *
     * @return `true` if sending is enabled, `false` otherwise.
     */
    fun getSendFlag(): Boolean {
        return _sendFlag.value
    }

    /**
     * Updates the current device location and normalizes coordinates using the ECEF reference system.
     *
     * This function performs a coordinate transformation cycle:
     * 1. It captures the raw geodetic location (Latitude, Longitude, Altitude).
     * 2. It converts the geodetic data into the ECEF (Earth-Centered, Earth-Fixed) Cartesian system
     * to ensure metric consistency.
     * 3. It converts the ECEF coordinates back to geodetic format to populate the internal
     * [_coordinates] state.
     *
     * This normalization process is crucial for the Collektive engine to calculate accurate
     * Euclidean distances in meters, avoiding spherical distortions.
     *
     * @param location The [Location] object provided by the system's location provider.
     */
    fun setLocation(location: Location?) {
        location?.let {
            _position.value = it
            // Step 1: Forward transformation to ECEF
            val ecefPoint = latLonAltToECEF(
                it.latitude,
                it.longitude,
                it.altitude
            )
            // Step 2: Backward transformation to normalize Geodetic values
            val (newLat, newLon, newAlt) = ECEFToLatLonAlt(
                Point3D(Triple(ecefPoint.x, ecefPoint.y, ecefPoint.z))
            )
            // Step 3: Update the state used for spatial computation
            _coordinates.value = Point3D(Triple(newLat, newLon, newAlt))
        }
    }

    /**
     * Transforms Geodetic coordinates (WGS84) into the ECEF Cartesian coordinate system.
     *
     * The transformation uses the standard WGS84 ellipsoid parameters:
     * - Semi-major axis (a): 6,378,137.0 meters.
     * - First eccentricity squared (e²): 6.69437999014e-3.
     *
     * This conversion maps a point on the Earth's surface to a 3D vector (X, Y, Z)
     * measured in meters from the Earth's center of mass.
     *
     * @param lat Latitude in decimal degrees.
     * @param lon Longitude in decimal degrees.
     * @param alt Altitude in meters above the ellipsoid.
     * @return A [Point3D] representing the ECEF coordinates.
     */
    private fun latLonAltToECEF(lat: Double, lon: Double, alt: Double): Point3D {
        val a = 6378137.0
        val e2 = 6.69437999014e-3
        val radLat = Math.toRadians(lat)
        val radLon = Math.toRadians(lon)
        val n = a / Math.sqrt(1 - e2 * Math.sin(radLat) * Math.sin(radLat))
        val x = (n + alt) * Math.cos(radLat) * Math.cos(radLon)
        val y = (n + alt) * Math.cos(radLat) * Math.sin(radLon)
        val z = (n * (1 - e2) + alt) * Math.sin(radLat)
        return Point3D(Triple(x, y, z))
    }

    /**
     * Transforms ECEF Cartesian coordinates back into Geodetic coordinates (Lat, Lon, Alt).
     *
     * This implementation uses Bowring's irrational method to accurately retrieve
     * geodetic latitude, longitude, and altitude from a Cartesian vector.
     *
     * @param point The [Point3D] in ECEF coordinates (X, Y, Z in meters).
     * @return A [Triple] containing Latitude (degrees), Longitude (degrees), and Altitude (meters).
     */
    private fun ECEFToLatLonAlt(point: Point3D): Triple<Double, Double, Double> {
        val a = 6378137.0
        val e2 = 6.69437999014e-3
        val ePrime2 = e2 / (1 - e2)
        val x = point.x
        val y = point.y
        val z = point.z
        val p = Math.sqrt(x * x + y * y)
        val theta = Math.atan2(z * a, p * (1 - e2) * a)
        val lat = Math.atan2(
            z + ePrime2 * a * Math.pow(Math.sin(theta), 3.0),
            p - e2 * a * Math.pow(Math.cos(theta), 3.0)
        )
        val lon = Math.atan2(y, x)
        val n = a / Math.sqrt(1 - e2 * Math.sin(lat) * Math.sin(lat))
        val alt = p / Math.cos(lat) - n
        return Triple(Math.toDegrees(lat), Math.toDegrees(lon), alt)
    }

    /**
     * Retrieves the current geographic location of the entity.
     *
     * This function returns the current value stored in the backing property `_position`,
     * which represents the entity's location in terms of latitude and longitude.
     * If no location is available, the function returns `null`.
     *
     * @return the current [Location] if available, or `null` otherwise.
     */
    fun getLocation(): Location? {
        return _position.value
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
     * Adds a message to the pending messages queue to be sent after the current propagation completes.
     *
     * If a message is already being propagated (i.e., another message is currently in progress),
     * the enqueued message will wait in the queue until the previous message finishes.
     *
     * @param message The text content of the message to enqueue.
     * @param time The timestamp representing when the message was created or requested for sending.
     * @param distance The maximum distance (in meters) within which the message should be propagated.
     * @param spreadingTime The duration (in seconds) for which the message should be propagated.
     */
    fun enqueueMessage(
        message: String,
        time: LocalDateTime,
        distance: Float,
        spreadingTime: Int
    ) {
        _pendingMessages.add(
            EnqueueMessage(
                text = message,
                time = time,
                distance = distance,
                spreadingTime = spreadingTime
            )
        )
    }

    /**
     * Dequeues and removes the first message from the list of pending messages.
     *
     * @return The first [EnqueueMessage] in the queue, or `null` if the queue is empty.
     *
     * This function follows a FIFO (First-In-First-Out) policy, returning the oldest message
     * waiting to be sent. Once dequeued, the message is removed from the pending list.
     */
    private fun dequeueMessage(): EnqueueMessage? {
        return if (_pendingMessages.isNotEmpty()) {
            _pendingMessages.removeAt(0)
        } else {
            null
        }
    }

    /**
     * Checks whether this [Params] instance represents the same logical message as another.
     *
     * Two messages are considered the same if they originate from the same [sender]
     * and contain identical message content ([message]), regardless of the receiver,
     * distance, timestamp, or any other metadata.
     *
     * This is useful when filtering out duplicate messages that were relayed
     * multiple times but originate from the same source.
     *
     * @param other The [Params] instance to compare against.
     * @return `true` if both messages have the same [sender] and [message], `false` otherwise.
     */
    private fun Params.isSameMessage(other: Params): Boolean {
        return this.sender == other.sender && this.message == other.message
    }

    /**
     * Starts listening and sending messages using the Collektive framework.
     *
     * This function manages:
     * 1. A main "listener" program that continuously runs and listens for incoming messages.
     * 2. A dynamic list of additional "sender" programs, each responsible for broadcasting
     *    a specific message for a limited amount of time.
     *
     * Each program is an instance of an aggregate program created via `createProgram`.
     * On every heartbeat tick, all currently active programs are executed via `cycle()`.
     * Expired sender programs (based on their `spreadingTime`) are automatically removed
     * from the list of active programs.
     *
     * @param nearbyDevicesViewModel The ViewModel that holds the current user’s name and
     *        information about nearby devices.
     *
     * Function behavior:
     * - Initializes a default listener-only program (without a message to send) and stores
     *   it with a very long duration (`Long.MAX_VALUE`) to keep it always active.
     * - On each heartbeat emission, the function:
     *   - Clears any temporary state,
     *   - Removes expired sender programs,
     *   - Executes the `cycle()` of all active programs (both listener and senders).
     * - Additional sender programs are created separately by `sendHeartbeatPulse()` and
     *   added dynamically to the `_programs` list with their respective expiration times.
     *
     * Related: `sendHeartbeatPulse()`, `createProgram()`, `_programs`
     */
    fun listenAndSend(nearbyDevicesViewModel: NearbyDevicesViewModel, userName: String) {
        externalScope.launch(dispatcher) {
            val listenProgram = createProgram(
                nearbyDevicesViewModel,
                userName,
                EnqueueMessage("", timeProvider.now(), MAX_VALUE, 0)
            )
            _programs.value = listOf(listenProgram to Long.MAX_VALUE)
            generateHeartbeatFlow()
                .onEach {
                    clear()
                    val now = timeProvider.currentTimeMillis()
                    _programs.value = _programs.value.filter { (_, endTime) -> now < endTime }
                    _programs.value.forEach { (program, _) -> program.cycle() }
                }
                .flowOn(dispatcher)
                .launchIn(this)
        }
        dequeueAndSend(nearbyDevicesViewModel)
    }

    /**
     * Continuously listens for new messages to send and adds corresponding
     * aggregate programs to the active program list (`_programs`).
     *
     * This function runs in a background coroutine and checks for messages
     * queued via `dequeueMessage()`. For each message:
     * - A new aggregate program is created via `createProgram()`.
     * - The program is added to the `_programs` list along with its expiration timestamp,
     *   computed based on the `spreadingTime` field of the message.
     *
     * These additional programs will then be executed on each heartbeat tick
     * by the main `listenAndSend()` loop, until their time expires.
     *
     * If no message is available, the function waits 1 second before retrying.
     *
     * @param nearbyDevicesViewModel The ViewModel containing the user name and
     *        the local device context for program creation.
     *
     * @throws IllegalStateException if the message spreading time is too short
     *         (i.e., less than `MINIMUM_TIME_TO_SEND` seconds).
     *
     * Related: `listenAndSend()`, `_programs`, `createProgram()`
     */
    private fun dequeueAndSend(
        nearbyDevicesViewModel: NearbyDevicesViewModel
    ){
        externalScope.launch(dispatcher) {
            while (coroutineContext.isActive) {
                val enqueueMessage = dequeueMessage()
                if (enqueueMessage != null) {
                    if (enqueueMessage.spreadingTime.seconds < MINIMUM_TIME_TO_SEND) {
                        throw IllegalStateException("The time to send the message is too short")
                    }
                    val newProgram = createProgram(
                        nearbyDevicesViewModel,
                        nearbyDevicesViewModel.userName.value,
                        enqueueMessage
                    )
                    val endTime = timeProvider.currentTimeMillis() + (enqueueMessage.spreadingTime * 1000L)
                    _programs.update { it + (newProgram to endTime) }
                } else {
                    if(_sendFlag.value){
                        _sendFlag.value = false
                    }
                    delay(1.seconds)
                }
            }
        }
    }

    /**
     * Creates and returns a new instance of the propagation/listening program.
     *
     * This function wraps a call to [spreadAndListen], providing the current device ID and user name.
     * It is used to (re)initialize the program that manages the communication logic.
     *
     * @param nearbyDevicesViewModel The ViewModel containing information about the local device and nearby devices.
     * @param userName The name of the user to associate with the program.
     * @return A new instance of the program returned by [spreadAndListen].
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun createProgram(
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        userName: String,
        message: EnqueueMessage
    ) = spreadAndListen(
        deviceId = nearbyDevicesViewModel.deviceId,
        userName = userName,
        nearbyDevicesViewModel = nearbyDevicesViewModel,
        distance = message.distance,
        message = message.text,
        time = message.time
    )

    /**
     * Creates a flow that emits a signal every second while the system is online.
     *
     * This flow is typically used to drive periodic actions (e.g., program cycles)
     * by emitting a [Unit] value once per second. The emission continues as long as
     * the `_online` flag is `true`. Once `_online` becomes `false`, the flow completes.
     *
     * @return A cold [Flow] emitting [Unit] every second while `_online.value` is `true`.
     */
    private fun generateHeartbeatFlow(): Flow<Unit> = flow {
        while (_online.value) {
            emit(Unit)
            delay(1.seconds)
        }
    }

    /**
     * Processes the results of a propagation cycle by updating senders, devices, and received messages.
     *
     * - If the received distance is finite, it listens for other message sources and updates the senders list.
     * - Updates the list of nearby devices.
     * - Saves new messages based on the current device, position, time, username, and known devices and senders.
     * - If there are new received messages, they are added to the displayed message list.
     *
     * @param newResult The result from the propagation cycle, containing the message UUID and a Triple with
     *                  distance, sender ID, and message content.
     * @param nearbyDevicesViewModel Reference to the NearbyDevicesViewModel for device information.
     * @param time The LocalDateTime timestamp representing the current processing time.
     */
    private fun Aggregate<Uuid>.workflow(
        newResult: Pair<Uuid, Triple<Float, String, String>>,
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        time: LocalDateTime,
        position: Point3D
    ){
        if(newResult.second.first != MAX_VALUE) {
            val allSender = listenOtherSources(newResult)
            _senders.value.putAll(allSender.map { it.value.first to it.value.second })
        }
        _devices.value.putAll(getListOfDevices(nearbyDevicesViewModel))
        _received.value.putAll(
            saveNewMessages(
                position = position,
                time = time,
                userName = nearbyDevicesViewModel.userName.value,
                devices = _devices.value,
                senders = _senders.value
            )
        )
        val sources = sources(_received.value.isNotEmpty())
        val updateNewMessages = spreadNewMessage(
            sources = sources,
            incomingMessages = _received.value,
            position = position,
            userName = nearbyDevicesViewModel.userName.value
        )
        val tmp = _received.value.mapValues { it.value.toMutableList() }.toMutableMap()
        updateNewMessages.forEach { (_, messagesFromOthers) ->
            messagesFromOthers.forEach { (key, list) ->
                val currentList = tmp.getOrPut(key) { mutableListOf() }
                val newMessages = list.filter { newMsg ->
                    currentList.none { existing -> existing.isSameMessage(newMsg) }
                }
                currentList.addAll(newMessages)
            }
        }
        _received.value = tmp.mapValues { it.value.toList() }.toMutableMap()
        if(_received.value.isNotEmpty()) {
            addNewMessagesToList(_received.value.toMap())
        }
    }

    private fun clear(){
        _senders.value.clear()
        _devices.value.clear()
        _received.value.clear()
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
    private fun Aggregate<Uuid>.listenOtherSources(
        sender:  Pair<Uuid, Triple<Float, String, String>>
    ): Map<Uuid, Pair<Uuid, Triple<Float, String, String>>> = neighboring(sender).toMap()

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
        isSender: Boolean = _sendFlag.value,
        deviceId: Uuid,
        userName: String,
        distance: Float,
        position: Point3D = _coordinates.value?: Point3D(Triple(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)),
        message: String,
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        time: LocalDateTime
    ): Collektive<Uuid, Unit> =
        Collektive(deviceId, mailboxFactory(deviceId)) {
            val result = gradientCast(
                source = isSender,
                local = deviceId to Triple(distance, userName, message),
                metric = euclideanDistance3D(position),
                accumulateData = { fromSource, toNeighbor, value ->
                    if (fromSource + toNeighbor <= distance.toDouble()) {
                        value
                    } else {
                        deviceId to Triple(MAX_VALUE, userName, "")
                    }
                }
            )
            workflow(result, nearbyDevicesViewModel, time, position)
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
    private fun Aggregate<Uuid>.getListOfDevices(nearbyDevicesViewModel: NearbyDevicesViewModel): Map<Uuid, Triple<Float, String, String>> {
        val neighborhoodMap = mapNeighborhood { id ->
            _senders.value[id] ?: Triple(-1f, nearbyDevicesViewModel.userName.value, "")
        }.toMap()
        val combined = _senders.value.toMutableMap()
        combined.putAll(neighborhoodMap)
        combined.toMap()
        val size = neighborhoodMap.size
        _counterDevices.value = size
        return neighborhoodMap
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
    private fun Aggregate<Uuid>.saveNewMessages(
        position: Point3D,
        time: LocalDateTime,
        userName: String,
        senders: Map<Uuid, Triple<Float, String, String>>,
        devices: Map<Uuid, Triple<Float, String, String>>,
    ) : Map<Uuid, List<Params>> =
        neighboring(devices).alignedMap(euclideanDistance3D(position)) { _: Uuid, deviceValues: Map<Uuid, Triple<Float, String, String>>, distance: Double ->
            deviceValues.entries.map { (sender, messagingParams) ->
                Params(
                    sender = sender to messagingParams.second,
                    receiver = localId to userName,
                    distanceForMessaging = messagingParams.first,
                    distance = distance,
                    message = messagingParams.third,
                    timestamp = time,
                    isSenderValues =
                        senders.containsKey(sender) &&
                        messagingParams.first != -1f &&
                        sender != localId
                )
            }
        }.toMap()
            .filterKeys { senders.containsKey(it) && it != localId }
            .mapValues { (key, list) ->
                list.filter { it.isSenderValues && it.distance <= it.distanceForMessaging && it.sender.first == key}
            }

    /**
     * Propagates received messages from neighboring nodes using a multi-source gradient,
     * updating the distances and forwarding only messages within the allowed communication radius.
     *
     * This function relies on `multiGradientCast` to perform a distance-based diffusion from
     * multiple sources, leveraging a 3D Euclidean distance metric. The propagation is constrained
     * by each message's `distanceForMessaging`, ensuring that messages do not spread beyond
     * their intended range.
     *
     * @param incomingMessages a map where each key is a source node ID (`Int`), and the corresponding
     *        value is a list of [SourceDistances] representing messages received from that source.
     * @param position the current 3D position of the local node, used to compute distance to neighbors.
     *
     * @return a nested map where the outer keys are neighbor node IDs (`Int`), and the values
     *         are maps associating each message source ID to a filtered list of [SourceDistances].
     *         Each message is updated with the cumulative distance and the current node's ID
     *         as the new intermediate sender (`from`). Messages that exceed their allowed
     *         `distanceForMessaging` are discarded.
     *
     * The returned map excludes empty lists, keeping only meaningful propagated data.
     */
    private fun Aggregate<Uuid>.spreadNewMessage(
        sources: Set<Uuid>,
        incomingMessages: Map<Uuid, List<Params>>,
        position: Point3D,
        userName: String
    ) : Map<Uuid, Map<Uuid, List<Params>>> =
        multiGradientCast(
            sources = sources,
            local = incomingMessages,
            metric = euclideanDistance3D(position),
            accumulateData = { fromSource, toNeighbor, value ->
                value.mapValues { (_, list) ->
                    list.mapNotNull {
                        val totalDistance = it.distance + fromSource + toNeighbor
                        if (totalDistance <= it.distanceForMessaging) {
                            it.copy(receiver = localId to userName, distance = totalDistance)
                        } else {
                            null
                        }
                    }.filter { it.sender.first != localId }
                }.filterValues { it.isNotEmpty() }
            },
        )
            .filterKeys { it != localId }
            .filterValues { it.isNotEmpty() }

    /**
     * Shares and aggregates sets of `Uuid` identifiers from neighboring nodes,
     * optionally including the local node's identifier.
     *
     * This function uses a sharing mechanism (`share`) to exchange and collect sets of `Uuid`
     * from neighbors in an `Aggregate<Uuid>` network. For each node, it collects the union
     * of all sets received from neighbors and, if the `from` parameter is `true`, adds the
     * local node's identifier (`localId`) to the resulting set.
     *
     * @param from indicates whether to include the local node's identifier in the resulting set.
     *             If `true`, the local identifier is added to the aggregated set.
     *             If `false`, only the aggregated set from neighbors is returned.
     *
     * @return a set (`Set<Uuid>`) containing the identifiers collected from neighbors,
     *         including the local identifier only if `from` is `true`.
     */
    private fun Aggregate<Uuid>.sources(
        from: Boolean,
    ) : Set<Uuid> =
        share(emptySet<Uuid>()) { neighborSources ->
            neighborSources.fold(emptySet<Uuid>()) { accumulated, neighborSet ->
                accumulated union neighborSet.value
            }.let { collected ->
                if (from) collected + localId else collected
            }
        }

    fun cancel() {
        externalScope.cancel()
    }

    /**
     * Retrieves the current list of messages stored in the ViewModel.
     *
     * This function provides a snapshot of all messages that have been received or
     * sent locally up to the moment of invocation. The returned list reflects the
     * current state of the underlying [messages] flow.
     *
     * @return a [List] of [Message] objects representing all messages currently
     *         stored in the ViewModel.
     */
    fun getCurrentListOfMessages() : List<Message> {
        return messages.value
    }

    /**
     * Logs the status of child jobs within [externalScope].
     *
     * This function retrieves the root [Job] from the [CoroutineContext] of [externalScope]
     * and iterates over its child jobs, printing their state:
     * - whether the job is active ([Job.isActive])
     * - whether the job has been cancelled ([Job.isCancelled])
     *
     * If no root job is found or if the root job has no children, an appropriate message is printed.
     *
     * @param tag An optional string to include in the log for contextual identification.
     *            Useful for distinguishing multiple invocations of `dumpJobs`.
     *
     * @sample
     * ```
     * dumpJobs("MyCoroutineTest")
     * // Possible output:
     * // [MyCoroutineTest] Job=StandaloneCoroutine{Active}@6f2b958c active=true cancelled=false
     * ```
     */
    fun dumpJobs(tag: String = "") {
        val rootJob: Job? = externalScope.coroutineContext[Job]
        if (rootJob == null) {
            println("[$tag] No root job found in externalScope")
            return
        }
        val children: Sequence<Job> = rootJob.children
        if (!children.any()) {
            println("[$tag] No children jobs")
        } else {
            children.forEach { child: Job ->
                println("[$tag] Job=$child active=${child.isActive} cancelled=${child.isCancelled}")
            }
        }
    }

    companion object {
        private const val IP_HOST = "broker.emqx.io"

        /**
         * Minimum time required for a message to propagate through the network.
         */
        val MINIMUM_TIME_TO_SEND = 5.seconds

        /**
         * Delay before deleting messages from local cache.
         */
        val TIME_FOR_DELETE_MESSAGES = 2.seconds
    }
}
