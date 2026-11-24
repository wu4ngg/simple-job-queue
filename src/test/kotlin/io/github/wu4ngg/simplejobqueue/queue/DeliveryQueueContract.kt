package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Delivery
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class DeliveryQueueContract {
    protected lateinit var queue: DeliveryQueue
    abstract fun newQueue(): DeliveryQueue
    abstract suspend fun createEndpoint(): String
    abstract fun advanceTime(d: Duration)
    abstract suspend fun now(): Instant

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
                    val res = queue.claimNext() ?: break
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
    fun `markSucceeded throws on an unclaimed or unknown delivery`(): Unit = runBlocking {
        val del = queue.enqueue(
            createEndpoint(),
            "{}"
        )
        assertFailsWith<IllegalStateException> {
            queue.markSucceeded(del.id)
        }
        assertFailsWith<IllegalStateException> {
            queue.markSucceeded(UUID.randomUUID().toString())
        }
    }

    @Test
    fun `claimNext doesn't retry immediately after markFailed`(): Unit = runBlocking {
        val del = queue.enqueue(
            createEndpoint(),
            "{}"
        )
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        assertEquals(del.id, claimed.id)
        val before = now()
        queue.markFailed(del.id, "HTTP 500")
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
        assertEquals(claimed.id, claimed2s?.id)
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
            assertEquals(delivery.id, deliveryClaimNext.id)
            assertEquals(DeliveryStatus.IN_FLIGHT, deliveryClaimNext.status)
            queue.markFailed(deliveryClaimNext.id, "HTTP 500 $it")
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
            setOf(d1.id, d2.id, d3.id) - claimed.id,
            pendingList.map { it.id }.toSet(),
        )
        assertEquals(listOf(claimed.id), inFlightList.map { it.id })
    }
}