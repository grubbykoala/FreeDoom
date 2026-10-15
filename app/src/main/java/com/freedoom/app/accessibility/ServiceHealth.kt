package com.freedoom.app.accessibility

import android.content.Context

/**
 * Latido del servicio de accesibilidad, para detectar cuándo NO ha estado contando
 * (fabricantes que lo matan, "forzar detención", Stop de Android Studio...).
 * Solo guarda marcas de tiempo, nunca contenido.
 */
object ServiceHealth {
    private const val PREFS = "freedoom_health"
    private const val KEY_LAST_HEARTBEAT = "a11y_last_event_at"
    private const val KEY_CONNECTED = "a11y_connected_at"
    private const val KEY_GAP_FROM = "gap_from"
    private const val KEY_GAP_TO = "gap_to"
    private const val KEY_GAP_MS = "gap_instagram_ms"
    private const val KEY_REELS_SEEN_AT = "reels_seen_at"
    private const val KEY_REELS_VIEWER = "reels_viewer"
    private const val REELS_SEEN_INTERVAL_MS = 10_000L

    /** Como mucho una escritura de latido por intervalo. */
    const val HEARTBEAT_INTERVAL_MS = 60_000L

    /** Hueco en el que Instagram estuvo en primer plano sin que el servicio contara. */
    data class Gap(val from: Long, val to: Long, val instagramMs: Long)

    @Volatile private var cachedHeartbeat: Long? = null
    @Volatile private var cachedReelsSeen: Long? = null

    /** Última vez que se estaba en vídeos cortos y con qué visor (su identidad técnica, no su contenido). */
    data class ReelsSeen(val at: Long, val viewer: Int?)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordConnected(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_CONNECTED, at).apply()
    }

    fun connectedAt(context: Context): Long? =
        prefs(context).getLong(KEY_CONNECTED, -1L).takeIf { it >= 0 }

    /** Llamar con cada evento recibido; solo escribe si ha pasado [HEARTBEAT_INTERVAL_MS]. */
    fun recordHeartbeat(context: Context, at: Long, force: Boolean = false) {
        val last = cachedHeartbeat ?: lastHeartbeatAt(context)
        // Los eventos llevan su propia hora y pueden llegar unos ms "antes" del último latido.
        if (!force && last != null && at - last in -HEARTBEAT_INTERVAL_MS + 1 until HEARTBEAT_INTERVAL_MS) return
        cachedHeartbeat = at
        prefs(context).edit().putLong(KEY_LAST_HEARTBEAT, at).apply()
    }

    /** Apagado ordenado del servicio: sin latido, la próxima conexión no avisa de ningún hueco. */
    fun clearHeartbeat(context: Context) {
        cachedHeartbeat = null
        prefs(context).edit().remove(KEY_LAST_HEARTBEAT).apply()
    }

    fun lastHeartbeatAt(context: Context): Long? =
        cachedHeartbeat ?: prefs(context).getLong(KEY_LAST_HEARTBEAT, -1L).takeIf { it >= 0 }

    /**
     * Mientras se está en vídeos cortos (como mucho cada 10 s): si el proceso se reinicia a mitad de
     * un reel, al volver al mismo visor no se cuenta otra vez.
     */
    fun recordReelsSeen(context: Context, at: Long, viewer: Int?) {
        val last = cachedReelsSeen
        if (last != null && at - last in 0 until REELS_SEEN_INTERVAL_MS) return
        cachedReelsSeen = at
        prefs(context).edit().apply {
            putLong(KEY_REELS_SEEN_AT, at)
            if (viewer != null) putInt(KEY_REELS_VIEWER, viewer) else remove(KEY_REELS_VIEWER)
        }.apply()
    }

    fun reelsSeen(context: Context): ReelsSeen? {
        val p = prefs(context)
        val at = p.getLong(KEY_REELS_SEEN_AT, -1L).takeIf { it >= 0 } ?: return null
        return ReelsSeen(at, if (p.contains(KEY_REELS_VIEWER)) p.getInt(KEY_REELS_VIEWER, 0) else null)
    }

    fun saveGap(context: Context, gap: Gap) {
        prefs(context).edit()
            .putLong(KEY_GAP_FROM, gap.from)
            .putLong(KEY_GAP_TO, gap.to)
            .putLong(KEY_GAP_MS, gap.instagramMs)
            .apply()
    }

    fun lastGap(context: Context): Gap? {
        val p = prefs(context)
        val from = p.getLong(KEY_GAP_FROM, -1L)
        if (from < 0) return null
        return Gap(from, p.getLong(KEY_GAP_TO, from), p.getLong(KEY_GAP_MS, 0L))
    }

    fun clearGap(context: Context) {
        prefs(context).edit().remove(KEY_GAP_FROM).remove(KEY_GAP_TO).remove(KEY_GAP_MS).apply()
    }
}
