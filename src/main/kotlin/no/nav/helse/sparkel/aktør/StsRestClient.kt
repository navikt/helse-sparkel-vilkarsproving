package no.nav.helse.sparkel.aktør

import com.fasterxml.jackson.module.kotlin.*
import no.nav.helse.sparkel.*
import java.net.*
import java.net.http.*
import java.time.*
import java.util.*

class StsRestClient(val baseUrl: String = "http://security-token-service.default.nais.svc.local", val user: ServiceUser) {
    private var cachedOidcToken: Token? = null

    val objectMapper = jacksonObjectMapper()
    val client = HttpClient.newHttpClient()

    fun accessToken(): String =
            (cachedOidcToken
                    ?.takeUnless { cachedOidcToken.shouldRenew() }
                    ?: run {
                        val response = client
                                .send(HttpRequest.newBuilder(URI("$baseUrl/rest/v1/sts/token?grant_type=client_credentials&scope=openid"))
                                        .GET()
                                        .header("Accept", "application/json")
                                        .header("Authorization", "Basic ${Base64.getEncoder().encodeToString("${user.username}:${user.password}".toByteArray())}")
                                        .build(), HttpResponse.BodyHandlers.ofString())

                        if (response.statusCode() != 200) {
                            throw RuntimeException("Feil ved henting av token. Får statuskode ${response.statusCode()}")
                        }

                        response.body()
                                .toToken()
                                .also { cachedOidcToken = it }
                    }).access_token

    private fun String.toToken() =
            objectMapper.readValue<Token>(this)

    private fun Token?.shouldRenew() =
            if (this == null) {
                true
            } else {
                expirationTime.isBefore(LocalDateTime.now())
            }

    data class Token(val access_token: String, val token_type: String, val expires_in: Int) {
        // expire 10 seconds before actual expiry. for great margins.
        val expirationTime: LocalDateTime = LocalDateTime.now().plusSeconds(expires_in - 10L)
    }
}
