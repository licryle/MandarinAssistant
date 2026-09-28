package fr.berliat.hskwidget.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal actual object PlatformLocaleManager {
    actual fun setLocale(languageCode: String?) {
        val appLocale: LocaleListCompat = if (languageCode == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    actual fun getCurrentLocale(): String? {
        // App-level override set via setLocale(), if any.
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) return locales.get(0)?.toLanguageTag()

        // No override: follow the system locale.
        val adjusted = LocaleListCompat.getAdjustedDefault()
        if (!adjusted.isEmpty) return adjusted.get(0)?.toLanguageTag()
        return java.util.Locale.getDefault().toLanguageTag()
    }
}
