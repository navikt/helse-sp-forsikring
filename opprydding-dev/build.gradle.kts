plugins {
    id("application")
    alias(libs.plugins.kotlin.jvm)
}

application {
    mainClass.set("no.nav.helse.sykepenger.forsikring.opprydding_dev.AppKt")
    applicationName = "app"
}

dependencies {
    implementation(libs.hikaricp)
    implementation(libs.kotliquery)
    implementation(libs.postgresql)
    implementation(libs.cloud.sql.postgres.socket.factory)
    implementation(libs.rapids.and.rivers)

    testImplementation(project(":migreringer"))
    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.flyway.database.postgresql)
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
