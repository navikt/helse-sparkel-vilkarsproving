package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sparkel.egenansatt.EgenAnsattFactory
import no.nav.helse.sparkel.egenansatt.EgenAnsattLøser
import no.nav.helse.sparkel.egenansatt.configureFor
import no.nav.helse.sparkel.egenansatt.stsClient
import no.nav.helse.sparkel.opptjening.AaregClient
import no.nav.helse.sparkel.opptjening.OpptjeningLøser
import no.nav.helse.sparkel.opptjening.StsRestClient
import org.apache.cxf.ext.logging.LoggingFeature

fun main() {
    val env = setUpEnvironment()
    val app = createApp(env)
    app.start()
}

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())


fun createApp(env: Environment): RapidsConnection {
    val rapidsConnection = RapidApplication.create(env.raw)
    val serviceUser = readServiceUserCredentials()

    val stsClientWs = stsClient(
        stsUrl = env.stsSoapBaseUrl,
        serviceUser = serviceUser
    )

    val egenAnsattService = EgenAnsattFactory.create(
        env.egenAnsattBaseUrl,
        listOf(LoggingFeature())
    ).apply {
        stsClientWs.configureFor(this)
    }

    val aregClient = AaregClient(
        baseUrl = env.aaregBaseUrl,
        stsRestClient = StsRestClient(env.stsBaseUrl, serviceUser, simpleHttpClient()),
        httpClient = simpleHttpClient()
    )

    EgenAnsattLøser(rapidsConnection, egenAnsattService)
    OpptjeningLøser(rapidsConnection, aregClient)

    return rapidsConnection
}

private fun simpleHttpClient(serializer: JacksonSerializer? = JacksonSerializer()) = HttpClient() {
    install(JsonFeature) {
        this.serializer = serializer
    }
    install(HttpTimeout) {
        socketTimeoutMillis = 10000
        requestTimeoutMillis = 10000
        connectTimeoutMillis = 10000
    }
}
