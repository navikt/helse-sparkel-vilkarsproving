package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.node.*
import com.fasterxml.jackson.datatype.jsr310.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.util.*
import no.nav.helse.sparkel.aktør.*
import no.nav.helse.sparkel.serde.*
import no.nav.helse.sparkel.egenansatt.*
import no.nav.tjeneste.pip.egen.ansatt.v1.*
import org.apache.kafka.clients.*
import org.apache.kafka.common.config.*
import org.apache.kafka.common.serialization.*
import org.apache.kafka.streams.*
import org.apache.kafka.streams.errors.*
import org.apache.kafka.streams.kstream.*
import java.io.*
import java.time.*
import java.util.*

private const val behovstype = "Vilkårsdata"
private const val behovTopic = "privat-helse-sykepenger-behov"

private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

@KtorExperimentalAPI
fun Application.sykepengeperioderApplication(): KafkaStreams {

    val stsRest = StsRestClient(username = environment.config.property("srv.username").getString(),
            password = environment.config.property("srv.password").getString())
    val aktørRegister = AktørregisterClient(environment.config.property("aktør.url").getString(), stsRest)

    val egenAnsattService = egenAnsattService()

    val builder = StreamsBuilder()

    builder.stream<String, JsonNode>(
            listOf(behovTopic), Consumed.with(Serdes.String(), JsonNodeSerde(objectMapper))
            .withOffsetResetPolicy(Topology.AutoOffsetReset.LATEST)
    ).peek { key, value ->
        log.info("mottok melding key=$key value=$value")
    }.filter { _, value ->
        value.erBehov(behovstype)
    }.filterNot { _, value ->
        value.harLøsning()
    }.filter { _, value ->
        value.has("aktørId")
    }.mapValues { _, value ->
        val aktørId = value["aktørId"].asText()
        val fnr = aktørRegister.fnr(aktørId)
        val erEgenAnsatt = fnr?.let { egenAnsattService.erEgenAnsatt(it) } ?: true
        value.setLøsning(ObjectNode(JsonNodeFactory.instance, mapOf("erEgenAnsatt" to BooleanNode.valueOf(erEgenAnsatt))))
    }.filterNot { _, value ->
        value == null
    }.peek { key, value ->
        log.info("løst behov for key=$key")
    }.to(behovTopic, Produced.with(Serdes.String(), JsonNodeSerde(objectMapper)))

    return KafkaStreams(builder.build(), streamsConfig()).apply {
        addShutdownHook(this)

        environment.monitor.subscribe(ApplicationStarted) {
            start()
        }

        environment.monitor.subscribe(ApplicationStopping) {
            close(Duration.ofSeconds(10))
        }
    }
}

private fun EgenAnsattV1.erEgenAnsatt(fnr: String) =
        hentErEgenAnsattEllerIFamilieMedEgenAnsatt(
                WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest().withIdent(fnr))
                .isEgenAnsatt


private fun JsonNode.erBehov(type: String) =
        has("@behov") && this["@behov"].textValue() == type

private fun JsonNode.harLøsning() =
        has("@løsning")

private fun JsonNode.setLøsning(løsning: JsonNode) =
        (this as ObjectNode).set("@løsning", løsning)

@KtorExperimentalAPI
private fun Application.streamsConfig() = Properties().apply {
    put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, environment.config.property("kafka.bootstrap-servers").getString())
    put(StreamsConfig.APPLICATION_ID_CONFIG, environment.config.property("kafka.app-id").getString())

    put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndFailExceptionHandler::class.java)

    put(SaslConfigs.SASL_MECHANISM, "PLAIN")
    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")

    environment.config.propertyOrNull("srv.username")?.getString()?.let { username ->
        environment.config.propertyOrNull("srv.password")?.getString()?.let { password ->
            put(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
            )
        }
    }

    environment.config.propertyOrNull("kafka.truststore-path")?.getString()?.let { truststorePath ->
        environment.config.propertyOrNull("kafka.truststore-password")?.getString().let { truststorePassword ->
            try {
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(truststorePath).absolutePath)
                put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword)
                log.info("Configured '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location ")
            } catch (ex: Exception) {
                log.error("Failed to set '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location", ex)
            }
        }
    }
}

private fun Application.addShutdownHook(streams: KafkaStreams) {
    streams.setStateListener { newState, oldState ->
        log.info("From state={} to state={}", oldState, newState)

        if (newState == KafkaStreams.State.ERROR) {
            // if the stream has died there is no reason to keep spinning
            log.warn("No reason to keep living, closing stream")
            streams.close(Duration.ofSeconds(10))
        }
    }
    streams.setUncaughtExceptionHandler { _, ex ->
        log.error("Caught exception in stream, exiting", ex)
        streams.close(Duration.ofSeconds(10))
    }
}

@KtorExperimentalAPI
private fun Application.egenAnsattService() =
        createPort<EgenAnsattV1>(environment.config.property("egenAnsatt.url").getString()) {
            port {
                withSTS(
                        environment.config.property("srv.username").getString(),
                        environment.config.property("srv.password").getString(),
                        environment.config.property("sts.url").getString()
                )
            }
        }
