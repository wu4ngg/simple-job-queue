package io.github.wu4ngg.simplejobqueue.dtos

import io.github.wu4ngg.simplejobqueue.models.Endpoint
import kotlinx.serialization.Serializable

@Serializable
data class CreateEndpointResponse(val id: String, val url: String, val secret: String)

@Serializable
data class GetEndpointResponse(val id: String, val url: String)

@Serializable
data class CreateEndpointRequest(val url: String? = null)

fun Endpoint.toCreatedResponse(): CreateEndpointResponse {
    return CreateEndpointResponse(id = this.id, url = this.url, secret = this.secret.value)
}

fun Endpoint.toResponse(): GetEndpointResponse {
    return GetEndpointResponse(id = this.id, url = this.url)
}
