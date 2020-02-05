package no.nav.helse.sparkel.opptjening

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidsConnection
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OpptjeningLøserTest {
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private lateinit var sendtMelding: JsonNode
    private val context = object : RapidsConnection.MessageContext {
        override fun send(message: String) {
            sendtMelding = objectMapper.readTree(message)
        }

        override fun send(key: String, message: String) {}
    }

    @Test
    internal fun `løser opptjeningbehov`() {
        val behov = """{"@id": "behovsid", "@behov":["${OpptjeningRiver.behov}"], "fødselsnummer": "fnr" }"""
        val mockAaregClient = AaregClient(
            baseUrl = "http://baseUrl.local",
            stsRestClient = mockStsRestClient,
            httpClient = aregMockClient(mockGenerator)
        )
        val løser = OpptjeningLøser(mockAaregClient)
        løser.onPacket(objectMapper.readTree(behov), context)
        val løsning = sendtMelding.løsning()
        assertTrue(løsning.isNotEmpty())
        println(sendtMelding)
    }

    private fun JsonNode.løsning(): List<Arbeidsforhold> =
        this.path("@løsning")
            .path(OpptjeningRiver.behov)
            .map {
                Arbeidsforhold(
                    it["orgnummer"].asText(),
                    it["ansattSiden"].asLocalDate(),
                    it["ansattTil"].asOptionalLocalDate()
                )
            }
}
