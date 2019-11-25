package no.nav.helse.sparkel

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.micrometer.prometheus.*
import io.prometheus.client.exporter.common.*

private val prometheusContentType = ContentType.parse(TextFormat.CONTENT_TYPE_004)

fun Routing.registerHealthApi(
        liveness: () -> Boolean,
        readiness: () -> Boolean,
        meterRegistry: PrometheusMeterRegistry
) {
    get("/isalive") {
        if (liveness()) {
            call.respondText("alive")
        } else {
            call.respondText("not alive", status = HttpStatusCode.InternalServerError)
        }
    }

    get("/isready") {
        if (readiness()) {
            call.respondText("ready")
        } else {
            call.respondText("not ready", status = HttpStatusCode.InternalServerError)
        }
    }

    get("/metrics") {
        call.respondText(
                text = meterRegistry.scrape(),
                contentType = prometheusContentType
        )
    }
}