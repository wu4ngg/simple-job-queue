package io.github.wu4ngg.simplejobqueue.workers

import io.github.wu4ngg.simplejobqueue.models.Delivery

interface DeliveryQueue {
    suspend fun enqueue(endpointId: String, payload: String)
    suspend fun claimNext(): Delivery?
    suspend fun markSucceeded(id: String)
    suspend fun markFailed(id: String, error: String)
}