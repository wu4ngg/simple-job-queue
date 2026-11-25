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
        call.receiveText()
        val deliveryIdHeader = call.request.headers["X-Delivery-Id"]
        val deliveryAttemptHeader = call.request.headers["X-Delivery-Attempt"]
        if (roll > failRate) {
            call.application.log.info("testReceiver. Status 200. deliveryId $deliveryIdHeader, attempt #$deliveryAttemptHeader")
            call.respond(HttpStatusCode.OK)
        } else {
            call.application.log.info("testReceiver. Status 500. deliveryId $deliveryIdHeader, attempt #$deliveryAttemptHeader")
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
