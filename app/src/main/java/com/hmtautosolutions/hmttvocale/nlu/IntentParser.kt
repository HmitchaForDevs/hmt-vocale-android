package com.hmtautosolutions.hmttvocale.nlu

/**
 * Deterministic, offline, multilingual (FR / EN / AR + some Algerian Darija) intent parser.
 * Matches many phrasings per intent and scores them; low scores become UNKNOWN so the
 * assistant asks for clarification instead of guessing.
 * Darija phrases are included but NOT validated with real speakers / recognizer output.
 */
object IntentParser {

    const val MIN_CONFIDENCE = 0.6f

    private class Rule(val type: IntentType, phrases: List<String>) {
        val phrases: List<String> = phrases.map { TextNormalizer.normalize(it) }
    }

    private val unsafePhrases = listOf(
        "coupe le moteur", "eteins le moteur", "arrete le moteur", "frein", "freins", "airbag",
        "turn off the engine", "stop the engine", "engine off", "brake", "brakes", "disable abs",
        "اطفئ المحرك", "طفي الموتور", "الفرامل", "الفران", "الايرباغ"
    ).map { TextNormalizer.normalize(it) }

    private val rules = listOf(
        Rule(IntentType.CLIMATE_OFF, listOf(
            "eteins la clim", "coupe la clim", "arrete la clim", "eteins la climatisation",
            "coupe la climatisation", "arrete la climatisation", "j ai froid",
            "turn off the air conditioning", "turn off the ac", "switch off the ac", "ac off", "i am cold", "i m cold",
            "اطفئ المكيف", "طفي المكيف", "طفي الكليم", "سكر الكليماتيزور", "راني بردان", "اوقف المكيف")),
        Rule(IntentType.CLIMATE_ON, listOf(
            "j ai chaud", "il fait chaud", "trop chaud", "mets la clim", "allume la clim", "allume la climatisation",
            "mets la climatisation", "climatisation", "clim",
            "turn on the air conditioning", "turn on the ac", "air conditioning", "i m hot", "i am hot", "it s hot",
            "too hot", "so hot",
            "شغل المكيف", "المكيف", "حر", "راني سخون", "سخون", "شعل الكليم", "الكليم", "الكليماتيزور")),
        Rule(IntentType.TEMP_UP, listOf(
            "augmente la temperature", "monte la temperature", "plus chaud", "increase the temperature",
            "temperature up", "warmer", "ارفع الحراره", "ارفع الحرارة", "زيد الحرارة", "زيد الحراره")),
        Rule(IntentType.TEMP_DOWN, listOf(
            "baisse la temperature", "diminue la temperature", "plus frais", "decrease the temperature",
            "lower the temperature", "temperature down", "cooler", "اخفض الحرارة", "نقص الحرارة", "نقص الحراره")),
        Rule(IntentType.FAN_UP, listOf(
            "augmente la ventilation", "ventilation plus fort", "plus de ventilation", "fan up", "increase the fan",
            "more air", "زيد الهواء", "زيد الفونتيلاتور", "ارفع المروحه")),
        Rule(IntentType.FAN_DOWN, listOf(
            "baisse la ventilation", "diminue la ventilation", "moins de ventilation", "fan down", "decrease the fan",
            "less air", "نقص الهواء", "اخفض المروحه")),
        Rule(IntentType.WINDOWS_OPEN, listOf(
            "ouvre les vitres", "ouvre la vitre", "ouvre les fenetres", "ouvre la fenetre", "baisse les vitres",
            "baisse la vitre", "open the windows", "open the window", "roll down the windows", "windows down",
            "افتح النوافذ", "افتح النافذه", "افتح النافذة", "حل الشباك", "حل الزاج", "افتح الشبابك")),
        Rule(IntentType.WINDOWS_CLOSE, listOf(
            "ferme les vitres", "ferme la vitre", "ferme les fenetres", "ferme la fenetre", "monte les vitres",
            "remonte les vitres", "close the windows", "close the window", "roll up the windows", "windows up",
            "اغلق النوافذ", "اغلق النافذه", "سكر الشباك", "سكر الزاج", "سكر النوافذ")),
        Rule(IntentType.MEDIA_PLAY, listOf(
            "lecture", "reprends", "reprends la musique", "mets la musique", "joue de la musique", "play",
            "play music", "resume", "resume music", "شغل الموسيقى", "كمل الموسيقى", "شغل الغنيه")),
        Rule(IntentType.MEDIA_PAUSE, listOf(
            "pause", "mets en pause", "stop la musique", "arrete la musique", "pause the music", "stop the music",
            "stop music", "وقف الموسيقى", "حبس الموسيقى", "وقف الغنيه", "حبس")),
        Rule(IntentType.MEDIA_NEXT, listOf(
            "suivant", "chanson suivante", "piste suivante", "next", "next song", "next track", "skip",
            "التالي", "الغنيه الجايه", "اللي بعدها")),
        Rule(IntentType.MEDIA_PREVIOUS, listOf(
            "precedent", "chanson precedente", "piste precedente", "previous", "previous song", "go back",
            "السابق", "اللي قبلها")),
        Rule(IntentType.VOLUME_UP, listOf(
            "monte le son", "augmente le son", "augmente le volume", "monte le volume", "plus fort",
            "volume up", "louder", "turn it up", "turn up the volume",
            "ارفع الصوت", "علي الصوت", "زيد الصوت", "طلع الصوت")),
        Rule(IntentType.VOLUME_DOWN, listOf(
            "baisse le son", "baisse le volume", "diminue le volume", "moins fort",
            "volume down", "quieter", "turn it down", "turn down the volume",
            "اخفض الصوت", "نقص الصوت", "هبط الصوت")),
        Rule(IntentType.VOLUME_MUTE, listOf(
            "coupe le son", "silence", "mode silencieux", "mute", "mute the sound",
            "اكتم الصوت", "كتم الصوت", "قطع الصوت")),
        Rule(IntentType.IDENTITY, listOf(
            "qui a cree", "qui t a cree", "qui a fait cette application", "qui es tu", "tu es qui",
            "who created", "who made", "who built", "who are you",
            "من انشا", "من صنع", "من انت", "شكون دارك", "شكون صنعك", "شكون انت", "شكون دار")),
        Rule(IntentType.TIME, listOf(
            "quelle heure", "l heure", "what time", "the time", "كم الساعه", "كم الساعة", "شحال الساعه", "شحال الساعة")),
        Rule(IntentType.HELP, listOf(
            "aide", "aide moi", "que peux tu faire", "qu est ce que tu sais faire", "help", "what can you do",
            "مساعده", "ساعدني", "واش تقدر دير", "ماذا تستطيع"))
    )

