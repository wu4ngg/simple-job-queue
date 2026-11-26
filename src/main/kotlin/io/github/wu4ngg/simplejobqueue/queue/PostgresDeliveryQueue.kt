package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.db.withConnection
import io.github.wu4ngg.simplejobqueue.endpoints.EndpointNotFoundException
import io.github.wu4ngg.simplejobqueue.models.Claim
import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import io.github.wu4ngg.simplejobqueue.models.MAX_ATTEMPTS
import io.github.wu4ngg.simplejobqueue.models.toCode
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

private const val DELIVERY_COLUMNS =
    "id, endpoint_id, payload, status, attempts, next_attempt_at, last_error, created_at"
private const val FOREIGN_KEY_VIOLATION = "23503"

class PostgresDeliveryQueue(private val ds: DataSource, private val lease: Duration) : DeliveryQueue {
    private fun ResultSet.toDelivery() = Delivery(
        id = getObject("id", UUID::class.java).toString(),
        payload = getString("payload"),
        status = getString("status").let { DeliveryStatus.fromCode(it) ?: error("Unknown DeliveryStatus: '$it'") },
        lastError = getString("last_error"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        nextAttemptAt = getObject("next_attempt_at", OffsetDateTime::class.java).toInstant(),
        endpointId = getObject("endpoint_id", UUID::class.java).toString(),
        attempts = getInt("attempts"),
    )

    override suspend fun enqueue(
        endpointId: String,
        payload: String
    ): Delivery {
        val id = UUID.randomUUID()
        val sql = """
            INSERT INTO deliveries
            (id, endpoint_id, payload, status, attempts, next_attempt_at, created_at)
            VALUES (?, ?, ?, ?, 0, NOW(), NOW()) RETURNING $DELIVERY_COLUMNS
        """.trimIndent()
        return try {
            ds.withConnection { con ->
                con.prepareStatement(sql).use { ps ->
                    ps.setObject(1, id)
                    ps.setObject(2, UUID.fromString(endpointId))
                    ps.setString(3, payload)
                    ps.setString(4, DeliveryStatus.PENDING.toCode())
                    ps.executeQuery().use { rs ->
                        check(rs.next()) { "INSERT ... RETURNING returned no row" }
                        rs.toDelivery()
                    }
                }
            }
        } catch (ex: SQLException) {
            if (ex.sqlState == FOREIGN_KEY_VIOLATION) {
                throw EndpointNotFoundException(endpointId, ex)
            } else {
                throw ex
            }
        }
    }

    override suspend fun claimNext(): Claim? {
        val claimToken = UUID.randomUUID()
        val sql = """
            UPDATE deliveries SET 
                status = '${DeliveryStatus.IN_FLIGHT.toCode()}', 
                claim_token = ?, 
                attempts = attempts + 1,
                lease_expires_at = now() + make_interval(secs => ?)
            WHERE id = (
                SELECT id FROM deliveries
                WHERE status = '${DeliveryStatus.PENDING.toCode()}' AND next_attempt_at <= now()
                ORDER BY next_attempt_at ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            ) RETURNING $DELIVERY_COLUMNS
        """.trimIndent()
        val res = ds.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, claimToken)
                ps.setDouble(2, lease.toMillis() / 1000.0)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.toDelivery()
                    } else null
                }
            }
        }
        return res?.let { Claim(it, claimToken) }
    }

    override suspend fun markSucceeded(claim: Claim) {
        val uuid = UUID.fromString(claim.delivery.id)
        val sql = """
            UPDATE deliveries
            SET status           = '${DeliveryStatus.SUCCEEDED.toCode()}',                 
                claim_token      = NULL,
                lease_expires_at = NULL
            WHERE status = '${DeliveryStatus.IN_FLIGHT.toCode()}' AND id = ?
        """.trimIndent()
        val updated = ds.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, uuid)
                ps.executeUpdate()
            }
        }
        check(updated == 1) { "Delivery '${uuid}' is not inflight" }
    }

    override suspend fun markFailed(claim: Claim, error: String) {
        val uuid = UUID.fromString(claim.delivery.id)
        val sql = """
            UPDATE deliveries
                SET status       = CASE WHEN attempts >= ?
                                   THEN '${DeliveryStatus.DEAD.toCode()}'
                                   ELSE '${DeliveryStatus.PENDING.toCode()}'
                                   END,
                next_attempt_at  = now() + make_interval(secs => power(2, attempts)),
                claim_token      = NULL,
                lease_expires_at = NULL,
                last_error       = ?
            WHERE status = '${DeliveryStatus.IN_FLIGHT.toCode()}' AND id = ?
        """.trimIndent()
        val updated = ds.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, MAX_ATTEMPTS)
                ps.setString(2, error)
                ps.setObject(3, uuid)
                ps.executeUpdate()
            }
        }
        check(updated == 1) { "Delivery '$uuid' is not inflight" }
    }

    override suspend fun reapExpired(): Int {
        val sql = """
            UPDATE deliveries
            SET status              = CASE WHEN attempts >= ?
                                      THEN '${DeliveryStatus.DEAD.toCode()}'
                                      ELSE '${DeliveryStatus.PENDING.toCode()}'
                                      END,
                 lease_expires_at   = NULL,
                 claim_token        = NULL,
                 last_error         = 'lease expired'
            WHERE lease_expires_at <= now() AND status = '${DeliveryStatus.IN_FLIGHT.toCode()}'
        """.trimIndent()
        val res = ds.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, MAX_ATTEMPTS)
                ps.executeUpdate()
            }
        }
        return res
    }

    override suspend fun find(id: String): Delivery? {
        val uuid = UUID.fromString(id)
        val sql = """
            SELECT $DELIVERY_COLUMNS FROM deliveries WHERE id = ?
        """.trimIndent()
        return ds.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, uuid)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.toDelivery()
                    } else null
                }
            }
        }
    }

    override suspend fun list(status: DeliveryStatus?): List<Delivery> {
        val select = """
            SELECT $DELIVERY_COLUMNS FROM deliveries
        """
        val order = """
            ORDER BY created_at DESC, id DESC
        """
        val where = """
            WHERE status = ?
        """
        val sql = if (status == null) "$select $order" else "$select $where $order"
        return ds.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                if (status != null) {
                    ps.setString(1, status.toCode())
                }
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toDelivery()) }
                }
            }
        }
    }
}
