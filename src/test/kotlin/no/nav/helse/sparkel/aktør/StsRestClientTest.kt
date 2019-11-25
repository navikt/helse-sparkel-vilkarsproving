package no.nav.helse.sparkel.aktør

import com.github.tomakehurst.wiremock.*
import com.github.tomakehurst.wiremock.client.*
import com.github.tomakehurst.wiremock.core.*
import no.nav.helse.sparkel.ServiceUser
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class StsRestClientTest{
    fun stsRestStub(): MappingBuilder {
        return WireMock.get(WireMock.urlPathEqualTo("/rest/v1/sts/token"))
                .willReturn(WireMock.okJson(okStsResponse))
    }

    private val okStsResponse = """{
        "access_token": "default access token",
        "token_type": "Bearer",
        "expires_in": 3600
    }""".trimIndent()

    companion object {
        val sts: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            sts.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            sts.stop()
        }
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(sts.port())
        sts.stubFor(stsRestStub())
    }

    @Test
    fun `Skal returnere token`() {
        val stsClient = StsRestClient("http://localhost:${sts.port()}", ServiceUser("username", "password"))
        assertNotNull(stsClient.accessToken())
    }

    @Test
    fun `Token skal ha riktige verdier`() {
        val stsClient = StsRestClient("http://localhost:${sts.port()}", ServiceUser("username", "password"))
        val token = stsClient.accessToken()
        assertEquals("default access token", token)
    }
}