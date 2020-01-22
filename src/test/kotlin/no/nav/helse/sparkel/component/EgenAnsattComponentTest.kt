package no.nav.helse.sparkel.component

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import no.nav.common.KafkaEnvironment
import no.nav.helse.sparkel.*
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration.ofMillis
import java.util.*
import java.util.concurrent.TimeUnit

class EgenAnsattComponentTest {

    companion object {

        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"
        private const val kafkaApplicationId = "sparkel-vilkarsproving"

        private val topic = "privat-helse-sykepenger-rapid-v1"
        private val topics = listOf(topic)
        private val topicInfos = topics.map { KafkaEnvironment.TopicInfo(it, partitions = 1) }

        private lateinit var adminClient: AdminClient
        private lateinit var kafkaProducer: KafkaProducer<String, String>
        private lateinit var kafkaConsumer: KafkaConsumer<String, JsonNode>
        private lateinit var kafkaStream: KafkaStreams

        val egenAnsattService = mockk<EgenAnsattV1>()

        private val embeddedKafkaEnvironment = KafkaEnvironment(
                autoStart = false,
                noOfBrokers = 1,
                topicInfos = topicInfos,
                withSchemaRegistry = false,
                withSecurity = false,
                topicNames = topics
        )

        private fun producerProperties() =
                Properties().apply {
                    put(BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
                    put(SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                    put(ACKS_CONFIG, "all")
                    put(MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
                    put(LINGER_MS_CONFIG, "0")
                    put(RETRIES_CONFIG, "0")
                    put(SASL_MECHANISM, "PLAIN")
                }

        private fun consumerProperties(): MutableMap<String, Any>? {
            return HashMap<String, Any>().apply {
                put(BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaEnvironment.brokersURL)
                put(SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                put(SASL_MECHANISM, "PLAIN")
                put(GROUP_ID_CONFIG, "sakComponentTest")
                put(AUTO_OFFSET_RESET_CONFIG, "earliest")
            }
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            embeddedKafkaEnvironment.start()
            adminClient = embeddedKafkaEnvironment.adminClient ?: Assertions.fail("Klarte ikke få tak i adminclient")
            kafkaProducer = KafkaProducer(producerProperties(), StringSerializer(), StringSerializer())
            kafkaConsumer = KafkaConsumer(consumerProperties(), StringDeserializer(), JacksonKafkaDeserializer()).also {
                it.subscribe(topics)
            }

            val environment = Environment(
                    kafkaBootstrapServers = embeddedKafkaEnvironment.brokersURL,
                    stsSoapBaseUrl = "",
                    truststorePassword = "",
                    truststorePath = "",
                    aktørregisterUrl = "",
                    egenAnsattUrl = "",
                    serviceUser = ServiceUser("", "")
            )

            kafkaStream = startStream(
                    egenAnsattService = egenAnsattService,
                    environment = environment,
                    streamsConfig = streamsConfig(environment),
                    offsetResetPolicy = Topology.AutoOffsetReset.EARLIEST,
                    liveness = Liveness()
            )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            kafkaStream.close()
            embeddedKafkaEnvironment.tearDown()
        }

        private fun streamsConfig(environment: Environment) = Properties().apply {
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, environment.kafkaBootstrapServers)
            put(StreamsConfig.APPLICATION_ID_CONFIG, environment.kafkaAppId)
            put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndFailExceptionHandler::class.java)
            put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100)

            put(SASL_MECHANISM, "PLAIN")
            put(SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
        }
    }

    @Test
    fun `tester at vi løser et behov hvor bruker er egen ansatt‍`() {
        every { egenAnsattService.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } returns WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse().apply {
            isEgenAnsatt = true
        }

        val melding = """{"@behov": ["EgenAnsatt"], "@id":"id2", "fødselsnummer": "10101011111"}"""
        sendKafkaMessage(topic, "", melding)

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val records = kafkaConsumer.poll(ofMillis(100))
                            .map { it.value() }
                            .filter { it.path("@løsning").hasNonNull("EgenAnsatt") }
                            .filter { it["@id"].textValue() == "id2" }
                            .map { it.path("@løsning")["EgenAnsatt"].booleanValue() }

                    assertEquals(1, records.size)
                    assertTrue(records.first())
                }
    }

    @Test
    fun `tester at vi løser et behov hvor bruker ikke er egen ansatt‍`() {
        every { egenAnsattService.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } returns WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse().apply {
            isEgenAnsatt = false
        }

        val melding = """{"@behov": ["EgenAnsatt"], "@id":"id1", "fødselsnummer": "10101011111"}"""
        sendKafkaMessage(topic, "", melding)

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val records = kafkaConsumer.poll(ofMillis(100))
                                    .map { it.value() }
                                    .filter { it.path("@løsning").hasNonNull("EgenAnsatt") }
                                    .filter { it["@id"].textValue() == "id1" }
                                    .map { it.path("@løsning")["EgenAnsatt"].booleanValue() }

                    assertEquals(1, records.size)
                    assertFalse(records.first())
                }
    }

    private fun sendKafkaMessage(topic: String, key: String, message: String) =
            kafkaProducer.send(ProducerRecord(topic, key, message))

}
