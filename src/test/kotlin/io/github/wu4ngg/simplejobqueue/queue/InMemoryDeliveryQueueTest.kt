package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(d: Duration) {
        now = now.plus(d)
    }
}

class InMemoryDeliveryQueueTest {
    @Test
    fun `claimNext never claim a delivery twice`(): Unit = runBlocking {
        val queue = InMemoryDeliveryQueue()
        (1..1000).forEach {
            queue.enqueue(
                it.toString(),
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
        assertTrue { listPending.isEmpty() }
        assertEquals(1000, listInFlight.size)
    }

    @Test
    fun `markSucceeded throws on an unclaimed or unknown delivery`(): Unit = runBlocking {
        val queue = InMemoryDeliveryQueue()
        val del = queue.enqueue(
            "success",
            "{}"
        )
        assertFailsWith<IllegalStateException> {
            queue.markSucceeded(del.id)
        }
        assertFailsWith<IllegalStateException> {
            queue.markSucceeded("nope")
        }
    }

    @Test
    fun `claimNext doesn't retry immediately after markFailed`(): Unit = runBlocking {
        val clock = TestClock(now = Instant.parse("2026-01-01T00:00:00Z"))
        val queue = InMemoryDeliveryQueue(clock)
        val del = queue.enqueue(
            "failure",
            "{}"
        )
        val claimed = queue.claimNext()
        assertNotNull(claimed)
        assertEquals(del.id, claimed.id)
        val start = clock.instant()
        queue.markFailed(del.id, "HTTP 500")
        val failed = queue.find(del.id)
        assertNotNull(failed)
        assertEquals(del.id, failed.id)
        assertEquals(DeliveryStatus.PENDING, failed.status)
        assertEquals(1, failed.attempts)
        assertEquals(start.plus(Duration.ofSeconds(2)), failed.nextAttemptAt)
        assertEquals("HTTP 500", failed.lastError)
        val claimed0s = queue.claimNext()
        assertNull(claimed0s)
        clock.advance(Duration.ofSeconds(1))
        val claimed1s = queue.claimNext()
        assertNull(claimed1s)
        clock.advance(Duration.ofSeconds(1))
        val claimed2s = queue.claimNext()
        assertEquals(claimed.id, claimed2s?.id)
    }
}