package no.nav.helse.sykepenger.forsikring.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.sykepenger.forsikring.domain.Forsikringstype
import no.nav.helse.sykepenger.forsikring.domain.KollektivForsikring
import no.nav.helse.sykepenger.forsikring.domain.NavKjøptForsikringType
import no.nav.helse.sykepenger.forsikring.shared.logging.loggInfo
import no.nav.helse.sykepenger.forsikring.shared.util.inTransaction
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.BELØPSSKALA
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.SumPerForsikringstype
import no.nav.helse.sykepenger.forsikring.tellingutbetaling.UtbetalingPerForsikringstypeDao
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.sql.DataSource

fun Route.utbetalingsstatistikkApi(spForsikringDataSource: DataSource) {
    get("/api/utbetalinger/utbetaltesummer") {
        val fom =
            call.parameters["fom"].tilDato()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ugyldigDatoProblem("fom", call.request.uri))
        val tom =
            call.parameters["tom"].tilDato()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ugyldigDatoProblem("tom", call.request.uri))

        loggInfo("Mottok kall til GET /api/utbetalinger/utbetaltesummer", "fom" to fom, "tom" to tom)

        if (fom.isAfter(tom)) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ProblemResponse(
                    title = "Ugyldig periode",
                    status = HttpStatusCode.BadRequest.value,
                    detail = "fom ($fom) kan ikke være etter tom ($tom)",
                    instance = call.request.uri,
                ),
            )
        }

        val summerFraDatabasen =
            spForsikringDataSource
                .inTransaction { transaction ->
                    UtbetalingPerForsikringstypeDao(transaction).summerPerForsikringstype(fom = fom, tom = tom)
                }.associateBy { it.forsikringstype }

        val response =
            UtbetalingsutbetaltesummerResponse(
                fom = fom,
                tom = tom,
                perForsikringstype =
                    (KollektivForsikring.entries + NavKjøptForsikringType.entries).map { forsikringstype ->
                        summerFraDatabasen[forsikringstype]?.tilApi() ?: forsikringstype.utenUtbetalinger()
                    },
            )

        loggInfo("Svarer på GET /api/utbetalinger/utbetaltesummer", "response" to response)

        call.respond(response)
    }
}

private fun String?.tilDato(): LocalDate? {
    if (isNullOrBlank()) return null
    return try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun ugyldigDatoProblem(
    feltnavn: String,
    instance: String,
): ProblemResponse =
    ProblemResponse(
        title = "Ugyldig $feltnavn",
        status = HttpStatusCode.BadRequest.value,
        detail = "$feltnavn må oppgis som en dato på formatet yyyy-MM-dd",
        instance = instance,
    )

private val NULLBELØP: BigDecimal = BigDecimal.ZERO.setScale(BELØPSSKALA)

private fun SumPerForsikringstype.tilApi(): UtbetalingsutbetaltesummerResponse.PerForsikringstype =
    UtbetalingsutbetaltesummerResponse.PerForsikringstype(
        kategori = forsikringstype.kategori(),
        forsikringstype = forsikringstype.navn(),
        utbetaltIVentetid = utbetaltIVentetid,
        utbetaltUtenomVentetid = utbetaltUtenomVentetid,
        totalt = totalt,
    )

private fun Forsikringstype.utenUtbetalinger(): UtbetalingsutbetaltesummerResponse.PerForsikringstype =
    UtbetalingsutbetaltesummerResponse.PerForsikringstype(
        kategori = kategori(),
        forsikringstype = navn(),
        utbetaltIVentetid = NULLBELØP,
        utbetaltUtenomVentetid = NULLBELØP,
        totalt = NULLBELØP,
    )

private fun Forsikringstype.kategori(): UtbetalingsutbetaltesummerResponse.Forsikringskategori =
    when (this) {
        is KollektivForsikring -> UtbetalingsutbetaltesummerResponse.Forsikringskategori.KOLLEKTIV
        is NavKjøptForsikringType -> UtbetalingsutbetaltesummerResponse.Forsikringskategori.NAV_KJØPT
    }

private fun Forsikringstype.navn(): String =
    when (this) {
        is KollektivForsikring -> name
        is NavKjøptForsikringType -> name
    }

data class UtbetalingsutbetaltesummerResponse(
    val fom: LocalDate,
    val tom: LocalDate,
    val perForsikringstype: List<PerForsikringstype>,
) {
    enum class Forsikringskategori {
        KOLLEKTIV,
        NAV_KJØPT,
    }

    data class PerForsikringstype(
        val kategori: Forsikringskategori,
        val forsikringstype: String,
        val utbetaltIVentetid: BigDecimal,
        val utbetaltUtenomVentetid: BigDecimal,
        val totalt: BigDecimal,
    )
}
