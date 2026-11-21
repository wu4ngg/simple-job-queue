package io.github.wu4ngg.simplejobqueue.repositories

import io.github.wu4ngg.simplejobqueue.models.Endpoint

interface EndpointRepository {
    suspend fun create(url: String): Endpoint
    suspend fun get(id: String): Endpoint?
}