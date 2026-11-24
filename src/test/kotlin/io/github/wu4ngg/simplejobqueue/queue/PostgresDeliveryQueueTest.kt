package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.db.TestDatabase
import io.github.wu4ngg.simplejobqueue.endpoints.EndpointNotFoundException
import io.github.wu4ngg.simplejobqueue.endpoints.PostgresEndpointRepository
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import io.github.wu4ngg.simplejobqueue.models.toCode
import kotlinx.coroutines.runBlocking
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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

    @Test
    fun `enqueue unknown endpoint throws EndpointNotFoundException with cause SQLException`(): Unit = runBlocking {
        val queue = PostgresDeliveryQueue(TestDatabase.ds)
        val ex = assertFailsWith<EndpointNotFoundException> {
            queue.enqueue(UUID.randomUUID().toString(), "{}")
        }
        assertEquals(emptyList(), queue.list())
        assertIs<SQLException>(ex.cause)
        assertEquals("23503", (ex.cause as SQLException).sqlState)
    }

    @Test
    fun `all DeliveryStatuses passes SQL CHECK constraint`(): Unit = runBlocking {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        val endpoint = endpointRepo.create("http://localhost:8080/test-receiver")
        val queue = PostgresDeliveryQueue(TestDatabase.ds)
        val delivery = queue.enqueue(endpointId = endpoint.id, payload = "{\"a\":\"0\"}")
        val sql = """
            UPDATE deliveries
            SET status=?
            WHERE id=?
        """.trimIndent()
        for (it in DeliveryStatus.entries) {
            TestDatabase.ds.connection.use { c ->
                val updated = c.prepareStatement(sql).use { statement ->
                    statement.setString(1, it.toCode())
                    statement.setObject(2, UUID.fromString(delivery.id))
                    statement.executeUpdate()
                }
                assertEquals(1, updated)
            }
            assertEquals(it, queue.find(delivery.id)?.status)
        }
    }

    @Test
    fun `invalid id returns IllegalArgumentException`(): Unit = runBlocking {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        val endpoint = endpointRepo.create("http://localhost:8080/test-receiver")
        val queue = PostgresDeliveryQueue(TestDatabase.ds)
        assertFailsWith<IllegalArgumentException> {
            queue.find("nope")
        }
        assertFailsWith<IllegalArgumentException> {
            queue.markSucceeded("nope")
        }
        assertFailsWith<IllegalArgumentException> {
            queue.enqueue("nope", "{}")
        }
        assertFailsWith<IllegalArgumentException> {
            queue.markFailed("nope", "npe")
        }
    }
}
