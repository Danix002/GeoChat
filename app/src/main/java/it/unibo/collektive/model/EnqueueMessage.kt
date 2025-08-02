package it.unibo.collektive.model;

import java.time.LocalDateTime

/**
 * Represents a message that is queued to be sent after the current message propagation completes.
 *
 * This data class stores the essential details needed to perform a delayed message dispatch,
 * including the content, timestamp, propagation distance, and duration.
 *
 * @property text The content of the message to be sent.
 * @property time The timestamp at which the message was created or enqueued.
 * @property distance The propagation range (in meters) for the message.
 * @property spreadingTime The duration (in seconds) for which the message should be spread across the network.
 */
data class EnqueueMessage (
    val text: String,
    val time: LocalDateTime,
    val distance: Float,
    val spreadingTime: Int
)
