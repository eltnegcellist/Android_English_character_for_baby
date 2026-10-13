package com.eltnegcellist.emma.ai

import android.content.SharedPreferences

/** Default to the new Ruri Topic × Interaction replies without overwriting opt-outs. */
internal object LiteInteractionSetting {
    const val KEY = "interaction_experimental_enabled"
    const val DEFAULT_ENABLED = true

    fun isEnabled(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(KEY, DEFAULT_ENABLED)

    fun setEnabled(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit().putBoolean(KEY, enabled).apply()
    }
}
