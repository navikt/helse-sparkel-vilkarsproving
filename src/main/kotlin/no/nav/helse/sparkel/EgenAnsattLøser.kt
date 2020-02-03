package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.toJson
import no.nav.helse.sparkel.EgenAnsattRiver.Companion.behov
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest
import org.slf4j.LoggerFactory

internal class EgenAnsattLøser(private val egenAnsattService: EgenAnsattV1) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    override fun onPacket(packet: JsonNode, context: RapidsConnection.MessageContext) {
        try {
            sikkerlogg.info("mottok melding: ${packet.toJson()}")
            egenAnsattService.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(
                WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest().withIdent(packet["fødselsnummer"].asText())
            ).isEgenAnsatt
                .also {
                    packet.setLøsning(behov, it)
                }
            log.info("løser behov: ${packet["@id"].textValue()}")

            context.send(packet.toJson())
        } catch (err: Exception) {
            log.error("feil ved henting av egen ansatt: ${err.message}", err)
        }
    }

    private fun JsonNode.setLøsning(nøkkel: String, data: Any) =
        (this as ObjectNode).set<JsonNode>(
            "@løsning", objectMapper.convertValue(
                mapOf(
                    nøkkel to data
                )
            )
        )

}
