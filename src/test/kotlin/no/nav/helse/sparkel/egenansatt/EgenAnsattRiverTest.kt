package no.nav.helse.sparkel.egenansatt

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EgenAnsattRiverTest {

    @Test
    internal fun `Test om behov inneholder ønsket string` () {
        assertInvalid("""{}""")
        assertInvalid("""{"@id": "id", "fødselsnummer": "fnr", "@behov": "Foreldrepenger"}""")
        assertInvalid("""{"@id": "id", "@behov": "EgenAnsatt"}""")
        assertInvalid("""{"@id": "id", "@behov": ["EgenAnsatt"]}""")
        assertValid("""{"@id": "id", "fødselsnummer": "fnr", "@behov": ["EgenAnsatt", "Foreldrepenger"]}""")
        assertValid("""{"@id": "id", "fødselsnummer": "fnr", "@behov": "EgenAnsatt"}""")
    }

    private fun assertValid(message: String) {
        assertTrue(testMelding(message))
    }

    private fun assertInvalid(message: String) {
        assertFalse(testMelding(message))
    }

    private fun testMelding(message: String): Boolean {
        var called = false
        EgenAnsattRiver().apply {
            register(object : River.PacketListener {
                override fun onPacket(packet: JsonNode, context: RapidsConnection.MessageContext) {
                    called = true
                }
            })
        }.onMessage(message, object : RapidsConnection.MessageContext {
            override fun send(message: String) {}

            override fun send(key: String, message: String) {}
        })

        return called
    }
}
