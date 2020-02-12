package no.nav.helse.sparkel.opptjening

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.fullPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class AaregClientTest {

    private val responsMock = mockk<Mock>()

    @Test
    internal fun `person med aktivt arbeidsforhold`() = runBlocking {
        every { responsMock.get() }.returns(aktivtArbeidsforhold)
        val arbeidsforhold = aaregClient.hentArbeidsforhold("fnr")

        assertNotNull(arbeidsforhold)
        assertEquals("orgnummer", arbeidsforhold[0].orgnummer)
        assertEquals(LocalDate.of(2019,3,1), arbeidsforhold[0].ansattSiden)
        assertNull(arbeidsforhold[0].ansattTil)
    }

    private val aaregClient = AaregClient(
        baseUrl = "http://localhost.no",
        httpClient = HttpClient(MockEngine) {
            install(JsonFeature) {
                this.serializer = JacksonSerializer()
            }
            engine {
                addHandler { request ->
                    if (request.url.fullPath.startsWith("/v1/arbeidstaker/arbeidsforhold")) {
                        respond(responsMock.get())
                    } else {
                        error("Endepunktet finnes ikke ${request.url.fullPath}")
                    }
                }
            }
        },
        stsRestClient = mockk { every { runBlocking { token() } }.returns("token") }
    )
}

private class Mock {
    fun get() = ""
}

val aktivtArbeidsforhold = """
[
    {
        "navArbeidsforholdId": 49119313,
        "arbeidsforholdId": "1",
        "arbeidstaker": {
            "type": "Person",
            "offentligIdent": "fnr",
            "aktoerId": "aktørId"
        },
        "arbeidsgiver": {
            "type": "Organisasjon",
            "organisasjonsnummer": "orgnummer"
        },
        "opplysningspliktig": {
            "type": "Organisasjon",
            "organisasjonsnummer": "juridisk-orgnummer"
        },
        "type": "ordinaertArbeidsforhold",
        "ansettelsesperiode": {
            "periode": {
                "fom": "2019-03-01"
            },
            "bruksperiode": {
                "fom": "2020-02-12T13:33:19.711"
            },
            "sporingsinformasjon": {
                "opprettetTidspunkt": "2020-02-12T13:33:19.712",
                "opprettetAv": "srvtestnorge-aareg",
                "opprettetKilde": "AAREG",
                "opprettetKildereferanse": "Dolly 3dca648d-6cd5-47a8-82da-4cbf9bf946c8",
                "endretTidspunkt": "2020-02-12T13:33:19.712",
                "endretAv": "srvtestnorge-aareg",
                "endretKilde": "AAREG",
                "endretKildereferanse": "Dolly 3dca648d-6cd5-47a8-82da-4cbf9bf946c8"
            }
        },
        "arbeidsavtaler": [
            {
                "arbeidstidsordning": "ikkeSkift",
                "yrke": "2521106",
                "stillingsprosent": 100,
                "antallTimerPrUke": 37.5,
                "beregnetAntallTimerPrUke": 37.5,
                "bruksperiode": {
                    "fom": "2020-02-12T13:33:19.711"
                },
                "gyldighetsperiode": {
                    "fom": "2019-03-01"
                },
                "sporingsinformasjon": {
                    "opprettetTidspunkt": "2020-02-12T13:33:19.712",
                    "opprettetAv": "srvtestnorge-aareg",
                    "opprettetKilde": "AAREG",
                    "opprettetKildereferanse": "Dolly 3dca648d-6cd5-47a8-82da-4cbf9bf946c8",
                    "endretTidspunkt": "2020-02-12T13:33:19.712",
                    "endretAv": "srvtestnorge-aareg",
                    "endretKilde": "AAREG",
                    "endretKildereferanse": "Dolly 3dca648d-6cd5-47a8-82da-4cbf9bf946c8"
                }
            }
        ],
        "innrapportertEtterAOrdningen": true,
        "registrert": "2020-02-12T13:33:19.653",
        "sistBekreftet": "2020-02-12T13:33:19",
        "sporingsinformasjon": {
            "opprettetTidspunkt": "2020-02-12T13:33:19.711",
            "opprettetAv": "srvtestnorge-aareg",
            "opprettetKilde": "AAREG",
            "opprettetKildereferanse": "Dolly 3dca648d-6cd5-47a8-82da-4cbf9bf946c8",
            "endretTidspunkt": "2020-02-12T13:33:19.711",
            "endretAv": "srvtestnorge-aareg",
            "endretKilde": "AAREG",
            "endretKildereferanse": "Dolly 3dca648d-6cd5-47a8-82da-4cbf9bf946c8"
        }
    }
]"""