group = "no.nav.helse"

plugins {
    id("application")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

application {
    mainClass.set("no.nav.helse.sykepenger.forsikring.AppKt")
    applicationName = "app"
}

kotlin {
    jvmToolchain(21)
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.logback)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation(libs.wiremock)
    testImplementation(libs.httpclient5.fluent)
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.tbdLibs.rapidsAndRiversTest)
    testImplementation(libs.ktor.server.testHost)
}
