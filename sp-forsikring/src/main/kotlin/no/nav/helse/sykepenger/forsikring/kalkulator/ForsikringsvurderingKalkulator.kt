package no.nav.helse.sykepenger.forsikring.kalkulator

import java.time.LocalDate
import no.nav.helse.sykepenger.forsikring.NavKjøptForsikring
import no.nav.helse.sykepenger.forsikring.RåForsikring

data class Dekning(val grad: Int, val fraDag: Int)

data class KalkulatorEkskludering(
    val råForsikring: RåForsikring,
    val årsak: NavKjøptForsikring.Ekskluderingsårsak,
)

data class KalkulatorResultat(
    val dekning: Dekning?,
    val inkluderteRåForsikringer: List<RåForsikring>,
    val ekskluderinger: List<KalkulatorEkskludering>,
) {
    val forsikret: Boolean get() = dekning != null
}

class ForsikringsvurderingKalkulator {
    fun kalkuler(forsikringer: List<RåForsikring>, skjæringstidspunkt: LocalDate): KalkulatorResultat {
        val kandidater = forsikringer.toMutableList()
        val ekskluderinger = mutableListOf<KalkulatorEkskludering>()

        // Skjæringstidspunkt må ikke være i opptjeningstid [IF10_FORSFOM, IF10_VIRKDATO)
        val forsikringerIOpptjeningstid = kandidater.filter { it.erIOpptjeningstid(skjæringstidspunkt) }
        kandidater.removeAll(forsikringerIOpptjeningstid)
        forsikringerIOpptjeningstid.forEach {
            ekskluderinger.add(KalkulatorEkskludering(it, NavKjøptForsikring.Ekskluderingsårsak.SKJÆRINGSTIDSPUNKT_I_OPPTJENINGSTID))
        }

        // Skjæringstidspunkt må være etter eller lik virkningsdato
        val forsikringerMedVirkningsdatoEtterSkjæringstidspunkt = kandidater.filterNot { it.harVirkningPå(skjæringstidspunkt) }
        kandidater.removeAll(forsikringerMedVirkningsdatoEtterSkjæringstidspunkt)
        forsikringerMedVirkningsdatoEtterSkjæringstidspunkt.forEach {
            ekskluderinger.add(KalkulatorEkskludering(it, NavKjøptForsikring.Ekskluderingsårsak.VIRKNINGSDATO_ETTER_SKJÆRINGSTIDSPUNKT))
        }

        // Skjæringstidspunkt må være før eller lik opphørsdato (hvis det er en opphørsdato)
        val opphørteForsikringer = kandidater.filter { it.erOpphørtPå(skjæringstidspunkt) }
        kandidater.removeAll(opphørteForsikringer)
        opphørteForsikringer.forEach {
            ekskluderinger.add(KalkulatorEkskludering(it, NavKjøptForsikring.Ekskluderingsårsak.OPPHØRT_PÅ_SKJÆRINGSTIDSPUNKT))
        }

        // Forsikringen må være betalt noen gang
        val ubetalteForsikringer = kandidater.filterNot { it.erBetaltNoenGang() }
        kandidater.removeAll(ubetalteForsikringer)
        ubetalteForsikringer.forEach {
            ekskluderinger.add(KalkulatorEkskludering(it, NavKjøptForsikring.Ekskluderingsårsak.ALDRI_BETALT))
        }

        val dekning = kandidater.minByOrNull { it.dekningFraDag() }?.let {
            Dekning(grad = it.dekningGrad(), fraDag = it.dekningFraDag())
        }

        return KalkulatorResultat(
            dekning = dekning,
            inkluderteRåForsikringer = kandidater,
            ekskluderinger = ekskluderinger,
        )
    }
}
