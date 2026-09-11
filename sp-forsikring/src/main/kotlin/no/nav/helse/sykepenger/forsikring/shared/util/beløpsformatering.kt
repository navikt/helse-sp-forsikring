package no.nav.helse.sykepenger.forsikring.shared.util

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

fun Number.somBeløpstekst(): String =
    NumberFormat
        .getInstance(Locale.of("no", "NO"))
        .apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            roundingMode = RoundingMode.HALF_UP
        }.format(this)
        .replace('\u00A0', ' ')
        .replace('\u2212', '-')
        .replace(",00", "")
