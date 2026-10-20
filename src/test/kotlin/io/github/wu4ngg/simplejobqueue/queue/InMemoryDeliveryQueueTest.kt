package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.models.Delivery
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        val res2 = (1..10).map {
            async(Dispatchers.Default) {
                val resList = mutableListOf<String>()
                while (true) {
                    val res = queue.claimNext() ?: break
                    resList.add(res.id)
                }
                resList
            }
        }.awaitAll().flatten()
        val uniqueSize = res2.toSet().size
        val actualSize = res2.size
        assertEquals(1000, actualSize)
        assertEquals(1000, uniqueSize)
        val listPending = queue.list(DeliveryStatus.PENDING)
        val listInFlight = queue.list(DeliveryStatus.IN_FLIGHT)
        assertTrue { listPending.isEmpty() }
        assertEquals(1000, listInFlight.size)
    }

    @Test
    fun `markSuccess throws on an unclaimed or unknown delivery`(): Unit = runBlocking {
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
}