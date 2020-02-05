package no.nav.helse.sparkel.egenansatt

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.River

internal class EgenAnsattRiver() : River() {
    companion object {
        internal const val behov = "EgenAnsatt"
    }

    init {
        validate { behov == it.path("@behov").asText() || behov in it.path("@behov").map(JsonNode::asText) }
        validate { it.path("@løsning").let { løsning -> løsning.isMissingNode || løsning.isNull } }
        validate { it.hasNonNull("@id") }
        validate { it.hasNonNull("fødselsnummer") }
    }
}
