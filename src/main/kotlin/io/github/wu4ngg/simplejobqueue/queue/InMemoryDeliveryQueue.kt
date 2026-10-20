package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InMemoryDeliveryQueue(private val clock: Clock = Clock.systemUTC()) : DeliveryQueue {
    private val deliveries = mutableMapOf<String, Delivery>()


    // block concurrent modification/access to deliveries (shared state)
    private val mutex = Mutex()

    // caller must hold mutex
    private fun requireInflight(id: String): Delivery {
        val delivery = deliveries[id]
        checkNotNull(delivery) { "Delivery not found: $id" }
        check(delivery.status == DeliveryStatus.IN_FLIGHT) { "Invalid delivery status: $id" }
        return delivery
    }

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

    override suspend fun markSucceeded(id: String) = mutex.withLock {
        val delivery = requireInflight(id)
        val nextAttempt = delivery.attempts + 1
        val nextStatus = DeliveryStatus.SUCCEEDED
        val newDelivery = delivery.copy(status = nextStatus, attempts = nextAttempt)
        deliveries[id] = newDelivery
    }

    override suspend fun markFailed(id: String, error: String) = mutex.withLock {
        val now = Instant.now(clock)
        val delivery = requireInflight(id)
        val nextAttempt = delivery.attempts + 1
        val nextStatus = if (nextAttempt >= MAX_ATTEMPTS) DeliveryStatus.DEAD else DeliveryStatus.PENDING
        val nextSeconds = 1L shl nextAttempt
        val nextAttemptAt = now.plus(Duration.ofSeconds(nextSeconds))
        val newDelivery =
            delivery.copy(status = nextStatus, nextAttemptAt = nextAttemptAt, attempts = nextAttempt, lastError = error)
        deliveries[id] = newDelivery

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

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}