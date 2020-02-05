package no.nav.helse.sparkel.opptjening

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.River

class OpptjeningRiver : River() {
    companion object {
        internal const val behov = "Opptjening"
    }

    init {
        validate { behov == it.path("@behov").asText() || behov in it.path("@behov").map(JsonNode::asText) }
        validate { it.path("@løsning").let { løsning -> løsning.isMissingNode || løsning.isNull } }
        validate { it.hasNonNull("@id") }
        validate { it.hasNonNull("fødselsnummer") }
    }
}
