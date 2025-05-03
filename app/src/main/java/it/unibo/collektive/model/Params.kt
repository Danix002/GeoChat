package it.unibo.collektive.model

import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * TODO: doc
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
