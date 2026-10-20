package io.github.wu4ngg.simplejobqueue.exceptions

import io.ktor.http.HttpStatusCode

class ValidationException(message: String) : ApiException(HttpStatusCode.BadRequest, message)