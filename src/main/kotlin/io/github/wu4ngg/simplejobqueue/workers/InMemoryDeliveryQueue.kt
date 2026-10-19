package io.github.wu4ngg.simplejobqueue.workers

import io.github.wu4ngg.simplejobqueue.models.Delivery
import kotlinx.coroutines.sync.Mutex
import java.time.Clock
import java.util.UUID

class InMemoryDeliveryQueue(val clock: Clock = Clock.systemUTC()) : DeliveryQueue {
    private val delivery = mutableMapOf<String, Delivery>()
    private val mutex = Mutex()
    override suspend fun enqueue(endpointId: String, payload: String) {
        val deliveryId = UUID.randomUUID().toString()
    }

    override suspend fun claimNext(): Delivery? {
        TODO("Not yet implemented")
    }

    override suspend fun markSucceeded(id: String) {
        TODO("Not yet implemented")
    }

    override suspend fun markFailed(id: String, error: String) {
        TODO("Not yet implemented")
    }
}