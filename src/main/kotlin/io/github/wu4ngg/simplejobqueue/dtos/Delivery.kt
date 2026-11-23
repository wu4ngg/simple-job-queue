package io.github.wu4ngg.simplejobqueue.dtos

import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.toCode
import kotlinx.serialization.Serializable

@Serializable
data class EnqueueEventResponse(val deliveryId: String) {
}

@Serializable
data class DeliveryResponse(
    val id: String,
    val endpointId: String,
    val status: String,
    val attempts: Int,
    val nextAttemptAt: String,
    val lastError: String? = null,
    val createdAt: String,
)

fun Delivery.toResponse(): DeliveryResponse {
    return DeliveryResponse(
        id = this.id,
        endpointId = this.endpointId,
        status = this.status.toCode(),
        attempts = this.attempts,
        nextAttemptAt = this.nextAttemptAt.toString(),
        lastError = this.lastError,
        createdAt = this.createdAt.toString(),
    )
}
