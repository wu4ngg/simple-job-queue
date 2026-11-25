package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.db.configureDatabase
import io.github.wu4ngg.simplejobqueue.queue.DeliveryQueue
import io.github.wu4ngg.simplejobqueue.queue.InMemoryDeliveryQueue
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.github.wu4ngg.simplejobqueue.endpoints.InMemoryEndpointRepository
import io.github.wu4ngg.simplejobqueue.endpoints.PostgresEndpointRepository
import io.github.wu4ngg.simplejobqueue.queue.PostgresDeliveryQueue
import io.github.wu4ngg.simplejobqueue.worker.DeliveryWorker
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

fun Application.module() {
    val ds = configureDatabase()
    val endpoints: EndpointRepository = PostgresEndpointRepository(ds)
    val queue: DeliveryQueue = PostgresDeliveryQueue(ds)
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
        instanceId = UUID.randomUUID().toString(),
    )
    launch { worker.run(500.milliseconds) }
}