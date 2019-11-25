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
import no.nav.helse.sparkel.aktør.*
import no.nav.helse.sparkel.egenansatt.egenAnsattService
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
    val server = embeddedServer(Netty, 8080) {
        install(MicrometerMetrics) {
            registry = meterRegistry
        }

        routing {
            registerHealthApi(liveness = { true }, readiness = { true }, meterRegistry = meterRegistry)
        }
    }.start(wait = false)

    val stsRest = StsRestClient(user = environment.serviceUser)
    val aktørregister = AktørregisterClient(environment.aktørregisterUrl, stsRest)
    val egenAnsattService = egenAnsattService(environment)

    startStream(aktørRegisterClient = aktørregister, egenAnsattService = egenAnsattService, environment = environment)

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(10, 10, TimeUnit.SECONDS)
    })
}
