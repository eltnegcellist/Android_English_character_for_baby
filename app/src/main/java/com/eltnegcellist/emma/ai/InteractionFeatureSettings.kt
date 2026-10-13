package com.eltnegcellist.emma.ai

import android.content.SharedPreferences

/** Default ON for new and existing installations with no saved preference.
 * Explicit user OFF is preserved across updates and app restarts.
 */
object InteractionFeatureSettings {
    const val KEY = "interaction_experimental_enabled"
    const val DEFAULT_ENABLED = true

    fun isEnabled(readBoolean: (String, Boolean) -> Boolean): Boolean =
        readBoolean(KEY, DEFAULT_ENABLED)

    fun isEnabled(preferences: SharedPreferences): Boolean =
        isEnabled { key, default -> preferences.getBoolean(key, default) }
}
