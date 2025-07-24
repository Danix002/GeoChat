package it.unibo.collektive.model

import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Represents the metadata and state information associated with a message
 * exchanged between devices in a geospatial network.
 *
 * This data class encapsulates details about the message sender and receiver,
 * spatial distances involved, the message content, timing, and a flag indicating
 * if the sender is actively propagating the message.
 *
 * @property to A pair containing the recipient device's unique identifier ([Uuid]) and
 *              its associated username or label.
 * @property from A pair containing the sender device's unique identifier ([Uuid]) and
 *                its associated username or label.
 * @property distanceForMessaging The distance value reported or estimated by the sender,
 *                               used for message propagation or filtering.
 * @property distance The Euclidean distance between sender and recipient devices in 3D space.
 * @property message The content of the message being propagated.
 * @property timestamp The time at which the message was created or observed, useful for
 *                     ordering or expiring messages.
 * @property isSenderValues A boolean flag indicating whether the sender device is considered
 *                          an active source of valid messages in the current network state.
 */
data class Params(
    val to: Pair<Uuid, String>,
    val from: Pair<Uuid, String>,
    val distanceForMessaging: Float,
    val distance: Double,
    val message: String,
    val timestamp: LocalDateTime,
    val isSenderValues: Boolean
)
