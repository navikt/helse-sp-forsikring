plugins {
    id("application")
    alias(libs.plugins.kotlin.jvm)
}

application {
    mainClass.set("no.nav.helse.sykepenger.forsikring.AppKt")
    applicationName = "app"
}

dependencies {
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.ojdbc11)
    implementation(libs.rapids.and.rivers)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.logback)
    implementation(libs.kotliquery)
    implementation(libs.flyway.database.postgresql)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.3")
    implementation(project(":migreringer"))

    testImplementation(libs.flyway.database.oracle)
    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.httpclient5.fluent)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.wiremock)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.oracle.free)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(25)
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
