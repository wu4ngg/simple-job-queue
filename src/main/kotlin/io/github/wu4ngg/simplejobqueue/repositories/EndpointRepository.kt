package io.github.wu4ngg.simplejobqueue.repositories

import io.github.wu4ngg.simplejobqueue.models.Endpoint
import io.github.wu4ngg.simplejobqueue.models.Secret
import java.util.UUID
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

interface EndpointRepository {
    suspend fun create(url: String): Endpoint
    suspend fun get(id: String): Endpoint?
}

class InMemoryEndpointRepository : EndpointRepository {
    private val endpoints = ConcurrentHashMap<String, Endpoint>()
    private val random = SecureRandom()
    override suspend fun create(url: String): Endpoint {
        val id = UUID.randomUUID().toString()
        // 32 bytes = 256-bit HMAC-SHA256 Secret key.
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val secretRaw =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val secret = Secret("jq_$secretRaw")
        val endpoint = Endpoint(id, url, secret)
        endpoints[id] = endpoint
        return endpoint
    }

    override suspend fun get(id: String): Endpoint? = endpoints[id]
}