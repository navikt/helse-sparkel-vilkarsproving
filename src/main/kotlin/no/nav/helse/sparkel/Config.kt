package no.nav.helse.sparkel

import java.nio.file.*

const val vaultBase = "/var/run/secrets/nais.io/vault"
val vaultBasePath: Path = Paths.get(vaultBase)

fun readServiceUserCredentials() = ServiceUser(
        username = Files.readString(vaultBasePath.resolve("username")),
        password = Files.readString(vaultBasePath.resolve("password"))
)

fun setUpEnvironment() =
        Environment(
                kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: error("Mangler env var KAFKA_BOOTSTRAP_SERVERS"),
                stsSoapBaseUrl = System.getenv("STS_URL") ?: error("Mangler env var STS_URL"),
                aktørregisterUrl = System.getenv("AKTORREGISTER_URL") ?: error("Mangler env var AKTORREGISTER_URL"),
                egenAnsattUrl = System.getenv("EGENANSATT_URL") ?: error("Mangler env var EGENANSATT_URL"),
                truststorePath = System.getenv("NAV_TRUSTSTORE_PATH") ?: error("Mangler env var NAV_TRUSTSTORE_PATH"),
                truststorePassword = System.getenv("NAV_TRUSTSTORE_PASSWORD") ?: error("Mangler env var NAV_TRUSTSTORE_PASSWORD"),
                serviceUser = readServiceUserCredentials()
        )

data class Environment(
        val kafkaBootstrapServers: String,
        val kafkaAppId: String = "sparkel-vilkarsproving",
        val spleisRapidtopic: String = "privat-helse-sykepenger-rapid-v1",
        val stsSoapBaseUrl: String,
        val aktørregisterUrl: String,
        val egenAnsattUrl: String,
        val truststorePath: String,
        val truststorePassword: String,
        val serviceUser: ServiceUser
)

data class ServiceUser(
        val username: String,
        val password: String
)


