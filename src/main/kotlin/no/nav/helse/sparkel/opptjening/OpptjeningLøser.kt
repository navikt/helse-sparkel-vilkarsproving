package no.nav.helse.sparkel.opptjening

import io.ktor.client.features.ClientRequestException
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class OpptjeningLøser(rapidsConnection: RapidsConnection, private val aaregClient: AaregClient) : River.PacketListener {

    companion object {
        internal const val behov = "Opptjening"
    }

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.requireAll("@behov", listOf(behov)) }
            validate { it.forbid("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("vedtaksperiodeId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("Mottok melding: ${packet.toJson()}")

        val arbeidsforhold = try {
            runBlocking {
                aaregClient.hentArbeidsforhold(packet["fødselsnummer"].asText())
            }.also {
                log.info(
                    "løser behov={} for {}",
                    keyValue("id", packet["@id"].asText()),
                    keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
                )
            }
        } catch (err: ClientRequestException) {
            emptyList<Arbeidsforhold>().also {
                log.error(
                    "Feilmelding for behov={} for {} ved oppslag i AAreg. Svarer med tom liste",
                    keyValue("id", packet["@id"].asText()),
                    keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText())
                )
                sikkerlogg.error(
                    "Feilmelding for behov={} for {} ved oppslag i AAreg: ${err.message}. Svarer med tom liste.",
                    keyValue("id", packet["@id"].asText()),
                    keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                    err
                )
            }
        }

        packet.setLøsning(behov, arbeidsforhold)
        context.send(packet.toJson())
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {}

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }
}
