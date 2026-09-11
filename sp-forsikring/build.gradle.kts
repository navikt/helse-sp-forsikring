plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.sykepenger.forsikring.AppKt"
}

dependencies {
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.cloud.sql.postgres.socket.factory)
    implementation(libs.ojdbc11)
    implementation(libs.rapids.and.rivers)
    implementation(libs.bundles.ktor.server)
    implementation(libs.httpclient5.fluent)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sykepengerLibs.logging)
    implementation(libs.kotliquery)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.tbd.libs.access.token.provider.api)
    implementation(libs.tbd.libs.access.token.provider.texas)
    implementation(libs.tbd.libs.retry)
    implementation(project(":migreringer"))

    testImplementation(libs.flyway.database.oracle)
    testImplementation(libs.sykepengerLibs.testing)
    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.wiremock)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.oracle.free)
}
