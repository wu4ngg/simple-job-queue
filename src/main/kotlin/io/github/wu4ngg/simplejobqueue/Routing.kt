package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.constants.MAX_PAYLOAD_BYTES
import io.github.wu4ngg.simplejobqueue.dtos.CreateEndpointRequest
import io.github.wu4ngg.simplejobqueue.dtos.CreateEndpointResponse
import io.github.wu4ngg.simplejobqueue.dtos.EnqueueEventResponse
import io.github.wu4ngg.simplejobqueue.dtos.GetEndpointResponse
import io.github.wu4ngg.simplejobqueue.dtos.toCreatedResponse
import io.github.wu4ngg.simplejobqueue.dtos.toResponse
import io.github.wu4ngg.simplejobqueue.etc.testReceiver
import io.github.wu4ngg.simplejobqueue.exceptions.ApiBadRequestException
import io.github.wu4ngg.simplejobqueue.exceptions.ApiPayloadTooLargeException
import io.github.wu4ngg.simplejobqueue.exceptions.ResourceNotFoundException
import io.github.wu4ngg.simplejobqueue.exceptions.ValidationException
import io.github.wu4ngg.simplejobqueue.repositories.EndpointRepository
import io.github.wu4ngg.simplejobqueue.queue.DeliveryQueue
import io.github.wu4ngg.simplejobqueue.routes.deliveryRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import io.ktor.util.toLowerCasePreservingASCIIRules
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URISyntaxException

fun Application.configureRouting(endpoints: EndpointRepository, queue: DeliveryQueue) {
    val failRate = environment.config.property("testReceiver.flakyReceiverFailureRate")
        .getString().toDouble()
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        post("/endpoints/{id}/events") {
            val endpointId = call.parameters.getOrFail("id")
            val endpoint = endpoints.get(endpointId) ?: throw ResourceNotFoundException("Endpoint not found")
            val maxPayloadBytes = MAX_PAYLOAD_BYTES
            val reqLength = call.request.contentLength() ?: 0
            if (reqLength > maxPayloadBytes) {
                call.application.log.error("Invalid request length: $reqLength")
                throw ApiPayloadTooLargeException("Invalid request")
            }
            val payload = call.receiveText()
            val payloadLength = payload.toByteArray().size
            call.application.log.info("Received request with reqLength: $reqLength / payloadLength: $payloadLength")
            if (payloadLength > maxPayloadBytes) {
                call.application.log.error("Invalid request length: $payloadLength")
                throw ApiPayloadTooLargeException("Payload exceeds 256 KB")
            }
            if (payload.isBlank()) {
                throw ValidationException("Body is blank")
            }
            try {
                Json.parseToJsonElement(payload)
            } catch (_: SerializationException) {
                throw ApiBadRequestException("Body is invalid (not a JSON)")
            }
            val delivery = queue.enqueue(endpoint.id, payload)
            val response = EnqueueEventResponse(deliveryId = delivery.id)
            call.respond<EnqueueEventResponse>(HttpStatusCode.Accepted, response)
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

        deliveryRoutes(queue)

        testReceiver(
            failRate = failRate
        )
    }
}
