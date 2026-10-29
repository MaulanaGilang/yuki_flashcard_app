package app.yuki.core.model

/** Yuki's mascot poses; each has a matching drawable. */
enum class Mood { WAVE, CELEBRATE, WORRIED, SLEEPY }

/** What Yuki says and how she looks, on Today and in the streak widget. */
data class CoachLine(val mood: Mood, val message: String, val short: String)

/** Everything the coach needs to know about today. */
data class DayStatus(
  val streak: Int,
  val studiedToday: Boolean,
  val dueToday: Int,
  /** Local hour 0–23. */
  val hour: Int,
  val hasLines: Boolean,
)

/**
 * Picks Yuki's mood and line (PRD S19). Rules, in order:
 * no languages yet → wave hello; studied today → celebrate (or sleepy late at night);
 * streak at risk in the evening → worried; otherwise → wave with today's work.
 */
object YukiCoach {
  const val EVENING_HOUR = 18

  fun line(s: DayStatus): CoachLine {
    val night = s.hour >= 22 || s.hour < 4
    return when {
      !s.hasLines -> CoachLine(Mood.WAVE, "Hi, I'm Yuki! Pick a language and we'll start your first line together.", "Let's start!")
      s.studiedToday && night -> CoachLine(Mood.SLEEPY, "All done for today. Sleep well, I'll see you tomorrow!", "Done today ✓")
      s.studiedToday && s.dueToday > 0 ->
        CoachLine(Mood.CELEBRATE, "${streakText(s.streak)} is safe! ${s.dueToday} more cards are ready if you want them.", "Streak safe ✓")
      s.studiedToday -> CoachLine(Mood.CELEBRATE, "${streakText(s.streak)} is safe. You've done everything for today!", "Streak safe ✓")
      s.streak > 0 && (s.hour >= EVENING_HOUR || night) ->
        CoachLine(Mood.WORRIED, "Your ${s.streak}-day streak ends at 4 am. One short session keeps it alive!", "Keep your streak!")
      s.streak > 0 -> CoachLine(Mood.WAVE, "${s.dueToday} cards today. Let's make it ${s.streak + 1} days!", "Study today")
      s.dueToday > 0 -> CoachLine(Mood.WAVE, "${s.dueToday} cards are waiting. Start a streak today!", "Start a streak")
      else -> CoachLine(Mood.SLEEPY, "Nothing is due right now. Come back a little later!", "All clear")
    }
  }

  private fun streakText(streak: Int) = if (streak <= 1) "Your streak" else "Your $streak-day streak"
}
