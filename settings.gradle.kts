rootProject.name = "helse-sp-forsikring"

include(
    "migreringer",
    "sp-forsikring",
    "opprydding-dev"
)

apply("buildSrc/repositories.settings.gradle.kts")
