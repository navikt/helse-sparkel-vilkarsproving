package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.River

// Understands a EgenAnsattbehov message
internal class EgenAnsattRiver() : River() {

    companion object {
        internal const val behov = "EgenAnsatt"
    }

    init {
        validate { behov == it.path("@behov").asText() || behov in it.path("@behov").map(JsonNode::asText) }
        validate { it.path("@løsning").let { it.isMissingNode || it.isNull } }
        validate { it.hasNonNull("@id") }
        validate { it.hasNonNull("fødselsnummer") }
    }
}
