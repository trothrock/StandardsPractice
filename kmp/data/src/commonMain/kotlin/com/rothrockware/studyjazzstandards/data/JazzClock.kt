package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.DateInt.toDateInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Time source for the repository — injectable so tests can pin "today". */
interface JazzClock {
    fun nowEpochMillis(): Long

    /** Today's date as integer yyyymmdd in the relevant timezone. */
    fun today(): Int
}

@OptIn(ExperimentalTime::class)
class SystemJazzClock(
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : JazzClock {
    override fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

    override fun today(): Int =
        Clock.System.now().toLocalDateTime(timeZone).date.toDateInt()
}

class FixedJazzClock(
    var todayInt: Int,
    var nowMillis: Long = 0L,
) : JazzClock {
    override fun nowEpochMillis(): Long = nowMillis
    override fun today(): Int = todayInt
}
