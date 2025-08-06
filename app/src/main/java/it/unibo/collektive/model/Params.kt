package it.unibo.collektive.model

import java.time.LocalDateTime
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
@Serializable
data class Params(
    val to: Pair<Uuid, String>,
    val from: Pair<Uuid, String>,
    val distanceForMessaging: Float,
    val distance: Double,
    val message: String,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val timestamp: LocalDateTime,
    val isSenderValues: Boolean
) {
    object JavaLocalDateTimeSerializer : KSerializer<LocalDateTime> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("JavaLocalDateTime", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: LocalDateTime) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): LocalDateTime {
            return LocalDateTime.parse(decoder.decodeString())
        }
    }
}

