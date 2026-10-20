package io.github.wu4ngg.simplejobqueue

import io.github.wu4ngg.simplejobqueue.exceptions.ApiException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
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

        exception<NotFoundException> { call, cause ->
            call.respondText(
                text = cause.message ?: "Not found",
                status = HttpStatusCode.NotFound,
            )
        }

        exception<BadRequestException> { call, cause ->
            call.application.log.debug("Bad request. Uri: ${call.request.uri}", cause)
            call.respondText("Bad request", status = HttpStatusCode.BadRequest)
        }

        exception<ApiException> { call, cause ->
            call.respondText(
                text = cause.message!!,
                status = cause.statusCode,
            )
        }
    }
}
