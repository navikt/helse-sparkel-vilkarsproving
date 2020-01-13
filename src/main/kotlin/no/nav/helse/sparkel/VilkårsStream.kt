package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sparkel.serde.JsonNodeSerde
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest
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
import org.apache.kafka.streams.kstream.Produced
import java.io.File
import java.time.Duration
import java.util.Properties

private const val behovstype = "EgenAnsatt"
private const val behovTopic = "privat-helse-sykepenger-behov"

fun startStream(
    egenAnsattService: EgenAnsattV1,
    environment: Environment,
    streamsConfig: Properties = streamsConfig(environment),
    offsetResetPolicy: Topology.AutoOffsetReset = Topology.AutoOffsetReset.LATEST,
    liveness: Liveness
): KafkaStreams {
    val builder = StreamsBuilder()

    builder.stream<String, JsonNode>(
        listOf(behovTopic), Consumed.with(Serdes.String(), JsonNodeSerde(objectMapper))
            .withOffsetResetPolicy(offsetResetPolicy)
    )
        .peek { key, _ -> log.info("mottok melding {}", keyValue("behovId", key)) }
        .filter { _, value -> value.skalOppfyllesAvOss(behovstype) }
        .filterNot { _, value -> value.harLøsning() }.filter { _, value ->
            value.hasNonNull("fødselsnummer")
        }
        .mapValues { _, value ->
            val fnr = value["fødselsnummer"].textValue()
            val erEgenAnsatt = egenAnsattService.erEgenAnsatt(fnr)
            value.setLøsning(
                ObjectNode(
                    JsonNodeFactory.instance,
                    mapOf(behovstype to BooleanNode.valueOf(erEgenAnsatt))
                )
            )
        }
        .peek { key, _ -> log.info("løst behov for key=$key") }
        .to(behovTopic, Produced.with(Serdes.String(), JsonNodeSerde(objectMapper)))

    return KafkaStreams(builder.build(), streamsConfig).apply {
        addShutdownHook(liveness)
        start()
    }
}

private fun EgenAnsattV1.erEgenAnsatt(fnr: String) =
    hentErEgenAnsattEllerIFamilieMedEgenAnsatt(WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest().withIdent(fnr))
        .isEgenAnsatt

private fun JsonNode.skalOppfyllesAvOss(type: String) = this["@behov"].map(JsonNode::asText).any { it == type }
private fun JsonNode.harLøsning() = hasNonNull("@løsning")
private fun JsonNode.setLøsning(løsning: JsonNode) = (this as ObjectNode).set("@løsning", løsning)

private fun streamsConfig(environment: Environment) = Properties().apply {
    put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, environment.kafkaBootstrapServers)
    put(StreamsConfig.APPLICATION_ID_CONFIG, environment.kafkaAppId)

    put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndFailExceptionHandler::class.java)

    put(SaslConfigs.SASL_MECHANISM, "PLAIN")
    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")

    put(
        SaslConfigs.SASL_JAAS_CONFIG,
        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${environment.serviceUser.username}\" password=\"${environment.serviceUser.password}\";"
    )

    try {
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(environment.truststorePath).absolutePath)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, environment.truststorePassword)
        log.info("Configured '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location ")
    } catch (ex: Exception) {
        log.error("Failed to set '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location", ex)
    }
}

private fun KafkaStreams.addShutdownHook(liveness: Liveness) {
    setStateListener { newState, oldState ->
        log.info("From state={} to state={}", oldState, newState)

        if (newState == KafkaStreams.State.ERROR) {
            // if the stream has died there is no reason to keep spinning
            log.warn("No reason to keep living, closing stream")
            close(Duration.ofSeconds(10))
        }
        if (newState in arrayOf(KafkaStreams.State.ERROR, KafkaStreams.State.NOT_RUNNING)) {
            liveness.isAlive = false
        }
    }
    setUncaughtExceptionHandler { _, ex ->
        log.error("Caught exception in stream, exiting", ex)
        close(Duration.ofSeconds(10))
    }
}