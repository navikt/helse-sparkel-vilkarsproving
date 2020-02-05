package no.nav.helse.sparkel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

const val vaultBase = "/var/run/secrets/nais.io/vault"
val vaultBasePath: Path = Paths.get(vaultBase)

fun readServiceUserCredentials() = ServiceUser(
    username = Files.readString(vaultBasePath.resolve("username")),
    password = Files.readString(vaultBasePath.resolve("password"))
)

fun setUpEnvironment() =
    Environment(
        raw = System.getenv(),
        aaregBaseUrl = System.getenv("AAREG_BASE_URL")
            ?: error("Mangler env var FPSAK_BASE_URL"),
        egenAnsattBaseUrl = System.getenv("EGENANSATT_URL")
            ?: error("Mangler env var EGENANSATT_URL")
    )

data class Environment(
    val raw: Map<String, String>,
    val stsBaseUrl: String = "http://security-token-service",
    val aaregBaseUrl: String,
    val egenAnsattBaseUrl: String
)

data class ServiceUser(
    val username: String,
    val password: String
) {
    val basicAuth = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}