package app.yuki.core.model.script

/** Spelling-to-sound rules that trip up English readers, per Latin-script language. */
object LatinRules {
  private fun rule(spelling: String, sounds: String, example: String, pattern: String) =
    SpellingRule(spelling, sounds, example, Regex(pattern))

  private fun guide(title: String, intro: String, rules: List<SpellingRule>) = object : ScriptGuide {
    override val title = title
    override val intro = intro
    override val alphabet = emptyList<Letter>()
    override val rules = rules
  }

  val German: ScriptGuide = guide(
    "Reading German",
    "German is spelled almost exactly as it sounds. Learn these patterns and you can read any word. Nouns always start with a capital letter.",
    listOf(
      rule("ei", "\"eye\"", "mein", "ei"),
      rule("ie", "\"ee\"", "Liebe", "ie"),
      rule("eu / äu", "\"oy\"", "heute, Häuser", "eu|äu"),
      rule("w", "\"v\"", "Wasser", "w"),
      rule("v", "\"f\"", "Vater", "v"),
      rule("z", "\"ts\"", "Zeit", "z"),
      rule("j", "\"y\"", "ja", "j"),
      rule("sch", "\"sh\"", "Schule", "sch"),
      rule("st / sp at the start", "\"sht\" / \"shp\"", "Straße, Sport", "^(st|sp)"),
      rule("ch after a, o, u", "a throaty \"kh\"", "Buch", "[aou]ch"),
      rule("ch after e, i, ä, ö, ü", "a soft hiss, like a whispered \"h\" in huge", "ich", "(?<![aous])ch"),
      rule("ß", "\"ss\"", "Straße", "ß"),
      rule("ä", "\"e\" as in bed", "Äpfel", "ä"),
      rule("ö", "\"e\" said with rounded lips", "schön", "ö"),
      rule("ü", "\"ee\" said with rounded lips", "Tür", "ü"),
      rule("final b / d / g", "p / t / k", "Hund", "[bdg]$"),
    ),
  )

  val French: ScriptGuide = guide(
    "Reading French",
    "Many final letters in French are silent, and some letter groups make one sound. Stress falls lightly on the last syllable.",
    listOf(
      rule("final e, s, t, d, x", "silent", "petit, grand", "[stdx]$|[^é]e$"),
      rule("ou", "\"oo\"", "nous", "ou"),
      rule("oi", "\"wa\"", "moi", "oi"),
      rule("eau / au", "\"oh\"", "beau, chaud", "eau|au"),
      rule("ai / ei", "\"e\" as in bed", "lait", "ai|ei"),
      rule("an / en", "a nasal \"ahn\"", "enfant", "an|en"),
      rule("on", "a nasal \"ohn\"", "bon", "on"),
      rule("in / ain / ein", "a nasal \"an\"", "vin", "in|ain|ein"),
      rule("u", "\"ee\" said with rounded lips", "tu", "(?<![aeo])u(?!n)"),
      rule("ch", "\"sh\"", "chat", "ch"),
      rule("gn", "\"ny\" as in canyon", "montagne", "gn"),
      rule("h", "silent", "homme", "h"),
      rule("ç", "\"s\"", "garçon", "ç"),
      rule("é", "\"ay\" (short)", "café", "é"),
      rule("è / ê", "\"e\" as in bed", "mère, tête", "[èê]"),
      rule("j / g before e, i", "\"zh\" as in measure", "je, rouge", "j|g[eiy]"),
    ),
  )

  val Spanish: ScriptGuide = guide(
    "Reading Spanish",
    "Spanish is read exactly as written, with five pure vowels. The accent mark shows the stressed syllable.",
    listOf(
      rule("j / g before e, i", "a throaty \"h\"", "jamón, gente", "j|g[ei]"),
      rule("h", "silent", "hola", "(?<!c)h"),
      rule("ll", "\"y\"", "llamar", "ll"),
      rule("ñ", "\"ny\" as in canyon", "mañana", "ñ"),
      rule("rr / r at the start", "a rolled r", "perro, rojo", "rr|^r"),
      rule("c before e, i / z", "\"th\" in Spain, \"s\" in Latin America", "cena, zapato", "c[ei]|z"),
      rule("qu", "\"k\"", "queso", "qu"),
      rule("v", "\"b\"", "vino", "v"),
      rule("ue / ie", "\"weh\" / \"yeh\"", "bueno, tiempo", "ue|ie"),
    ),
  )
}
