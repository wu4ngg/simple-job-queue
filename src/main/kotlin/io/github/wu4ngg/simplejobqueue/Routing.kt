package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.dtos.CreateEndpointRequest
import io.github.wu4ngg.simplejobqueue.dtos.CreateEndpointResponse
import io.github.wu4ngg.simplejobqueue.dtos.GetEndpointResponse
import io.github.wu4ngg.simplejobqueue.dtos.toCreatedResponse
import io.github.wu4ngg.simplejobqueue.dtos.toResponse
import io.github.wu4ngg.simplejobqueue.exceptions.ResourceNotFoundException
import io.github.wu4ngg.simplejobqueue.exceptions.ValidationException
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.github.wu4ngg.simplejobqueue.workers.DeliveryQueue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import io.ktor.util.toLowerCasePreservingASCIIRules
import java.net.URI
import java.net.URISyntaxException

fun Application.configureRouting(endpoints: EndpointRepository, queue: DeliveryQueue) {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }

        post("/endpoints") {
            val request = call.receive<CreateEndpointRequest>()
            val rawUrl = request.url
            if (rawUrl.isNullOrBlank()) {
                throw ValidationException("URL is required")
            }
            val uri = try {
                URI(rawUrl)
            } catch (ex: URISyntaxException) {
                throw ValidationException("Invalid URL provided")
            }
            val scheme: String? = uri.scheme
            if (scheme.isNullOrBlank()) {
                throw ValidationException("A URL scheme is required")
            }
            val schemeNormalized = scheme.toLowerCasePreservingASCIIRules()
            if (schemeNormalized != "http" && schemeNormalized != "https") {
                throw ValidationException("Only http or https")
            }
            if (uri.host.isNullOrBlank()) {
                throw ValidationException("A URL host is required")
            }
            val endpoint = endpoints.create(rawUrl)
            call.respond<CreateEndpointResponse>(HttpStatusCode.Created, endpoint.toCreatedResponse())
        }

        get("/endpoints/{id}") {
            val id = call.parameters.getOrFail("id")
            val endpoint = endpoints.get(id) ?: throw ResourceNotFoundException("Endpoint: $id not found!")
            call.respond<GetEndpointResponse>(endpoint.toResponse())
        }
    }
}