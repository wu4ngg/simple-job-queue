package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.dtos.GetEndpointResponse
import io.github.wu4ngg.simplejobqueue.dtos.toResponse
import io.github.wu4ngg.simplejobqueue.exceptions.ResourceNotFoundException
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.github.wu4ngg.simplejobqueue.workers.DeliveryQueue
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail

fun Application.configureRouting(endpoints: EndpointRepository, queue: DeliveryQueue) {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }

        post("/endpoints") {

        }

        get("/endpoints/{id}") {
            val id = call.parameters.getOrFail("id")
            val endpoint = endpoints.get(id) ?: throw ResourceNotFoundException("Endpoint: $id not found!")
            call.respond<GetEndpointResponse>(endpoint.toResponse())
        }
    }
}