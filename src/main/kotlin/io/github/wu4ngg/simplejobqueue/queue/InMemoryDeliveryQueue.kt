package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import kotlinx.coroutines.sync.Mutex
import java.time.Clock

class InMemoryDeliveryQueue(private val clock: Clock = Clock.systemUTC()) : DeliveryQueue {
    private val deliveries = mutableMapOf<String, Delivery>()
    private val mutex = Mutex()
    override suspend fun enqueue(endpointId: String, payload: String): Delivery {
        TODO()
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

    override suspend fun find(id: String): Delivery? {
        TODO("Not yet implemented")
    }

    override suspend fun list(status: DeliveryStatus?): List<Delivery> {
        TODO("Not yet implemented")
    }
}