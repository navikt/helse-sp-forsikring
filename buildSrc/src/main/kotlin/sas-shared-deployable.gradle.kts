plugins {
    id("application")
    id("org.jetbrains.kotlin.jvm")
}

application {
    applicationName = "app"
}

// Legger main class i en argfil ved siden av start-scriptene, slik at Dockerfile kan peke på den og ikke har main class direkte definert
tasks.startScripts {
    val argsFile = outputDir!!.resolve("main.args")
    val mainClass = application.mainClass
    doLast { argsFile.writeText(mainClass.get() + "\n") }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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
