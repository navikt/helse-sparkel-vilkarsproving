package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import no.nav.helse.rapids_rivers.AppBuilder
import no.nav.helse.sparkel.egenansatt.*
import no.nav.helse.sparkel.opptjening.AaregClient
import no.nav.helse.sparkel.opptjening.OpptjeningLøser
import no.nav.helse.sparkel.opptjening.OpptjeningRiver
import no.nav.helse.sparkel.opptjening.StsRestClient
import org.apache.cxf.ext.logging.LoggingFeature

fun main() {
    val env = setUpEnvironment()
    val app = createApp(env)
    app.start()
}

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

fun createApp(env: Environment): AppBuilder {
    val builder = AppBuilder(env.raw)
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
        stsRestClient = StsRestClient(env.stsBaseUrl, serviceUser),
        httpClient = simpleHttpClient()
    )

    val egenAnsattRiver = EgenAnsattRiver()
    egenAnsattRiver.register(EgenAnsattLøser(egenAnsattService))
    builder.register(egenAnsattRiver)

    val opptjeningRiver = OpptjeningRiver()
    opptjeningRiver.register(OpptjeningLøser(aregClient))
    builder.register(opptjeningRiver)

    return builder
}

private fun simpleHttpClient(serializer: JacksonSerializer? = JacksonSerializer()) = HttpClient() {
    install(JsonFeature) {
        this.serializer = serializer
    }
}
