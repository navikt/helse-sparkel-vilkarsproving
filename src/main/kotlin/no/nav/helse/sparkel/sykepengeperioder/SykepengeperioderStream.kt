package no.nav.helse.sparkel.sykepengeperioder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.Application
import io.ktor.application.ApplicationStarted
import io.ktor.application.ApplicationStopping
import io.ktor.application.log
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sparkel.sykepengeperioder.serde.JsonNodeSerde
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

private val sykepengeperioderBehov = "sykepengeperioder"
private val behovTopic = "privat-helse-sykepenger-behov"

private val log = LoggerFactory.getLogger("sparkel-sykepengeperioder")

private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

@KtorExperimentalAPI
fun Application.sykepengeperioderApplication(): KafkaStreams {

    val builder = StreamsBuilder()

    builder.stream<String, JsonNode>(
            listOf(behovTopic), Consumed.with(Serdes.String(), JsonNodeSerde(objectMapper))
            .withOffsetResetPolicy(Topology.AutoOffsetReset.LATEST)
    ).peek { key, value ->
        log.info("mottok melding key=$key value=$value")
    }.filter { _, value ->
        value.erBehov(sykepengeperioderBehov)
    }.filterNot { _, value ->
        value.harLøsning()
    }.peek { key, value ->
        log.info("løser behov key=$key")
    }.mapValues { _, value ->
        value.setLøsning(lagLøsning())
    }.to(behovTopic)

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

private fun JsonNode.erBehov(type: String) =
        has("behov") && this["behov"].textValue() == type

private fun JsonNode.harLøsning() =
        has("løsning")

internal fun lagLøsning() = JsonNodeFactory.instance
        .objectNode().put("opprettet", LocalDateTime.now().toString())

private fun JsonNode.setLøsning(løsning: JsonNode) =
        (this as ObjectNode).set("løsning", løsning)

@KtorExperimentalAPI
private fun Application.streamsConfig() = Properties().apply {
    put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, environment.config.property("kafka.bootstrap-servers").getString())
    put(StreamsConfig.APPLICATION_ID_CONFIG, environment.config.property("kafka.app-id").getString())

    put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndFailExceptionHandler::class.java)

    put(SaslConfigs.SASL_MECHANISM, "PLAIN")
    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")

    environment.config.propertyOrNull("kafka.username")?.getString()?.let { username ->
        environment.config.propertyOrNull("kafka.password")?.getString()?.let { password ->
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
