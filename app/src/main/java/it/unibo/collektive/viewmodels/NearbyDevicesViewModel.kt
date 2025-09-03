package it.unibo.collektive.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.network.mqtt.MqttMailbox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A ViewModel that manages the list of nearby devices.
 */
class NearbyDevicesViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    providedScope: CoroutineScope? = null
) : ViewModel() {
    val debugHandler = CoroutineExceptionHandler { ctx, ex ->
        println("Coroutine failed in $ctx: $ex")
    }
    private val externalScope = if (providedScope == null) {
        viewModelScope
    } else {
        CoroutineScope(SupervisorJob() + dispatcher + debugHandler)
    }
    private val _dataFlow = MutableStateFlow<Set<Uuid>>(emptySet())
    private val _connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _userName = MutableStateFlow("User")
    private val _online = MutableStateFlow(true)

    /**
     * The connection state.
     */
    enum class ConnectionState {
        /**
         * Connected to the broker.
         */
        CONNECTED,

        /**
         * Disconnected from the broker.
         */
        DISCONNECTED,
    }

    /**
     * The set of nearby devices.
     */
    val dataFlow: StateFlow<Set<Uuid>> = _dataFlow.asStateFlow()

    /**
     * The connection state.
     */
    val connectionFlow: StateFlow<ConnectionState> = _connectionFlow.asStateFlow()

    /**
     * The user name of local device.
     */
    val userName: StateFlow<String> get() = _userName

    /**
     * The local device ID.
     */
    val deviceId = Uuid.random()

    /**
     * Change user name of local device.
     */
    fun setUserName(value: String){
        this._userName.value = value
    }

    /**
     * Online devices in the home page.
     */
    fun setOnlineStatus(flag: Boolean){
        this._online.value = flag

    }

    /**
     * Creates a Collektive program that identifies the neighboring devices of the local node.
     *
     * This suspending function builds a Collektive computation that leverages MQTT-based
     * communication to retrieve the set of UUIDs corresponding to all neighboring devices
     * currently visible to the local device.
     *
     * The function uses the `neighboring` construct to inspect the local communication
     * neighborhood, extracting and returning the UUIDs of the devices with which the local
     * node can interact.
     *
     * @return A Collektive program that computes a set of UUIDs representing
     *         the neighboring devices of the current local node.
     */
     private suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> =
        Collektive(deviceId, MqttMailbox(deviceId, host = IP_HOST, dispatcher = dispatcher)) {
            neighboring(localId).neighbors.toSet()
        }

    /**
     * Start the Collektive program.
     */
    fun startCollektiveProgram() {
        externalScope.launch(dispatcher) {
            val program = collektiveProgram()
            _connectionFlow.value = ConnectionState.CONNECTED
            generateHeartbeatFlow()
                .onEach {
                    val newResult = program.cycle()
                    _dataFlow.value = newResult
                }
                .flowOn(dispatcher)
                .launchIn(this)
        }
    }

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
            delay(2.seconds)
        }
    }

    fun cancel() {
        externalScope.cancel()
    }

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
    }
}
