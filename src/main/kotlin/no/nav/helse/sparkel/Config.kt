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
                kafkaAppId = "sparkel-vilkarsproving",
                stsSoapBaseUrl = System.getenv("STS_URL") ?: error("Mangler env var STS_URL"),
                aktørregisterUrl = System.getenv("AKTORREGISTER_URL") ?: error("Mangler env var AKTORREGISTER_URL"),
                egenAnsattUrl = System.getenv("EGENANSATT_URL") ?: error("Mangler env var EGENANSATT_URL")
        )

data class Environment(
        val kafkaBootstrapServers: String,
        val kafkaAppId: String,
        val spleisBehovtopic: String = "privat-helse-sykepenger-behov",
        val stsSoapBaseUrl: String,
        val aktørregisterUrl: String,
        val egenAnsattUrl: String
)

data class ServiceUser(
        val username: String,
        val password: String
)


