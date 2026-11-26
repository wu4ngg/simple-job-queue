package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Claim
import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import io.github.wu4ngg.simplejobqueue.models.MAX_ATTEMPTS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
private data class Lease (
    val token: UUID,
    val expiresAt: Instant,
)
class InMemoryDeliveryQueue(private val clock: Clock = Clock.systemUTC(), private val lease: Duration) : DeliveryQueue {
    private val deliveries = mutableMapOf<String, Delivery>()
    private val deliveryClaimTokens = mutableMapOf<String, Lease>()

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

    override suspend fun claimNext(): Claim? {
        val now = Instant.now(clock)
        val claimToken = UUID.randomUUID()
        mutex.withLock {
            val expiration = now.plus(lease)
            val list = deliveries.values
            val claimable = list.filter {
                it.nextAttemptAt <= now && it.status == DeliveryStatus.PENDING
            }
            val earliest = claimable.minByOrNull { it.nextAttemptAt } ?: return null
            // change status
            val newDelivery = earliest.copy(status = DeliveryStatus.IN_FLIGHT, attempts = earliest.attempts + 1)
            deliveries[newDelivery.id] = newDelivery
            deliveryClaimTokens[newDelivery.id] = Lease(claimToken, expiration)
            return Claim(delivery = newDelivery, token = claimToken)
        }
    }

    override suspend fun markSucceeded(claim: Claim) = mutex.withLock {
        val deliveryId = claim.delivery.id
        val delivery = requireInflight(deliveryId)
        val attempt = delivery.attempts
        val nextStatus = DeliveryStatus.SUCCEEDED
        val newDelivery = delivery.copy(status = nextStatus)
        deliveryClaimTokens.remove(deliveryId)
        deliveries[deliveryId] = newDelivery
    }

    override suspend fun markFailed(claim: Claim, error: String) = mutex.withLock {
        val deliveryId = claim.delivery.id
        val now = Instant.now(clock)
        val delivery = requireInflight(deliveryId)
        val attempt = delivery.attempts
        val nextStatus = if (attempt >= MAX_ATTEMPTS) DeliveryStatus.DEAD else DeliveryStatus.PENDING
        val nextSeconds = 1L shl attempt
        val nextAttemptAt = now.plus(Duration.ofSeconds(nextSeconds))
        val newDelivery =
            delivery.copy(status = nextStatus, nextAttemptAt = nextAttemptAt, attempts = attempt, lastError = error)
        deliveryClaimTokens.remove(deliveryId)
        deliveries[deliveryId] = newDelivery
    }

    override suspend fun reapExpired(): Int = mutex.withLock {
        val expired = deliveries.values.filter {
            val lease = deliveryClaimTokens[it.id]
            it.status == DeliveryStatus.IN_FLIGHT &&
                         lease != null &&
                         lease.expiresAt <= Instant.now(clock)
        }
        val newDeliveries = expired.map {
            val status = if(it.attempts >= MAX_ATTEMPTS) DeliveryStatus.DEAD else DeliveryStatus.PENDING
            it.copy(status = status, lastError = "lease expired")
        }
        for (delivery in newDeliveries) {
            deliveries[delivery.id] = delivery
            deliveryClaimTokens.remove(delivery.id)
        }
        return newDeliveries.size
    }

    override suspend fun find(id: String): Delivery? = mutex.withLock {
        return deliveries[id]
    }


    override suspend fun list(status: DeliveryStatus?): List<Delivery> {
        val list = mutex.withLock {
            deliveries.values.toList()
        }
        return list
            .filter { status == null || it.status == status }
            .sortedWith(compareByDescending<Delivery> { it.createdAt }.thenByDescending { it.id })

    }
}
