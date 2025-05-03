package it.unibo.collektive.model

import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * TODO: doc
 */
data class Message(
    val text: String,
    val userName: String,
    val sender: Uuid,
    val receiver: Uuid,
    val time: String,
    val distance: Float,
    val timestamp: LocalDateTime,
    val id: Int = generateId(text, userName, sender, receiver, time, distance, timestamp)
) {
    companion object {
        fun generateId(
            text: String,
            userName: String,
            sender: Uuid,
            receiver: Uuid,
            time: String,
            distance: Float,
            timestamp: LocalDateTime
        ): Int {
            return "$text|$userName|$sender|$receiver|$time|$distance|$timestamp".hashCode()
        }
    }
}
