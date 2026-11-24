package io.github.wu4ngg.simplejobqueue.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeliveryStatusTest {
    @Test
    fun `toCode returns value stored in DB`() {
        val statusMap = mapOf(
            DeliveryStatus.PENDING to "pending",
            DeliveryStatus.DEAD to "dead",
            DeliveryStatus.IN_FLIGHT to "inflight",
            DeliveryStatus.SUCCEEDED to "succeeded"
        )
        val associatedStatuses = DeliveryStatus.entries.associateWith { it.toCode() }
        assertEquals(statusMap, associatedStatuses)
    }

    @Test
    fun `fromCode reverses toCode for every status`() {
        for (status in DeliveryStatus.entries) {
            val code = status.toCode()
            val reversed = DeliveryStatus.fromCode(code)
            assertEquals(status, reversed)
        }
    }

    @Test
    fun `fromCode returns null for unknown status`() {
        val status = DeliveryStatus.fromCode("bogus")
        assertNull(status)
    }

    @Test
    fun `fromCode is case-sensitive`() {
        val status = DeliveryStatus.fromCode("PENDING")
        assertNull(status)
    }
}