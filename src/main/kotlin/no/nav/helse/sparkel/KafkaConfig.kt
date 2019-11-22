package no.nav.helse.sparkel

import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.*
import java.time.*
import java.util.*

fun <K, V> CoroutineScope.listen(
        topic: String,
        consumerConfig: Properties,
        delayMs: Long = 100,
        onMessage: (ConsumerRecord<K, V>) -> Unit
) = launch {
    val consumer = KafkaConsumer<K, V>(consumerConfig).also {
        it.subscribe(listOf(topic))
    }
    while (isActive) {
        val records = consumer.poll(Duration.ofMillis(0))
        if (records.isEmpty) {
            delay(delayMs)
        }

        records.forEach { onMessage(it) }
    }
}

fun loadBaseConfig(env: Environment, serviceUser: ServiceUser): Properties = Properties().also {
    it.load(Environment::class.java.getResourceAsStream("/kafka_base.properties"))
    it["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${serviceUser.username}\" password=\"${serviceUser.password}\";"
    it["bootstrap.servers"] = env.kafkaBootstrapServers
}

fun Properties.toConsumerConfig(): Properties = Properties().also {
    it.putAll(this)
    it[ConsumerConfig.GROUP_ID_CONFIG] = "sparkel-vilkarsproving-consumer"
    it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1000"
}

fun Properties.toProducerConfig(): Properties = Properties().also {
    it.putAll(this)
    it[ConsumerConfig.GROUP_ID_CONFIG] = "sparkel-vilkarsproving-producer"
    it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java
}