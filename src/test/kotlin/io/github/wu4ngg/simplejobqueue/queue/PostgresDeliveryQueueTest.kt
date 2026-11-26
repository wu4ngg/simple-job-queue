package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.db.TestDatabase
import io.github.wu4ngg.simplejobqueue.endpoints.EndpointNotFoundException
import io.github.wu4ngg.simplejobqueue.endpoints.PostgresEndpointRepository
import io.github.wu4ngg.simplejobqueue.models.Claim
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import io.github.wu4ngg.simplejobqueue.models.toCode
import kotlinx.coroutines.runBlocking
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresDeliveryQueueTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    val lease = Duration.ofSeconds(60)

    @Test
    fun `enqueue then find returns the stored delivery`(): Unit = runBlocking {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        val queue = PostgresDeliveryQueue(TestDatabase.ds, lease)
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
        val queue = PostgresDeliveryQueue(TestDatabase.ds, lease)
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
        val queue = PostgresDeliveryQueue(TestDatabase.ds, lease)
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
        val queue = PostgresDeliveryQueue(TestDatabase.ds, lease)
        val realDelivery = queue.enqueue(endpointId = endpoint.id, payload = "{\"a\":\"0\"}")
        assertFailsWith<IllegalArgumentException> {
            queue.find("nope")
        }
        assertFailsWith<IllegalArgumentException> {
            queue.markSucceeded(Claim(realDelivery.copy(id = "nope"), UUID.randomUUID()))
        }
        assertFailsWith<IllegalArgumentException> {
            queue.enqueue("nope", "{}")
        }
        assertFailsWith<IllegalArgumentException> {
            queue.markFailed(Claim(realDelivery.copy(id = "nope"), UUID.randomUUID()), "nope")
        }
    }

    private data class LeaseRow(val token: UUID?, val leaseExpiresAt: Instant?)

    private fun readLease(deliveryId: UUID): LeaseRow? {
        val sql = """
            SELECT lease_expires_at, claim_token
            FROM deliveries
            WHERE id = ?
        """.trimIndent()
        return TestDatabase.ds.connection.use { c ->
            c.prepareStatement(sql).use { st ->
                st.setObject(1, deliveryId)
                st.executeQuery().use { rs ->
                    if (rs.next()) {
                        LeaseRow(
                            token = rs.getObject("claim_token", UUID::class.java),
                            leaseExpiresAt = rs.getObject("lease_expires_at", OffsetDateTime::class.java)?.toInstant()
                        )
                    } else null
                }
            }
        }
    }

    private fun getDbNow(): Instant {
        val sql = """
            SELECT NOW()
        """.trimIndent()
        return TestDatabase.ds.connection.use { c ->
            c.prepareStatement(sql).use { st ->
                st.executeQuery().use { rs ->
                    check(rs.next()) {
                        "SELECT statement returned no rows"
                    }

                    rs.getObject(1, OffsetDateTime::class.java).toInstant()
                }
            }
        }
    }

    @Test
    fun `claim_token and lease_expires_at are saved after claim`(): Unit = runBlocking {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        val queue = PostgresDeliveryQueue(TestDatabase.ds, lease)
        val endpoint = endpointRepo.create("http://localhost:8080/test-receiver")
        val delivery = queue.enqueue(endpointId = endpoint.id, payload = "{\"a\":\"0\"}")
        val before = getDbNow()
        val claim = queue.claimNext()
        val after = getDbNow()
        assertNotNull(claim)
        val leaseRow = readLease(UUID.fromString(delivery.id))
        assertNotNull(leaseRow)
        assertEquals(claim.token, leaseRow.token)
        assertNotNull(leaseRow.leaseExpiresAt)
        assertTrue(leaseRow.leaseExpiresAt in (before.plus(lease))..(after.plus(lease)))
    }

    @Test
    fun `claim_token and lease_expires at are NULL after mark`(): Unit = runBlocking {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        val queue = PostgresDeliveryQueue(TestDatabase.ds, lease)
        val endpoint = endpointRepo.create("http://localhost:8080/test-receiver")
        val delivery = queue.enqueue(endpointId = endpoint.id, payload = "{\"a\":\"0\"}")
        val delivery1 = queue.enqueue(endpointId = endpoint.id, payload = "{\"a\":\"1\"}")
        val claim = queue.claimNext()
        assertNotNull(claim)
        val claim1 = queue.claimNext()
        assertNotNull(claim1)
        queue.markSucceeded(claim)
        val row = readLease(UUID.fromString(claim.delivery.id))
        assertNotNull(row)
        assertNull(row.token)
        assertNull(row.leaseExpiresAt)
        queue.markFailed(claim1, "test")
        val row1 = readLease(UUID.fromString(claim1.delivery.id))
        assertNotNull(row1)
        assertNull(row1.token)
        assertNull(row1.leaseExpiresAt)
    }
}
