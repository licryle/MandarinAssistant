package fr.berliat.hskwidget.core

object LocaleManager {
    val supportedLocales = mapOf(
        "en" to "English",
        "fr" to "Français",
        "zh-Hans" to "简体中文"
    )

    fun setLocale(languageCode: String?) {
        PlatformLocaleManager.setLocale(languageCode)
    }

    fun getCurrentLocale(): String {
        val platformLocale = PlatformLocaleManager.getCurrentLocale()
        if (platformLocale != null) {
            // Exact match first (e.g. "en", "fr", "zh-Hans")
            if (supportedLocales.containsKey(platformLocale)) {
                return platformLocale
            }

            // Then language-prefix match (e.g. "fr-FR" -> "fr", "en-US" -> "en").
            val lang = platformLocale.split("-", "_")[0].lowercase()
            supportedLocales.keys.firstOrNull {
                it.split("-", "_")[0].lowercase() == lang
            }?.let { return it }

            // Simplified Chinese, the only Chinese UI we support.
            if (lang == "zh") return "zh-Hans"
        }
        return supportedLocales.keys.first()
    }
}

internal expect object PlatformLocaleManager {
    fun setLocale(languageCode: String?)
    fun getCurrentLocale(): String?
}
