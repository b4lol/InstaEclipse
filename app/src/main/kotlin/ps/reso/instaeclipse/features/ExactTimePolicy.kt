package ps.reso.instaeclipse.features

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Accept only plausible post times, never duration counters or future countdowns. */
object ExactTimePolicy {
    @JvmStatic
    fun format(epochMillis: Long, nowMillis: Long, locale: Locale, zone: ZoneId): String? {
        if (epochMillis < 1_230_768_000_000L || epochMillis > nowMillis) return null
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale).withZone(zone).format(Instant.ofEpochMilli(epochMillis))
    }
}
