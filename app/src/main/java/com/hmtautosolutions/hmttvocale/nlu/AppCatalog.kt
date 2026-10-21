package com.hmtautosolutions.hmttvocale.nlu

/** Registry of launchable apps. Add new entries here to support more apps. */
data class AppEntry(
    val key: String,
    val displayName: String,
    val aliases: List<String>,
    val packages: List<String> = emptyList(),
    val category: String? = null,
    val openSystemSettings: Boolean = false
)

object AppCatalog {
    val entries: List<AppEntry> = listOf(
        AppEntry("youtube", "YouTube", listOf("youtube", "you tube", "يوتيوب", "يوتوب"),
            listOf("com.google.android.youtube", "com.google.android.youtube.tv")),
        AppEntry("spotify", "Spotify", listOf("spotify", "سبوتيفاي"), listOf("com.spotify.music")),
        AppEntry("deezer", "Deezer", listOf("deezer", "ديزر"), listOf("deezer.android.app")),
        AppEntry("anghami", "Anghami", listOf("anghami", "انغامي"), listOf("com.anghami")),
        AppEntry("waze", "Waze", listOf("waze", "ويز"), listOf("com.waze")),
        AppEntry("maps", "Google Maps", listOf("google maps", "maps", "carte", "خرائط", "الخريطه", "الخريطة"),
            listOf("com.google.android.apps.maps")),
        AppEntry("chrome", "Chrome", listOf("chrome", "navigateur", "browser", "كروم", "المتصفح"),
            listOf("com.android.chrome")),
        AppEntry("whatsapp", "WhatsApp", listOf("whatsapp", "واتساب"), listOf("com.whatsapp")),
        AppEntry("music", "Music", listOf("musique", "music", "الموسيقى", "موسيقى"),
            category = "android.intent.category.APP_MUSIC"),
        AppEntry("settings", "Settings", listOf("parametres", "reglages", "settings", "الاعدادات", "الإعدادات"),
            openSystemSettings = true)
    )

    private val normalizedAliases: List<Pair<String, AppEntry>> by lazy {
        entries.flatMap { e -> e.aliases.map { TextNormalizer.normalize(it) to e } }
            .sortedByDescending { it.first.length }
    }

    fun find(normalizedText: String): AppEntry? {
        val padded = " $normalizedText "
        return normalizedAliases.firstOrNull { padded.contains(" ${it.first} ") }?.second
    }

    fun byKey(key: String): AppEntry? = entries.firstOrNull { it.key == key }
}
