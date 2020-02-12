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
        assertEquals("arbeidsforholdId", arbeidsforhold[0].orgnummer)
        assertEquals(LocalDate.of(2019,6,6), arbeidsforhold[0].ansattSiden)
        assertNull(arbeidsforhold[0].ansattTil)
    }

    @Test
    internal fun `person med avsluttet arbeidsforhold`() = runBlocking {
        every { responsMock.get() }.returns(avsluttetArbeidsforhold)
        val arbeidsforhold = aaregClient.hentArbeidsforhold("fnr")

        assertNotNull(arbeidsforhold)
        assertEquals("arbeidsforholdId", arbeidsforhold[0].orgnummer)
        assertEquals(LocalDate.of(2019,6,6), arbeidsforhold[0].ansattSiden)
        assertEquals(LocalDate.of(2019,9,10), arbeidsforhold[0].ansattTil)
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

private val aktivtArbeidsforhold = """
            [
              {
                "arbeidsforholdId": "arbeidsforholdId",
                "ansettelsesperiode": {
                  "periode": {
                    "fom": "2019-06-06"
                  }
                }
              }
            ]
"""

private val avsluttetArbeidsforhold = """
    [
              {
                "arbeidsforholdId": "arbeidsforholdId",
                "ansettelsesperiode": {
                  "periode": {
                    "fom": "2019-06-06",
                    "tom": "2019-09-10"
                  }
                }
              }
            ]
"""