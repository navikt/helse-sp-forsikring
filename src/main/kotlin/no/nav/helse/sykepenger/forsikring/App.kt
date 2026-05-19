package no.nav.helse.sykepenger.forsikring

import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    launchApplication(System.getenv())
}

fun launchApplication(env: Map<String, String>) {
    val sykepengeforsikringService = SykepengeforsikringService()

    RapidApplication
        .create(System.getenv(), builder = {
            withKtorModule {
                sykepengeforsikringApi(
                    sykepengeforsikringService = sykepengeforsikringService,
                    clientId = env.getValue("AZURE_APP_CLIENT_ID"),
                    issuerUrl = env.getValue("AZURE_OPENID_CONFIG_ISSUER"),
                    jwkProviderUri = env.getValue("AZURE_OPENID_CONFIG_JWKS_URI")
                )
            }
        })
        .apply {
            SykepengeforsikringRiver(this, sykepengeforsikringService)
        }.start()
}
