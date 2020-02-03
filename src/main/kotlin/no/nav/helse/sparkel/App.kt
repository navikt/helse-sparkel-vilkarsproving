package no.nav.helse.sparkel

import no.nav.helse.rapids_rivers.AppBuilder
import no.nav.helse.sparkel.egenansatt.EgenAnsattFactory
import no.nav.helse.sparkel.egenansatt.configureFor
import no.nav.helse.sparkel.egenansatt.stsClient
import org.apache.cxf.ext.logging.LoggingFeature
import java.io.File
import java.io.FileNotFoundException

fun main() {
    val app = createApp(System.getenv())
    app.start()
}

fun createApp(env: Map<String, String>): AppBuilder {
    val builder = AppBuilder(env)

    val stsClientWs = stsClient(
        stsUrl = env["STS_URL"] ?: throw IllegalArgumentException("Mangler STS URL"),
        username = "/var/run/secrets/nais.io/service_user/username".readFile()
            ?: throw IllegalArgumentException("Mangler service user"),
        password = "/var/run/secrets/nais.io/service_user/password".readFile()
            ?: throw IllegalArgumentException("Mangler service user password")
    )

    val egenAnsattService = EgenAnsattFactory.create(
        env["EGENANSATT_URL"] ?: throw IllegalArgumentException("Mangler EGENANSATT_URL"),
        listOf(LoggingFeature())
    ).apply {
        stsClientWs.configureFor(this)
    }

    val river = EgenAnsattRiver()
    river.register(EgenAnsattLøser(egenAnsattService))
    builder.register(river)

    return builder
}

private fun String.readFile() =
    try {
        File(this).readText(Charsets.UTF_8)
    } catch (err: FileNotFoundException) {
        null
    }
