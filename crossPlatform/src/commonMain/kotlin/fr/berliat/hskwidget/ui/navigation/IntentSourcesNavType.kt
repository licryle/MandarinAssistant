package fr.berliat.hskwidget.ui.navigation

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write

import fr.berliat.hskwidget.core.IntentSources

/**
 * Explicit NavType for [IntentSources].
 *
 * Required because automatic enum mapping (SerialKind.ENUM) is Android-only
 * in Navigation 2.9.x — on iOS the framework throws "could not find any
 * NavType" for the `source` argument without this. Unknown/stale values
 * fall back to IN_APP instead of crashing (e.g. restored backstack entries).
 */
val IntentSourcesNavType = object : NavType<IntentSources>(isNullableAllowed = false) {
    override fun put(bundle: SavedState, key: String, value: IntentSources) {
        bundle.write { putString(key, value.name) }
    }

    @Suppress("DEPRECATION") // getString(key): library uses the same suppression
    override fun get(bundle: SavedState, key: String): IntentSources? {
        val name: String? = bundle.read {
            if (contains(key) && !isNull(key)) getString(key) else null
        }
        return name?.let { runCatching { IntentSources.valueOf(it) }.getOrDefault(IntentSources.IN_APP) }
    }

    override fun parseValue(value: String): IntentSources =
        runCatching { IntentSources.valueOf(value) }.getOrDefault(IntentSources.IN_APP)

    override fun serializeAsValue(value: IntentSources): String = value.name
}