    private val languageWords = mapOf(
        "fr" to listOf("francais", "french", "الفرنسيه", "الفرنسية", "بالفرنسيه", "بالفرنسية", "فرنسي", "بالفرونسي"),
        "en" to listOf("anglais", "english", "الانجليزيه", "الانجليزية", "بالانجليزيه", "بالانجليزية", "انجليزي", "بالنقليزي"),
        "ar" to listOf("arabe", "arabic", "العربيه", "العربية", "بالعربيه", "بالعربية", "عربي", "بالعربي")
    ).mapValues { (_, v) -> v.map { TextNormalizer.normalize(it) } }

    private val languageVerbs = listOf("parle", "parles", "reponds", "passe en", "speak", "talk", "switch to",
        "answer in", "in", "en", "تكلم", "هدر", "احكي", "بدل", "جاوب").map { TextNormalizer.normalize(it) }

    private val openVerbs = listOf("ouvre", "ouvrir", "lance", "lancer", "demarre", "affiche", "open", "launch",
        "start", "show", "افتح", "فتح", "حل", "شغل", "شعل").map { TextNormalizer.normalize(it) }

    private val navPatterns = listOf(
        Regex("(?:emmene moi|amene moi|conduis moi|guide moi|navigue|itineraire|direction)\\s+(?:a|au|aux|vers|jusqu a|pour)?\\s*(.+)"),
        Regex("(?:navigate to|take me to|directions to|drive to|navigate)\\s+(.+)"),
        Regex("(?:خذني الى|خذني ل|خذني|ديني ل|ديني|وديني ل|وديني|روح ل)\\s*(.+)")
    )

