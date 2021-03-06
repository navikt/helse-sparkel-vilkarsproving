package no.nav.helse.sparkel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

const val vaultServiceUserBase = "/var/run/secrets/nais.io/service_user"
val vaultServiceUserBasePath: Path = Paths.get(vaultServiceUserBase)

fun readServiceUserCredentials() = ServiceUser(
    username = Files.readString(vaultServiceUserBasePath.resolve("username")),
    password = Files.readString(vaultServiceUserBasePath.resolve("password"))
)

fun setUpEnvironment() =
    Environment(
        raw = System.getenv(),
        stsSoapBaseUrl = System.getenv("STS_URL"),
        aaregBaseUrl = System.getenv("AAREG_BASE_URL")
            ?: error("Mangler env var FPSAK_BASE_URL"),
        egenAnsattBaseUrl = System.getenv("EGENANSATT_URL")
            ?: error("Mangler env var EGENANSATT_URL")
    )

data class Environment(
    val raw: Map<String, String>,
    val stsBaseUrl: String = "http://security-token-service.default.svc.nais.local",
    val stsSoapBaseUrl: String,
    val aaregBaseUrl: String,
    val egenAnsattBaseUrl: String
)

data class ServiceUser(
    val username: String,
    val password: String
) {
    val basicAuth = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}
