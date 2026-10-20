package io.github.wu4ngg.simplejobqueue.models

import java.time.Instant

enum class DeliveryStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    DEAD,
}

data class Delivery(
    val id: String,
    val endpointId: String,
    val payload: String,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: Instant,
    val lastError: String? = null,
    val createdAt: Instant
)