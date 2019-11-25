package no.nav.helse.sparkel.egenansatt

import no.nav.helse.sparkel.Environment
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1

fun egenAnsattService(environment: Environment) =
        createPort<EgenAnsattV1>(environment.egenAnsattUrl) {
            port {
                withSTS(
                        environment.serviceUser.username,
                        environment.serviceUser.password,
                        environment.stsSoapBaseUrl
                )
            }
        }
