package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.UUID

class InMemoryDeliveryQueue(private val clock: Clock = Clock.systemUTC()) : DeliveryQueue {
    private val deliveries = mutableMapOf<String, Delivery>()

    // block concurrent modification/access to deliveries
    private val mutex = Mutex()
    override suspend fun enqueue(endpointId: String, payload: String): Delivery {
        val id: String = UUID.randomUUID().toString()
        val now = Instant.now(clock)
        val delivery = Delivery(
            id = id,
            endpointId = endpointId,
            payload = payload,
            nextAttemptAt = now,
            createdAt = now,
        )
        mutex.withLock {
            deliveries[id] = delivery
        }
        return delivery
    }

    override suspend fun claimNext(): Delivery? {
        val now = Instant.now(clock)
        mutex.withLock {
            val list = deliveries.values
            val claimable = list.filter {
                it.nextAttemptAt <= now && it.status == DeliveryStatus.PENDING
            }
            val earliest = claimable.minByOrNull { it.nextAttemptAt } ?: return null
            // change status
            val newDelivery = earliest.copy(status = DeliveryStatus.IN_FLIGHT)
            deliveries[newDelivery.id] = newDelivery
            return newDelivery
        }
    }

    override suspend fun markSucceeded(id: String) {
        TODO("Not yet implemented")
    }

    override suspend fun markFailed(id: String, error: String) {
        TODO("Not yet implemented")
    }

    override suspend fun find(id: String): Delivery? = mutex.withLock {
        return deliveries[id]
    }

    override suspend fun list(status: DeliveryStatus?): List<Delivery> {
        val list = mutex.withLock {
            deliveries.values.toList()
        }
        if (status != null) {
            return list.filter { it.status == status }
        }
        return list
    }
}