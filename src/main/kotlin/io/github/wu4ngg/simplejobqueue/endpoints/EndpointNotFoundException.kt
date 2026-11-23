package io.github.wu4ngg.simplejobqueue.endpoints

class EndpointNotFoundException(endpointId: String, cause: Throwable? = null) :
    Exception("Endpoint not found: $endpointId", cause)