package com.freedoom.app.data

import android.content.Context

/**
 * Consentimiento expreso para el servicio de accesibilidad.
 * Lo comparten la UI (que lo pide) y el servicio (que no cuenta nada sin él).
 */
object ConsentStore {
    private const val PREFS = "freedoom"
    private const val KEY_CONSENT = "accessibility_consent"

    fun hasConsent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONSENT, false)

    fun set(context: Context, given: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, given).apply()
    }
}
