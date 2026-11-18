package io.github.wu4ngg.simplejobqueue.exceptions

import io.ktor.http.HttpStatusCode

class ApiPayloadTooLargeException(message: String) : ApiException(HttpStatusCode.PayloadTooLarge, message)