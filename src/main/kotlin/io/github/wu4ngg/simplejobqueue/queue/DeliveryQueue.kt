package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Claim
import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus

interface DeliveryQueue {
    suspend fun enqueue(endpointId: String, payload: String): Delivery
    suspend fun claimNext(): Claim?
    suspend fun markSucceeded(claim: Claim)
    suspend fun markFailed(claim: Claim, error: String)
    /**
     * Mark inflight deliveries as PENDING/DEAD whose lease are expired and returns row counts of those deliveries
     */
    suspend fun reapExpired(): Int
    /**
     * id must be a valid UUID; implementations may throw otherwise
     */
    suspend fun find(id: String): Delivery?

    /**
     * Returns a list of deliveries ordered by [Delivery.createdAt], newest first.
     *
     * @param status only returns deliveries in this status. `null` returns all
     */
    suspend fun list(status: DeliveryStatus? = null): List<Delivery>
}
