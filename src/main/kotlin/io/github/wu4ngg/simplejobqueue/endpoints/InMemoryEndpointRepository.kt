package io.github.wu4ngg.simplejobqueue.endpoints

import io.github.wu4ngg.simplejobqueue.models.Endpoint
import io.github.wu4ngg.simplejobqueue.models.Secret
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val random = SecureRandom()

fun generateSecret(): Secret {
    // 32 bytes = 256-bit HMAC-SHA256 Secret key.
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    val secretRaw =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    val secret = Secret("jq_$secretRaw")
    return secret
}

class InMemoryEndpointRepository : EndpointRepository {
    private val endpoints = ConcurrentHashMap<String, Endpoint>()
    override suspend fun create(url: String): Endpoint {
        val id = UUID.randomUUID().toString()
        // 32 bytes = 256-bit HMAC-SHA256 Secret key.

        val secret = generateSecret()
        val endpoint = Endpoint(id, url, secret)
        endpoints[id] = endpoint
        return endpoint
    }

    override suspend fun get(id: String): Endpoint? = endpoints[id]
}