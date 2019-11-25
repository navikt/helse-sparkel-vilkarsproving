package no.nav.helse.sparkel.aktør

import com.github.tomakehurst.wiremock.*
import com.github.tomakehurst.wiremock.client.*
import com.github.tomakehurst.wiremock.core.*
import com.github.tomakehurst.wiremock.matching.*
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class AktørregisterClientTest {
    val stsMock = mockk<StsRestClient> {
        every { accessToken() } returns "whatever"
    }

    companion object {
        val aktørreg: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            aktørreg.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            aktørreg.stop()
        }
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(aktørreg.port())
    }

    @Test
    fun `Returnerer fnr hvis aktør finnes`() {
        aktørreg.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/identer"))
                .withQueryParam("gjeldende", EqualToPattern("true"))
                .willReturn(WireMock.okJson(ok_norskIdent_response)))
        val fnr = AktørregisterClient("http://localhost:${aktørreg.port()}", stsMock).fnr("11111")
        assertEquals("22222", fnr)
    }

    @Test
    fun `Returnerer null hvis aktør ikke finnes`() {
        aktørreg.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/identer"))
                .withQueryParam("gjeldende", EqualToPattern("true"))
                .willReturn(WireMock.okJson(id_not_found_response)))
        val fnr = AktørregisterClient("http://localhost:${aktørreg.port()}", stsMock).fnr("33333")
        assertNull(fnr)
    }

    private val ok_norskIdent_response = """
        {
          "11111": {
            "identer": [
              {
                "ident": "22222",
                "identgruppe": "NorskIdent",
                "gjeldende": true
              },
              {
                "ident": "1573082186699",
                "identgruppe": "AktoerId",
                "gjeldende": true
              }
            ],
            "feilmelding": null
          }
        }""".trimIndent()

    private val id_not_found_response = """
        {
            "33333": {
                "identer": null,
                "feilmelding": "Den angitte personidenten finnes ikke"
            }
        }
        """.trimIndent()

}