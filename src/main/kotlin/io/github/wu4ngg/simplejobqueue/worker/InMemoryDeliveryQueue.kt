package io.github.wu4ngg.simplejobqueue.worker

import io.github.wu4ngg.simplejobqueue.models.Delivery
import kotlinx.coroutines.sync.Mutex
import java.time.Clock

class InMemoryDeliveryQueue(val clock: Clock = Clock.systemUTC()) : DeliveryQueue {
    private val delivery = mutableMapOf<String, Delivery>()
    private val mutex = Mutex()
    override suspend fun enqueue(deliveryId: String, payload: String) {
        TODO("Not yet implemented")
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