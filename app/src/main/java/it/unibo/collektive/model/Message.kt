package it.unibo.collektive.model

import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Represents a chat message exchanged between devices in a geospatial network.
 *
 * This data class contains the message content, sender and receiver information,
 * time-related metadata, and distance data relevant for proximity-based communication.
 * Each message is uniquely identified by an ID generated from its properties.
 *
 * @property text The textual content of the message.
 * @property userName The display name of the user/device sending the message.
 * @property sender The unique identifier ([Uuid]) of the sender device.
 * @property receiver The unique identifier ([Uuid]) of the receiver device.
 * @property time A string representing the time the message was created or sent (human-readable).
 * @property distance The spatial distance (e.g., in meters) between sender and receiver at message creation.
 * @property timestamp The exact timestamp of the message creation, used for ordering and comparison.
 * @property id A unique identifier for the message, generated from the message's key properties.
 *
 * @constructor Creates a Message instance and automatically generates a unique [id] based on
 * the combination of its attributes.
 *
 * @see generateId
 */
data class Message(
    val text: String,
    val userName: String,
    val sender: Uuid,
    val receiver: Uuid,
    val time: String,
    val distance: Float,
    val timestamp: LocalDateTime,
    val id: Int = generateId(text, userName, sender, receiver, time, timestamp)
) {
    companion object {
        fun generateId(
            text: String,
            userName: String,
            sender: Uuid,
            receiver: Uuid,
            time: String,
            timestamp: LocalDateTime
        ): Int {
            return "$text|$userName|$sender|$receiver|$time|$timestamp".hashCode()
        }
    }
}
