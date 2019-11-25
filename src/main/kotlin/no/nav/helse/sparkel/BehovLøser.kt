package no.nav.helse.sparkel

import com.fasterxml.jackson.databind.*
import no.nav.helse.sparkel.aktør.*
import no.nav.helse.sparkel.egenansatt.*
import no.nav.tjeneste.pip.egen.ansatt.v1.*

class BehovLøser(private val aktørregister: AktørregisterClient, stsUrl: String, egenAnsattUrl: String, serviceUser: ServiceUser) {

    private val tps: EgenAnsattV1 = createPort(egenAnsattUrl) {
        port {
            withSTS(
                    serviceUser.username,
                    serviceUser.password,
                    stsUrl
            )
        }
    }

    fun erEgenAnsatt(behov: JsonNode): Boolean {
        val aktørId = behov["aktørId"].asText()

        return aktørregister.fnr(aktørId)?.let {
            val request = WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest().apply {
                ident = it
            }
            tps.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(request).isEgenAnsatt
        } ?: true
    }


}


