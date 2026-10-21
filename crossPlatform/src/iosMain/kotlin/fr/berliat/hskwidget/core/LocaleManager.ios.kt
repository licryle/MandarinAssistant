package fr.berliat.hskwidget.core

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

internal actual object PlatformLocaleManager {
    private const val APP_GROUP = "group.net.vertex8.hskwidgetassist2"
    private val appGroupDefaults: NSUserDefaults
        get() = NSUserDefaults(suiteName = APP_GROUP)

    actual fun setLocale(languageCode: String?) {
        if (languageCode == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
            appGroupDefaults.removeObjectForKey("AppleLanguages")
        } else {
            val languages = listOf(languageCode)
            NSUserDefaults.standardUserDefaults.setObject(languages, forKey = "AppleLanguages")
            appGroupDefaults.setObject(languages, forKey = "AppleLanguages")
        }
    }

    actual fun getCurrentLocale(): String? {
        // App-level override set via setLocale(), stored in App Group defaults for extension access.
        val groupLanguages = appGroupDefaults.objectForKey("AppleLanguages") as? List<*>
        val overrideLanguage = (groupLanguages?.firstOrNull() as? String)
            ?: ((NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<*>)?.firstOrNull() as? String)

        if (overrideLanguage != null) {
            val currentLocal = (NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<*>)?.firstOrNull() as? String
            if (currentLocal != overrideLanguage) {
                NSUserDefaults.standardUserDefaults.setObject(listOf(overrideLanguage), forKey = "AppleLanguages")
            }
            return overrideLanguage
        } else {
            if (NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") != null) {
                NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
            }
        }

        // No override: follow the system preferred language.
        return NSLocale.preferredLanguages.firstOrNull() as? String
    }
}