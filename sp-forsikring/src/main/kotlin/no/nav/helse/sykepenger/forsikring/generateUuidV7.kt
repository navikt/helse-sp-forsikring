package no.nav.helse.sykepenger.forsikring

import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
fun generateUuidV7(): UUID = Uuid.generateV7().toJavaUuid()
