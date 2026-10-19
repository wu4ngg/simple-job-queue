package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.exceptions.ValidationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.uri
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Uncaught exception on ${call.request.uri}", cause)
            call.respondText(
                text = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
            )

        }

        exception<ValidationException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Unknown error",
                status = HttpStatusCode.BadRequest,
            )
        }
    }
}
