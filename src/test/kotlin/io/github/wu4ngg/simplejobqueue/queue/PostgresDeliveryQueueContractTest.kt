package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.db.TestDatabase
import io.github.wu4ngg.simplejobqueue.endpoints.PostgresEndpointRepository
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

class PostgresDeliveryQueueContractTest : DeliveryQueueContract() {
    override fun newQueue(): DeliveryQueue {
        TestDatabase.reset()
        return PostgresDeliveryQueue(TestDatabase.ds, lease)
    }

    override suspend fun createEndpoint(): String {
        val endpointRepo = PostgresEndpointRepository(TestDatabase.ds)
        return endpointRepo.create("http://localhost:8080/test-receiver").id
    }

    override fun advanceTime(d: Duration) {
        val sql = """
            UPDATE deliveries 
            SET next_attempt_at  = next_attempt_at - make_interval(secs => ?), 
                lease_expires_at = lease_expires_at - make_interval(secs => ?)
        """.trimIndent()
        TestDatabase.ds.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setDouble(1, d.toMillis() / 1000.0)
                statement.setDouble(2, d.toMillis() / 1000.0)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun now(): Instant {
        val sql = "SELECT now()"
        return TestDatabase.ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    check(rs.next()) {
                        "SELECT statement returned no rows"
                    }
                    rs.getObject(1, OffsetDateTime::class.java).toInstant()
                }
            }
        }
    }
}
