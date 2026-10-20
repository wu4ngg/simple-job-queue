package io.github.wu4ngg.simplejobqueue.exceptions

import io.ktor.http.HttpStatusCode

sealed class ApiException(val statusCode: HttpStatusCode, message: String) : RuntimeException(message)