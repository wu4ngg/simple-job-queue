package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.github.wu4ngg.simplejobqueue.repositories.InMemoryEndpointRepository
import io.github.wu4ngg.simplejobqueue.queue.DeliveryQueue
import io.github.wu4ngg.simplejobqueue.queue.InMemoryDeliveryQueue
import io.ktor.server.application.Application

fun Application.module() {
    val endpoints: EndpointRepository = InMemoryEndpointRepository()
    val queue: DeliveryQueue = InMemoryDeliveryQueue()
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureRouting(endpoints, queue)
}