package fr.berliat.hskwidget.core

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

internal actual object PlatformLocaleManager {
    actual fun setLocale(languageCode: String?) {
        if (languageCode == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
        } else {
            NSUserDefaults.standardUserDefaults.setObject(listOf(languageCode), forKey = "AppleLanguages")
        }
    }

    actual fun getCurrentLocale(): String? {
        // App-level override set via setLocale(), if any.
        val languages = NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<*>
        (languages?.firstOrNull() as? String)?.let { return it }

        // No override: follow the system preferred language.
        return NSLocale.preferredLanguages.firstOrNull() as? String
    }
}