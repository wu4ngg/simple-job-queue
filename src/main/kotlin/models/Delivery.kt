package com.example.models

import kotlin.time.Instant

enum class Status {
    PENDING,
    SUCCESS,
    DEAD
}
data class Delivery (val id: String, val endpointId: String, val payload: String, val status: Status, val attempts: Int, val nextAttemptAt: Instant, val lastError: Error?) {
}