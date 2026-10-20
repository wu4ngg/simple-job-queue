package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus

interface DeliveryQueue {
    suspend fun enqueue(endpointId: String, payload: String): Delivery
    suspend fun claimNext(): Delivery?
    suspend fun markSucceeded(id: String)
    suspend fun markFailed(id: String, error: String)
    suspend fun find(id: String): Delivery?
    suspend fun list(status: DeliveryStatus? = null): List<Delivery>
}