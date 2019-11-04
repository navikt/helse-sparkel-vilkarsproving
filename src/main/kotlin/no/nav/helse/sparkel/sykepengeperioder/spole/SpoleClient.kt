package no.nav.helse.sparkel.sykepengeperioder.spole

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class SpoleClient(private val baseUrl: String, private val accesstokenScope: String, private val azureClient: AzureClient) {

    companion object {
        private val objectMapper = ObjectMapper()
        private val tjenestekallLog = LoggerFactory.getLogger("tjenestekall")
    }

    fun hentSykepengeperioder(aktørId: String): Sykepengeperioder {
        val (responseCode, responseBody) = with(URL("$baseUrl/sykepengeperioder/${aktørId}").openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            setRequestProperty("Authorization", "Bearer ${azureClient.getToken(accesstokenScope).accessToken}")
            setRequestProperty("Accept", "application/json")

            responseCode to (this.errorStream ?: this.inputStream).bufferedReader().readText()
        }

        tjenestekallLog.info("svar fra spole: responseCode=$responseCode responseBody=$responseBody")

        if (responseCode >= 300) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from spole")
        }

        val jsonNode = objectMapper.readTree(responseBody)

        return Sykepengeperioder(
                aktørId = jsonNode["aktør_id"].textValue(),
                perioder = (jsonNode["perioder"] as ArrayNode).map { periodeJson ->
                    Periode(
                            fom = LocalDate.parse(periodeJson["fom"].textValue()),
                            tom = LocalDate.parse(periodeJson["tom"].textValue()),
                            grad = periodeJson["grad"].textValue()
                    )
                }
        )
    }
}

data class Sykepengeperioder(val aktørId: String, val perioder: List<Periode>)
data class Periode(val fom: LocalDate, val tom: LocalDate, val grad: String)
