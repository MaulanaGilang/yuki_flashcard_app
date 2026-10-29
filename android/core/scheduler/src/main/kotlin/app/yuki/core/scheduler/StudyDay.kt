package app.yuki.core.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The study day starts at [startHour] local time (default 04:00, like Anki), so a session at
 * 1 am still counts toward the previous day.
 */
class StudyDay(private val zone: ZoneId, private val startHour: Int = 4) {

  fun dateOf(instant: Instant): LocalDate =
    instant.atZone(zone).minusHours(startHour.toLong()).toLocalDate()

  fun startOf(date: LocalDate): Instant = date.atTime(startHour, 0).atZone(zone).toInstant()

  fun start(instant: Instant): Instant = startOf(dateOf(instant))

  fun end(instant: Instant): Instant = startOf(dateOf(instant).plusDays(1))
}
