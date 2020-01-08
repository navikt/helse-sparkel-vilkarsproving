package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.metrics.micrometer.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.*
import no.nav.helse.sparkel.egenansatt.EgenAnsattFactory
import org.apache.cxf.ext.logging.LoggingFeature
import org.slf4j.*
import java.util.concurrent.*

val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())
val log: Logger = LoggerFactory.getLogger("sparkel-vilkarsproving")

fun main() {
    val environment = setUpEnvironment()

    launchApplication(environment)
}

fun launchApplication(environment: Environment) {
    val liveness = Liveness()
    val server = embeddedServer(Netty, 8080) {
        install(MicrometerMetrics) {
            registry = meterRegistry
        }

        routing {
            registerHealthApi(liveness = liveness::isAlive, readiness = { true }, meterRegistry = meterRegistry)
        }
    }.start(wait = false)

    val stsClientWs = stsClient(environment.stsSoapBaseUrl,
            environment.serviceUser.username to environment.serviceUser.password)

    val egenAnsattService = EgenAnsattFactory.create(environment.egenAnsattUrl, listOf(LoggingFeature())).apply {
        stsClientWs.configureFor(this)
    }

    startStream(egenAnsattService = egenAnsattService, environment = environment, liveness = liveness)

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(10, 10, TimeUnit.SECONDS)
    })
}

class Liveness{
    var isAlive = true
}
