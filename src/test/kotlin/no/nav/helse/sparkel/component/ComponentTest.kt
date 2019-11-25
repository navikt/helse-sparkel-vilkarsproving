package no.nav.helse.sparkel.component

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import no.nav.common.KafkaEnvironment
import no.nav.helse.sparkel.*
import no.nav.helse.sparkel.aktør.AktørregisterClient
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
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import java.time.Duration.ofMillis
import java.util.*
import java.util.concurrent.TimeUnit

class ComponentTest {

    companion object {

        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"
        private const val kafkaApplicationId = "sparkel-vilkarsproving"

        private val topic = "privat-helse-sykepenger-behov"
        private val topics = listOf(topic)
        private val topicInfos = topics.map { KafkaEnvironment.TopicInfo(it, partitions = 1) }

        private lateinit var adminClient: AdminClient
        private lateinit var kafkaProducer: KafkaProducer<String, String>
        private lateinit var kafkaConsumer: KafkaConsumer<String, JsonNode>
        private lateinit var kafkaStream: KafkaStreams

        val aktørRegisterClient = mockk<AktørregisterClient>()
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
                    aktørRegisterClient = aktørRegisterClient,
                    egenAnsattService = egenAnsattService,
                    environment = environment,
                    streamsConfig = streamsConfig(environment),
                    offsetResetPolicy = Topology.AutoOffsetReset.EARLIEST
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
    fun `tester at vi løser et behov? 🤷‍`() {
        every { egenAnsattService.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } returns WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse().apply {
            isEgenAnsatt = true
        }

        every { aktørRegisterClient.fnr(any()) } returns "12345678910"

        val melding = """{"@behov": "Vilkårsdata", "aktørId": "1234"}"""
        synchronousSendKafkaMessage(topic, "", melding)

        await()
            .atMost(10, TimeUnit.SECONDS)
            .until {
                kafkaConsumer.poll(ofMillis(100))
                    .any { it.value().hasNonNull("@løsning") }
            }
    }

    private fun sendKafkaMessage(topic: String, key: String, message: String) =
            kafkaProducer.send(ProducerRecord(topic, key, message))

    /**
     * Trick Kafka into behaving synchronously by sending the message, and then confirming that it is read by the consumer group
     */
    private fun synchronousSendKafkaMessage(topic: String, key: String, message: String) {
        val metadata = sendKafkaMessage(topic, key, message)
        kafkaProducer.flush()
        metadata.get().assertMessageIsConsumed()
    }

    /**
     * Check that the consumers has received this message, by comparing the position of the message with the reported last read message of the consumer group
     */
    private fun RecordMetadata.assertMessageIsConsumed() {
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val offsetAndMetadataMap = adminClient.listConsumerGroupOffsets(kafkaApplicationId).partitionsToOffsetAndMetadata().get()
                    val topicPartition = TopicPartition(topic(), partition())
                    val currentPositionOfSentMessage = offset()
                    val currentConsumerGroupPosition = offsetAndMetadataMap[topicPartition]?.offset()?.minus(1)
                            ?: Assertions.fail() // This offset represents next position to read from, so we subtract 1 to get the last read offset
                    Assertions.assertEquals(currentConsumerGroupPosition, currentPositionOfSentMessage)
                }
    }

}