package io.github.wu4ngg.simplejobqueue.dtos

import kotlinx.serialization.Serializable

@Serializable
data class EnqueueEventResponse(val deliveryId: String) {
}