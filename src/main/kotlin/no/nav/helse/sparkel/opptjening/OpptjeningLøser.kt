package no.nav.helse.sparkel.opptjening

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.toJson
import no.nav.helse.sparkel.objectMapper
import org.slf4j.LoggerFactory

class OpptjeningLøser(private val aaregClient: AaregClient) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun onPacket(packet: JsonNode, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("Mottok melding: ${packet.toJson()}")

        runBlocking {
            aaregClient.hentArbeidsforhold(packet["fødselsnummer"].asText())
                .also { packet.setLøsning(OpptjeningRiver.behov, it) }
        }

        log.info("løser behov: ${packet["@id"].textValue()}")
        context.send(packet.toJson())
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