    private val volumeSet = Regex("(?:volume|son|الصوت)\\D{0,8}(\\d{1,3})")
    private val tempSet = Regex("(\\d{2})\\s*(?:degres|degre|degrees|degree|درجه|درجة|درجات)")
    private val tempSet2 = Regex("(?:temperature|الحراره|الحرارة)\\D{0,8}(\\d{2})")

    private val yesWords = setOf("oui", "ouais", "confirme", "vas y", "yes", "yeah", "confirm", "sure", "ok", "okay",
        "نعم", "ايه", "اه", "واه", "واخا", "اكيد", "wakha").map { TextNormalizer.normalize(it) }
    private val noWords = setOf("non", "annule", "no", "nope", "cancel", "لا", "لالا", "بلاش", "الغي")
        .map { TextNormalizer.normalize(it) }

    fun isYes(text: String): Boolean = matchesAnyPhrase(TextNormalizer.stripWakeWord(text).second, yesWords)
    fun isNo(text: String): Boolean = matchesAnyPhrase(TextNormalizer.stripWakeWord(text).second, noWords)

    private fun matchesAnyPhrase(t: String, phrases: Collection<String>): Boolean {
        val padded = " $t "
        return phrases.any { padded.contains(" $it ") }
    }

    private fun phraseConfidence(phrase: String): Float {
        val words = phrase.count { it == ' ' } + 1
        return (0.6f + 0.1f * words).coerceAtMost(0.95f)
    }

    fun parse(raw: String): CommandIntent {
        val t = TextNormalizer.stripWakeWord(raw).second
        if (t.isBlank()) return CommandIntent(IntentType.UNKNOWN, 0f, normalizedText = t)
        val padded = " $t "

        if (unsafePhrases.any { padded.contains(" $it ") }) {
            return CommandIntent(IntentType.UNSAFE, 1f, normalizedText = t)
        }

        val candidates = mutableListOf<CommandIntent>()

        // Language change
        val verbPresent = languageVerbs.any { padded.contains(" $it ") }
        for ((lang, words) in languageWords) {
            if (words.any { padded.contains(" $it ") }) {
                candidates += CommandIntent(IntentType.CHANGE_LANGUAGE, if (verbPresent) 0.92f else 0.62f,
                    mapOf("lang" to lang), t)
            }
        }

        // Explicit volume level
        volumeSet.find(t)?.let { m ->
            val v = m.groupValues[1].toIntOrNull()
            if (v != null && v in 0..100) candidates += CommandIntent(IntentType.VOLUME_SET, 0.93f,
                mapOf("value" to v.toString()), t)
        }

        // Explicit temperature
        (tempSet.find(t) ?: tempSet2.find(t))?.let { m ->
            val v = m.groupValues[1].toIntOrNull()
            if (v != null && v in 16..32) candidates += CommandIntent(IntentType.TEMP_SET, 0.93f,
                mapOf("value" to v.toString()), t)
        }

        // Navigation
        for (p in navPatterns) {
            val m = p.find(t) ?: continue
            val dest = m.groupValues[1].trim()
            if (dest.isNotEmpty()) {
                candidates += CommandIntent(IntentType.NAVIGATE, 0.9f, mapOf("destination" to dest), t)
                break
            }
        }

        // Applications
        AppCatalog.find(t)?.let { app ->
            val hasVerb = openVerbs.any { padded.contains(" $it ") }
            candidates += CommandIntent(IntentType.OPEN_APP, if (hasVerb) 0.92f else 0.65f,
                mapOf("app" to app.key), t)
        }

        // Keyword rules
        for (rule in rules) {
            var best = 0f
            for (ph in rule.phrases) {
                if (padded.contains(" $ph ")) best = maxOf(best, phraseConfidence(ph))
            }
            if (best > 0f) candidates += CommandIntent(rule.type, best, normalizedText = t)
        }

        val top = candidates.maxByOrNull { it.confidence }
        if (top == null || top.confidence < MIN_CONFIDENCE) {
            return CommandIntent(IntentType.UNKNOWN, top?.confidence ?: 0f, normalizedText = t)
        }
        return top
    }
}
