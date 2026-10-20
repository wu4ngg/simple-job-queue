package io.github.wu4ngg.simplejobqueue.exceptions

import io.ktor.http.HttpStatusCode

class ResourceNotFoundException(message: String) : ApiException(HttpStatusCode.NotFound, message)