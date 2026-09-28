package no.nav.helse.sykepenger.forsikring.shared.testsupport

import no.nav.helse.sykepenger.forsikring.domain.Forsikringsvurdering
import no.nav.helse.sykepenger.forsikring.domain.Identitetsnummer
import no.nav.helse.sykepenger.forsikring.domain.SpesiellYrkesgruppe
import no.nav.helse.sykepenger.forsikring.forsikringsvurdering.ForsikringsvurderingRepository
import no.nav.helse.sykepenger.forsikring.råkopi.Råkopi
import no.nav.helse.sykepenger.forsikring.råkopi.RåkopiRepository
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction

fun Identitetsnummer.tilInfotrygdFødselsnummer(): Long = (value.substring(4, 6) + value.substring(2, 4) + value.substring(0, 2) + value.substring(6)).toLong()

fun lagreRåkopiOgForsikringsvurdering(
    forsikringsvurdering: Forsikringsvurdering,
    råkopi: Råkopi = lagRåkopiFor(forsikringsvurdering),
) {
    check(råkopi.id == forsikringsvurdering.råkopiId) {
        "Råkopien må ha samme ID som forsikringsvurderingen peker på"
    }
    TestcontainersSpForsikringDatabase.dataSource.inTransaction { transaction ->
        RåkopiRepository(transaction).lagre(råkopi)
        ForsikringsvurderingRepository(transaction).lagre(
            forsikringsvurdering,
            forsikringsvurdering.tilForsikringsvurderingBehovJson(),
        )
    }
}

private fun Forsikringsvurdering.tilForsikringsvurderingBehovJson(): String =
    """
    {
        "@behov": [ "Forsikringsvurdering" ],
        "fødselsnummer": "${identitetsnummer.value}",
        "yrkesaktivitetstype": "$yrkesaktivitetstype",
        "Forsikringsvurdering": {
            "spesielleYrkesgrupper": [ ${
        spesielleYrkesgrupper.joinToString(",") {
            when (it) {
                SpesiellYrkesgruppe.FISKER_BLAD_B -> "\"FISKER_BLAD_B\""
                SpesiellYrkesgruppe.JORDBRUKER -> "\"JORDBRUKER\""
                SpesiellYrkesgruppe.REINDRIFTER -> "\"REINDRIFTER\""
            }
        }
    } ],
            "skjæringstidspunkt": "$skjæringstidspunkt"
        }
    }
    """.trimIndent()
