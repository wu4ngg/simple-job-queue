package io.github.wu4ngg.simplejobqueue.routes

import io.github.wu4ngg.simplejobqueue.dtos.ApiListResponse
import io.github.wu4ngg.simplejobqueue.dtos.DeliveryResponse
import io.github.wu4ngg.simplejobqueue.dtos.toApi
import io.github.wu4ngg.simplejobqueue.dtos.toResponse
import io.github.wu4ngg.simplejobqueue.exceptions.ResourceNotFoundException
import io.github.wu4ngg.simplejobqueue.exceptions.ValidationException
import io.github.wu4ngg.simplejobqueue.models.DeliveryStatus
import io.github.wu4ngg.simplejobqueue.parseUUID
import io.github.wu4ngg.simplejobqueue.queue.DeliveryQueue
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.getOrFail

fun Route.deliveryRoutes(queue: DeliveryQueue) {
    get("/deliveries") {
        val queryParam = call.request.queryParameters["status"]
        val status = if (queryParam != null) {
            val status = DeliveryStatus.entries.find { it.toApi() == queryParam.lowercase() }
            if (status == null) {
                throw ValidationException("Invalid status $queryParam, valid statuses are: ${DeliveryStatus.entries.joinToString { it.toApi() }}")
            }
            status
        } else {
            null
        }

        val deliveries = queue.list(status = status).map { it.toResponse() }
        call.respond<ApiListResponse<DeliveryResponse>>(ApiListResponse(deliveries))
    }

    get("/deliveries/{id}") {
        val id = call.parameters.getOrFail("id")
        parseUUID(id)
        val delivery = queue.find(id) ?: throw ResourceNotFoundException("Delivery with id $id not found")
        call.respond<DeliveryResponse>(delivery.toResponse())
    }
}
