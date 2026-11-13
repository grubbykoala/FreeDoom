package com.freedoom.app.debug

import android.content.Context

/**
 * Tanda de calibración del modo depuración, guardada en el móvil para que sobreviva
 * si el proceso muere a mitad (Stop de Android Studio, un fallo, el fabricante...).
 * Solo guarda números y marcas de tiempo.
 */
object CalibrationStore {
    private const val PREFS = "freedoom_debug"
    private const val KEY_NUMBER = "calib_number"
    private const val KEY_START = "calib_start"
    private const val KEY_LAST_RESULT = "calib_last_result"

    /** Tanda en marcha: su número y cuándo empezó (epoch ms). */
    data class Batch(val number: Int, val startedAt: Long)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** La tanda en marcha, o null si no hay ninguna. */
    fun current(context: Context): Batch? {
        val p = prefs(context)
        val start = p.getLong(KEY_START, -1L)
        if (start < 0) return null
        return Batch(p.getInt(KEY_NUMBER, 1), start)
    }

    /** Empieza una tanda nueva con el siguiente número. */
    fun start(context: Context, at: Long): Batch {
        val p = prefs(context)
        val batch = Batch(p.getInt(KEY_NUMBER, 0) + 1, at)
        p.edit().putInt(KEY_NUMBER, batch.number).putLong(KEY_START, at).apply()
        return batch
    }

    /** Termina la tanda en marcha y guarda la línea de resultado para seguir viéndola. */
    fun finish(context: Context, result: String) {
        prefs(context).edit().remove(KEY_START).putString(KEY_LAST_RESULT, result).apply()
    }

    fun lastResult(context: Context): String? = prefs(context).getString(KEY_LAST_RESULT, null)
}
