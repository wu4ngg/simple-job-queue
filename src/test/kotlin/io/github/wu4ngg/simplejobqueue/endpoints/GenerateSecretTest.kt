package io.github.wu4ngg.simplejobqueue.endpoints

import io.github.wu4ngg.simplejobqueue.models.Endpoint
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateSecretTest {
    @Test
    fun `generateSecret generates jq_ plus 32 random bytes in base64url`() {
        val secret = generateSecret()
        val regex = Regex("jq_[A-Za-z0-9_-]{43}")
        assertTrue(regex.matches(secret.value))
    }

    @Test
    fun `generated secrets are distinct`() {
        val secrets = List(100) { generateSecret() }
        assertEquals(100, secrets.toSet().size)
    }

    @Test
    fun `endpoint toString does not leak secret`() {
        val secret = generateSecret()
        val endpoint = Endpoint(
            id = UUID.randomUUID().toString(),
            url = "https://example.com",
            secret = secret
        )
        val text = endpoint.toString()
        assertFalse(secret.value in text, "Endpoint.toString leaks the secret")
        assertContains(text, "secret=Secret(***)", message = "secret not obscured in Endpoint.toString")
    }
}
