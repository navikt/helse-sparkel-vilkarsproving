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
import kotlinx.coroutines.*
import no.nav.helse.sparkel.aktør.*
import org.slf4j.*
import java.util.concurrent.*

private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())
private val log = LoggerFactory.getLogger("sparkel-vilkarsproving")

fun main() = runBlocking {
    val serviceUser = readServiceUserCredentials()
    val environment = setUpEnvironment()

    launchApplication(environment, serviceUser)
}

fun launchApplication(environment: Environment, serviceUser: ServiceUser) {
    val applicationContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    val exceptionHandler = CoroutineExceptionHandler { context, ex ->
        context.cancel(CancellationException("Feil i lytter", ex))
    }

    runBlocking(exceptionHandler + applicationContext) {
        val server = embeddedServer(Netty, 8080) {
            install(MicrometerMetrics) {
                registry = meterRegistry
            }

            routing {
                registerHealthApi(liveness = { true }, readiness = { true }, meterRegistry = meterRegistry)
            }
        }.start(wait = false)

        val stsRest = StsRestClient(user = serviceUser)
        val aktørregister = AktørregisterClient(environment.aktørregisterUrl, stsRest)
        val behovLøser = BehovLøser(aktørregister, environment.stsSoapBaseUrl, environment.egenAnsattUrl, serviceUser)

        Runtime.getRuntime().addShutdownHook(Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
            applicationContext.close()
        })
    }
}


