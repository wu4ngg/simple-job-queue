package io.github.wu4ngg.simplejobqueue.etc

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.random.Random

fun Route.testReceiver(
    failRate: Double,
    random: Random = Random.Default
) {
    require(failRate in 0.0..1.0) { "testReceiver failure rate must be in 0.0..1.0, got $failRate" }
    post("/test-receiver") {
        val roll = random.nextDouble()
        val body = call.receiveText()
        if (roll > failRate) {
            call.application.log.info("testReceiver. Body: $body --> 200")
            call.respond(HttpStatusCode.OK)
        } else {
            call.application.log.info("testReceiver. Body: $body --> 500")
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
