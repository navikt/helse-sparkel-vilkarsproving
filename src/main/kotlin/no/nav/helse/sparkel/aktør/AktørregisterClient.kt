package no.nav.helse.sparkel.aktør

import com.fasterxml.jackson.databind.node.*
import com.fasterxml.jackson.module.kotlin.*
import java.net.*
import java.net.http.*

class AktørregisterClient(val baseUrl: String = "", val stsRestClient: StsRestClient) {

    val objectMapper = jacksonObjectMapper()
    val client = HttpClient.newHttpClient()

    fun fnr(aktørId: String): String? {
        val aktørregResponse = client
                .send(HttpRequest.newBuilder(URI("$baseUrl/api/v1/identer?gjeldende=true"))
                        .GET()
                        .header("Accept", "application/json")
                        .header("Nav-Call-Id", "anything") //todo
                        .header("Nav-Consumer-Id", "sparkel-vilkarsproving")
                        .header("Nav-Personidenter", aktørId)
                        .header("Authorization", "Bearer ${stsRestClient.accessToken()}")
                        .build(), HttpResponse.BodyHandlers.ofString())
        if (aktørregResponse.statusCode() != 200) {
            throw RuntimeException("Feil ved henting av identer. Får statuskode ${aktørregResponse.statusCode()}")
        }

        val identer = objectMapper.readTree(aktørregResponse.body())[aktørId]["identer"] as? ArrayNode
        return identer?.filter { it["gjeldende"].asBoolean() }
                ?.filter { it["identgruppe"].asText() == "NorskIdent" }
                ?.map { it["ident"].asText() }
                ?.firstOrNull()
    }

}

