package io.github.wu4ngg.simplejobqueue.models

import kotlin.time.Instant

enum class Status {
    PENDING,
    SUCCESS,
    DEAD
}
data class Delivery (val id: String, val endpointId: String, val payload: String, val status: Status = Status.PENDING, val attempts: Int = 0, val nextAttemptAt: Instant, val lastError: String? = null, val createdAt: Instant) {
}