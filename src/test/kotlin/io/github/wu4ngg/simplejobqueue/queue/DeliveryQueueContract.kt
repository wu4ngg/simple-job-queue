package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Claim
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import io.github.wu4ngg.simplejobqueue.models.MAX_ATTEMPTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class DeliveryQueueContract {
    protected lateinit var queue: DeliveryQueue
    abstract fun newQueue(): DeliveryQueue
    abstract suspend fun createEndpoint(): String
    abstract fun advanceTime(d: Duration)
    abstract suspend fun now(): Instant
    protected val lease: Duration = Duration.ofSeconds(60)

    @BeforeTest
    fun setUpQueue() {
        queue = newQueue()
    }

    @Test
    fun `claimNext never claims a delivery twice`(): Unit = runBlocking {
        val endpointId = createEndpoint()
        (1..1000).forEach {
            queue.enqueue(
                endpointId,
                """
                        {
                            "payload": "payload$it"
                        }
                    """.trimIndent()
            )
        }
        val claimedIds = (1..10).map {
            async(Dispatchers.Default) {
                val resList = mutableListOf<String>()
                while (true) {
                    val claim = queue.claimNext() ?: break
                    val res = claim.delivery
                    resList.add(res.id)
                }
                resList
            }
        }.awaitAll().flatten()
        val uniqueSize = claimedIds.toSet().size
        val actualSize = claimedIds.size
        assertEquals(1000, actualSize)
        assertEquals(1000, uniqueSize)
        val listPending = queue.list(DeliveryStatus.PENDING)
        val listInFlight = queue.list(DeliveryStatus.IN_FLIGHT)
        assertEquals(emptyList(), listPending)
        assertEquals(1000, listInFlight.size)
    }

    @Test
    fun `mark returns false for a delivery that is not claimed`(): Unit = runBlocking {
        val del = queue.enqueue(
            createEndpoint(),
            "{}"
        )
        assertFalse(queue.markSucceeded(Claim(del, UUID.randomUUID())))
        assertFalse(
            queue.markSucceeded(Claim(del.copy(id = UUID.randomUUID().toString()), UUID.randomUUID()))
        )
    }

    @Test
    fun `claimNext doesn't retry immediately after markFailed`(): Unit = runBlocking {
        val del = queue.enqueue(
            createEndpoint(),
            "{}"
        )
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        assertEquals(del.id, claimed.delivery.id)
        val before = now()
        queue.markFailed(claimed, "HTTP 500")
        val after = now()
        val failed = queue.find(del.id)
        assertNotNull(failed)
        assertEquals(del.id, failed.id)
        assertEquals(DeliveryStatus.PENDING, failed.status)
        assertEquals(1, failed.attempts)
        val range = before.plusSeconds(2)..after.plusSeconds(2)
        assertTrue(failed.nextAttemptAt in range, "nextAttemptAt=${failed.nextAttemptAt} not in $range")
        assertEquals("HTTP 500", failed.lastError)
        val claimed0s = queue.claimNext()
        assertNull(claimed0s)
        advanceTime(Duration.ofSeconds(1))
        val claimed1s = queue.claimNext()
        assertNull(claimed1s)
        advanceTime(Duration.ofSeconds(1))
        val claimed2s = queue.claimNext()
        assertEquals(claimed.delivery.id, claimed2s?.delivery?.id)
    }

    @Test
    fun `becomes DEAD after MAX_ATTEMPTS failures`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, "{}")
        assertEquals(DeliveryStatus.PENDING, delivery.status)
        assertEquals(0, delivery.attempts)
        repeat(MAX_ATTEMPTS) {
            val deliveryClaimNext = queue.claimNext()
            assertNotNull(deliveryClaimNext, "Expected delivery after claimNext")
            assertEquals(delivery.id, deliveryClaimNext.delivery.id)
            assertEquals(DeliveryStatus.IN_FLIGHT, deliveryClaimNext.delivery.status)
            queue.markFailed(deliveryClaimNext, "HTTP 500 $it")
            advanceTime(Duration.ofMinutes(1))
        }
        val failed = queue.find(delivery.id)
        assertNotNull(failed)
        assertEquals(DeliveryStatus.DEAD, failed.status)
        assertEquals(MAX_ATTEMPTS, failed.attempts)
        assertEquals("HTTP 500 ${MAX_ATTEMPTS - 1}", failed.lastError)
        advanceTime(Duration.ofHours(1))
        val claimNext = queue.claimNext()
        assertNull(claimNext, "DEAD deliveries are never claimed")
    }

    @Test
    fun `list returns newest first`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery1 = queue.enqueue(endpoint, """{"1": 1}""")
        advanceTime(Duration.ofSeconds(1))
        val delivery2 = queue.enqueue(endpoint, """{"2": 2}""")
        advanceTime(Duration.ofSeconds(1))
        val delivery3 = queue.enqueue(endpoint, """{"3": 3}""")
        val list = queue.list()
        val expected = listOf(delivery3.id, delivery2.id, delivery1.id)
        assertEquals(expected, list.map { it.id })
    }

    @Test
    fun `list filters by status`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val d1 = queue.enqueue(endpoint, """{"1": 1}""")
        val d2 = queue.enqueue(endpoint, """{"2": 2}""")
        val d3 = queue.enqueue(endpoint, """{"3": 3}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        val list = queue.list()
        val pendingList = queue.list(DeliveryStatus.PENDING)
        val inFlightList = queue.list(DeliveryStatus.IN_FLIGHT)
        assertEquals(setOf(d1.id, d2.id, d3.id), list.map { it.id }.toSet())
        assertEquals(
            setOf(d1.id, d2.id, d3.id) - claimed.delivery.id,
            pendingList.map { it.id }.toSet(),
        )
        assertEquals(listOf(claimed.delivery.id), inFlightList.map { it.id })
    }

    @Test
    fun `attempts counts the claim in flight`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, """{"1": 1}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        assertEquals(1, claimed.delivery.attempts)
        queue.markFailed(claimed, "HTTP 500")
        advanceTime(Duration.ofSeconds(2))
        val claimed2 = queue.claimNext()
        assertNotNull(claimed2)
        assertEquals(delivery.id, claimed2.delivery.id)
        assertEquals(2, claimed2.delivery.attempts)
        queue.markSucceeded(claimed2)
        val final = queue.find(delivery.id)
        assertNotNull(final)
        assertEquals(2, final.attempts)
    }

    @Test
    fun `reapExpired never reaps not-yet-expired deliveries`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, """{"1": 1}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        assertEquals(delivery.id, claimed.delivery.id)
        advanceTime(lease - Duration.ofSeconds(1))
        val rows = queue.reapExpired()
        assertEquals(0, rows)
    }

    @Test
    fun `reapExpired reaps expired deliveries`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, """{"1": 1}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        assertEquals(delivery.id, claimed.delivery.id)
        advanceTime(lease)
        val rows = queue.reapExpired()
        assertEquals(1, rows)
        val del = queue.find(delivery.id)
        assertNotNull(del)
        assertEquals(DeliveryStatus.PENDING, del.status)
        assertEquals("lease expired", del.lastError)
        val claimed2 = queue.claimNext()
        assertNotNull(claimed2)
        assertEquals(delivery.id, claimed2.delivery.id)
        assertEquals(2, claimed2.delivery.attempts)
    }

    @Test
    fun `reapExpired put delivery status to DEAD after MAX_ATTEMPTS attempts`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, """{"n": "0"}""")
        repeat(MAX_ATTEMPTS) {
            val claimed = queue.claimNext()
            assertNotNull(claimed)
            advanceTime(lease)
            val rows = queue.reapExpired()
            assertEquals(1, rows)
        }
        val deliveryFinal = queue.find(delivery.id)
        assertNotNull(deliveryFinal)
        assertEquals(MAX_ATTEMPTS, deliveryFinal.attempts)
        assertEquals(DeliveryStatus.DEAD, deliveryFinal.status)
        val claimed2 = queue.claimNext()
        assertNull(claimed2)
    }

    @Test
    fun `stale claim cannot mark a delivery reclaimed by another worker`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, """{"1": 1}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        advanceTime(lease)
        queue.reapExpired()
        val claimed2 = queue.claimNext()
        assertNotNull(claimed2)
        assertEquals(delivery.id, claimed2.delivery.id)
        val res = queue.markFailed(claimed, "HTTP 500")
        assertFalse(res)
        val row = queue.find(delivery.id)
        assertNotNull(row)
        assertEquals(DeliveryStatus.IN_FLIGHT, row.status)
        assertEquals(2, row.attempts)
        val res2 = queue.markSucceeded(claimed2)
        assertTrue(res2)
        val rowFinal = queue.find(delivery.id)
        assertNotNull(rowFinal)
        assertEquals(DeliveryStatus.SUCCEEDED, rowFinal.status)
        val claimed3 = queue.claimNext()
        assertNull(claimed3)
    }

    @Test
    fun `mark with a forged token returns false and leaves the delivery untouched`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        val delivery = queue.enqueue(endpoint, """{"1": 1}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        val claimed2 = Claim(delivery, UUID.randomUUID())
        val res = queue.markSucceeded(claimed2)
        assertFalse(res)
        val row1 = queue.find(delivery.id)
        assertNotNull(row1)
        assertEquals(DeliveryStatus.IN_FLIGHT, row1.status)
        assertEquals(1, row1.attempts)
        val res1 = queue.markFailed(claimed2, "HTTP 500")
        assertFalse(res1)
        val row2 = queue.find(delivery.id)
        assertNotNull(row2)
        assertEquals(DeliveryStatus.IN_FLIGHT, row2.status)
        assertEquals(1, row2.attempts)
        val resReal = queue.markSucceeded(claimed)
        assertTrue(resReal)
        val rowReal = queue.find(delivery.id)
        assertNotNull(rowReal)
        assertEquals(DeliveryStatus.SUCCEEDED, rowReal.status)
        assertEquals(1, rowReal.attempts)
    }

    @Test
    fun `second mark of the same claim returns false`(): Unit = runBlocking {
        val endpoint = createEndpoint()
        queue.enqueue(endpoint, """{"1": 1}""")
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        val markSuccess1 = queue.markSucceeded(claimed)
        val markSuccess2 = queue.markSucceeded(claimed)
        assertTrue(markSuccess1)
        assertFalse(markSuccess2)
        queue.enqueue(endpoint, """{"1": 1}""")
        val claimed1 = queue.claimNext()
        assertNotNull(claimed1)
        val markFail1 = queue.markFailed(claimed1, "FAILED")
        val markFail2 = queue.markFailed(claimed1, "FAILED")
        assertTrue(markFail1)
        assertFalse(markFail2)
    }
}
