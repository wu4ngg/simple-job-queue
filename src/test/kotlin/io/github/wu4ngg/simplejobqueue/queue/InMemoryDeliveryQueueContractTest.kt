package io.github.wu4ngg.simplejobqueue.queue

import io.github.wu4ngg.simplejobqueue.endpoints.InMemoryEndpointRepository
import java.time.Duration
import java.time.Instant

class InMemoryDeliveryQueueContractTest : DeliveryQueueContract() {
    private val clock = TestClock(now = Instant.parse("2026-01-01T00:00:00Z"))
    private val endpointRepo = InMemoryEndpointRepository()

    override fun newQueue(): DeliveryQueue = InMemoryDeliveryQueue(clock)

    override suspend fun createEndpoint(): String {
        val res = endpointRepo.create("http://localhost:8080/test-receiver")
        return res.id
    }

    override fun advanceTime(d: Duration) {
        clock.advance(d)
    }

    override suspend fun now(): Instant {
        return clock.instant()
    }
}
