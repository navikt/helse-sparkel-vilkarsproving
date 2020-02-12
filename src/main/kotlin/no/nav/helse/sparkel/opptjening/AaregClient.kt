package no.nav.helse.sparkel.opptjening

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import io.ktor.http.ContentType
import no.nav.helse.sparkel.objectMapper
import java.time.LocalDate


class AaregClient(
    private val baseUrl: String,
    private val stsRestClient: StsRestClient,
    private val httpClient: HttpClient = HttpClient()
) {
    internal suspend fun hentArbeidsforhold(fnr: String): List<Arbeidsforhold> =
        httpClient.get<HttpResponse>("$baseUrl/v1/arbeidstaker/arbeidsforhold") {
            header("Authorization", "Bearer ${stsRestClient.token()}")
            header("Nav-Consumer-Token", "Bearer ${stsRestClient.token()}")
            accept(ContentType.Application.Json)
            header("Nav-Personident", fnr)
        }.let {
            objectMapper.readValue<ArrayNode>(it.readText())
        }.map {
            it.toArbeidsforhold()
        }

    private fun JsonNode.toArbeidsforhold() = Arbeidsforhold(
        ansattSiden = this.path("ansettelsesperiode").path("periode").path("fom").asLocalDate(),
        ansattTil = this.path("ansettelsesperiode").path("periode").path("tom").asOptionalLocalDate(),
        orgnummer = this["arbeidsgiver"].path("organisasjonsnummer").asText()
    )
}

data class Arbeidsforhold(
    val orgnummer: String,
    val ansattSiden: LocalDate,
    val ansattTil: LocalDate?
)

internal fun JsonNode.asOptionalLocalDate() =
    takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotEmpty)?.let { LocalDate.parse(it) }

internal fun JsonNode.asLocalDate() =
    asText().let { LocalDate.parse(it) }