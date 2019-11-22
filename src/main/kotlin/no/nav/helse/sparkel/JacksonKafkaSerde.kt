package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.*
import org.apache.kafka.common.serialization.*

class JacksonKafkaSerializer : Serializer<JsonNode> {
    override fun serialize(topic: String?, data: JsonNode?): ByteArray = objectMapper.writeValueAsBytes(data)
}

class JacksonKafkaDeserializer: Deserializer<JsonNode> {
    override fun deserialize(topic: String?, data: ByteArray): JsonNode = objectMapper.readTree(data)
}