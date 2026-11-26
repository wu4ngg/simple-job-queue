package io.github.wu4ngg.simplejobqueue.models

import java.time.Instant
import java.util.UUID

const val MAX_ATTEMPTS = 5

enum class DeliveryStatus {

    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    DEAD;

    companion object {
        fun fromCode(raw: String): DeliveryStatus? = entries.find { it.toCode() == raw }
    }
}

data class Delivery(
    val id: String,
    val endpointId: String,
    val payload: String,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    /**
     * Number of delivery attempts started, IN_FLIGHT deliveries included
     * Starts at 0 and incremented when the delivery is claimed, NOT in markSucceeded and markFailed
     */
    val attempts: Int = 0,
    val nextAttemptAt: Instant,
    val lastError: String? = null,
    val createdAt: Instant,
)

data class Claim(
    val delivery: Delivery,
    val token: UUID,
)

/**
 * Wire and storage code. Also persisted in `deliveries.status` and its CHECK constraint:
 * changing a value need a migration
 */
fun DeliveryStatus.toCode(): String = when (this) {
    DeliveryStatus.DEAD -> "dead"
    DeliveryStatus.IN_FLIGHT -> "inflight"
    DeliveryStatus.SUCCEEDED -> "succeeded"
    DeliveryStatus.PENDING -> "pending"
}