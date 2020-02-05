package no.nav.helse.sparkel.egenansatt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
internal class EgenAnsattLøserTest {

    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private val egenansattV1 = mockk<EgenAnsattV1>()

    private lateinit var sendtMelding: JsonNode
    private val context = object : RapidsConnection.MessageContext {
        override fun send(message: String) {
            sendtMelding = objectMapper.readTree(message)
        }

        override fun send(key: String, message: String) {}
    }

    @BeforeAll
    fun setup() {
        mockEgenAnsatt()
    }

    private fun mockEgenAnsatt(egenAnsatt: Boolean = false) {
        egenansattV1.apply {
            every { hentErEgenAnsattEllerIFamilieMedEgenAnsatt(any()) } answers {
                WSHentErEgenAnsattEllerIFamilieMedEgenAnsattResponse()
                    .withEgenAnsatt(egenAnsatt)
            }
        }
    }

    @Test
    internal fun `løser behov ikke egen ansatt`() {
        val behov = """{"@id": "behovsid", "@behov":["${EgenAnsattRiver.behov}"], "fødselsnummer": "fnr" }"""

        testBehov(behov)

        assertFalse(sendtMelding.løsning())
    }


    @Test
    internal fun `løser behov egen ansatt`() {
        mockEgenAnsatt(true)

        val behov = """{"@id": "behovsid", "@behov":["${EgenAnsattRiver.behov}"], "fødselsnummer": "fnr" }"""

        testBehov(behov)

        assertTrue(sendtMelding.løsning())
    }

    private fun JsonNode.løsning() = this.path("@løsning").path(EgenAnsattRiver.behov).booleanValue()

    private fun testBehov(behov: String) {
        val løser = EgenAnsattLøser(egenansattV1)
        løser.onPacket(objectMapper.readTree(behov), context)
    }
}
