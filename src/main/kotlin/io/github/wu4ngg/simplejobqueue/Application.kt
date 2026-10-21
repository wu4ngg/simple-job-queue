package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.queue.DeliveryQueue
import io.github.wu4ngg.simplejobqueue.queue.InMemoryDeliveryQueue
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.github.wu4ngg.simplejobqueue.repositories.InMemoryEndpointRepository
import io.github.wu4ngg.simplejobqueue.worker.DeliveryWorker
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun Application.module() {
    val endpoints: EndpointRepository = InMemoryEndpointRepository()
    val queue: DeliveryQueue = InMemoryDeliveryQueue()
    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 3_000
        }
    }
    monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureRouting(endpoints, queue)
    val worker = DeliveryWorker(
        queue = queue,
        endpoints = endpoints,
        client = client,
    )
    launch { worker.run(500.milliseconds) }
}