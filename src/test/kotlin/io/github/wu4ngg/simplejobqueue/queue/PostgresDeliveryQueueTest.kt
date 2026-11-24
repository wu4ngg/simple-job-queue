package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.db.TestDatabase
import io.github.wu4ngg.simplejobqueue.endpoints.PostgresEndpointRepository
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class PostgresDeliveryQueueTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `enqueue then find returns the stored delivery`(): Unit = runBlocking {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        val queue = PostgresDeliveryQueue(TestDatabase.ds)
        val endpoint = endpointRepo.create("http://localhost:8080/test-receiver")
        val payload = "{\"a\":\"0\"}"
        val delivery =
            queue.enqueue(endpointId = endpoint.id, payload = payload)
        val found = queue.find(delivery.id)
        assertEquals(endpoint.id, delivery.endpointId)
        assertEquals(payload, delivery.payload)
        assertEquals(DeliveryStatus.PENDING, delivery.status)
        assertEquals(0, delivery.attempts)
        assertEquals(null, delivery.lastError)
        assertEquals(delivery, found)
    }
}
