import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = "1.2.4"
val cxfVersion = "3.3.1"
val egenAnsattVersion = "1.2019.09.25-00.21-49b69f0625e0"
val jaxWsVersion = "2.3.2"
val wireMockVersion = "2.19.0"
val mockkVersion = "1.9.3"
val junitJupiterVersion = "5.6.0"
val mainClass = "no.nav.helse.sparkel.AppKt"

plugins {
    kotlin("jvm") version "1.3.70"
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:1.84a7ce0")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("com.sun.xml.ws:jaxws-ri:$jaxWsVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    implementation("javax.activation:activation:1.1.1")

    implementation("no.nav.tjenestespesifikasjoner:egenansatt-v1-tjenestespesifikasjon:$egenAnsattVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("no.nav:kafka-embedded-env:2.2.3")
    testImplementation("org.awaitility:awaitility:3.1.6")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/navikt/rapids-and-rivers")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    maven("http://packages.confluent.io/maven/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_12
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions.jvmTarget = "12"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "12"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "6.0.1"
}
