package io.github.wu4ngg.simplejobqueue.exceptions

import io.ktor.http.HttpStatusCode

class ApiBadRequestException(message: String) : ApiException(HttpStatusCode.BadRequest, message)