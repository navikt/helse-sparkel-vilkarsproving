package no.nav.helse.sparkel

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import no.nav.helse.sparkel.egenansatt.*
import no.nav.helse.sparkel.nais.*
import no.nav.tjeneste.pip.egen.ansatt.v1.*
import java.io.*
import java.util.concurrent.*

@KtorExperimentalAPI
fun createConfigFromEnvironment(env: Map<String, String>) =
        MapApplicationConfig().apply {
            put("server.port", env.getOrDefault("HTTP_PORT", "8080"))

            put("kafka.app-id", "sparkel-vilkarsproving-v1")

            env["KAFKA_BOOTSTRAP_SERVERS"]?.let { put("kafka.bootstrap-servers", it) }
            put("srv.username", "/var/run/secrets/nais.io/srv/username".readFile() ?: env.getValue("SRV_USERNAME"))
            put("srv.password", "/var/run/secrets/nais.io/srv/password".readFile() ?: env.getValue("SRV_PASSWORD"))

            put("sts.url", env.getValue("STS_URL"))
            put("egenAnsatt.url", env.getValue("EGENANSATT_URL"))

            put("aktør.url", env.getValue("AKTORREGISTER_URL"))

            env["NAV_TRUSTSTORE_PATH"]?.let { put("kafka.truststore-path", it) }
            env["NAV_TRUSTSTORE_PASSWORD"]?.let { put("kafka.truststore-password", it) }

            put("azure.tenant_id", env.getValue("AZURE_TENANT_ID"))
            put("azure.client_id", "/var/run/secrets/nais.io/azure/client_id".readFile() ?: env.getValue("AZURE_CLIENT_ID"))
            put("azure.client_secret", "/var/run/secrets/nais.io/azure/client_secret".readFile() ?: env.getValue("AZURE_CLIENT_SECRET"))
        }

private fun String.readFile() =
        try {
            File(this).readText(Charsets.UTF_8)
        } catch (err: FileNotFoundException) {
            null
        }

@KtorExperimentalAPI
fun main() {
    val config = createConfigFromEnvironment(System.getenv())

    embeddedServer(Netty, createApplicationEnvironment(config)).let { app ->
        app.start(wait = false)

        Runtime.getRuntime().addShutdownHook(Thread {
            app.stop(1, 1, TimeUnit.SECONDS)
        })
    }
}

@KtorExperimentalAPI
fun createApplicationEnvironment(appConfig: ApplicationConfig) = applicationEngineEnvironment {
    config = appConfig

    connector {
        port = appConfig.property("server.port").getString().toInt()
    }

    module {
        val streams = sykepengeperioderApplication()
        nais(
                isAliveCheck = { streams.state().isRunning }
        )
    }
}
