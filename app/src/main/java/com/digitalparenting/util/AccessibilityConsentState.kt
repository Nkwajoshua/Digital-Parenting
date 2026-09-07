package com.digitalparenting.util

import android.content.Context

object AccessibilityConsentState {
    const val CURRENT_VERSION = 1

    private const val PREFS_NAME = "child_permission_setup"
    private const val KEY_ACCESSIBILITY_CONSENT_VERSION = "accessibility_consent_version"

    fun hasCurrentConsent(context: Context): Boolean {
        return prefs(context).getInt(KEY_ACCESSIBILITY_CONSENT_VERSION, 0) >= CURRENT_VERSION
    }

    fun recordCurrentConsent(context: Context) {
        prefs(context)
            .edit()
            .putInt(KEY_ACCESSIBILITY_CONSENT_VERSION, CURRENT_VERSION)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
