package io.github.wu4ngg.simplejobqueue.worker

import io.github.wu4ngg.simplejobqueue.queue.DeliveryQueue
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import kotlin.time.Duration

class DeliveryWorker(
    private val queue: DeliveryQueue,
    private val endpoints: EndpointRepository,
    private val client: HttpClient,
    private val instanceId: String,
) {
    private val log = LoggerFactory.getLogger(DeliveryWorker::class.java)
    suspend fun processNext(): Boolean {
        val claim = queue.claimNext() ?: return false
        val delivery = claim.delivery
        log.info("worker ${instanceId.take(8)} --> claiming ${delivery.endpointId}: (id: ${delivery.id}, attempt #${delivery.attempts})")
        val endpoint = endpoints.get(delivery.endpointId)
        if (endpoint == null) {
            queue.markFailed(claim, "Endpoint not found")
            return true
        }
        val result: HttpResponse = try {
            client.post(endpoint.url) {
                contentType(ContentType.Application.Json)
                setBody(delivery.payload)
                header("X-Delivery-Id", delivery.id)
                header("X-Delivery-Attempt", delivery.attempts)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            queue.markFailed(claim, "${ex::class.simpleName}: ${ex.message}")
            return true
        }
        if (result.status.isSuccess()) {
            queue.markSucceeded(claim)
        } else {
            queue.markFailed(claim, "HTTP ${result.status.value}")
        }
        return true
    }

    suspend fun run(idleDelay: Duration) {
        log.info("Starting worker ${instanceId.take(8)}")
        var willDelay: Boolean
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val process = processNext()
                    willDelay = !process
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    log.error("Exception occurred while processing delivery", ex)
                    willDelay = true
                }
                if (willDelay) {
                    delay(idleDelay)
                }
            }
        } finally {
            log.info("Ending worker ${instanceId.take(8)}")
        }

    }
}
