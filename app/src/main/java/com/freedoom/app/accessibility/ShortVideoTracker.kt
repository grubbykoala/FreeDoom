package com.freedoom.app.accessibility

/**
 * Decide cuándo empieza y termina cada vídeo corto a partir de señales simples.
 * No depende de Android para poder probarlo con tests normales.
 *
 * Reglas:
 *  - Entrar en la pantalla de vídeos cortos cuenta el vídeo que ya se está viendo.
 *  - Si el pager informa del índice, cada índice "asentado" (from == to) distinto
 *    del anterior es un vídeo nuevo. Los eventos a mitad de deslizamiento se ignoran,
 *    así un mismo gesto nunca cuenta dos veces.
 *  - Si no hay índice, cada ráfaga de eventos de scroll separada de la anterior por
 *    más de [gestureGapMs] cuenta como un gesto = un vídeo.
 *  - Volver atrás a un vídeo anterior también cuenta: es un vídeo visto otra vez.
 */
class ShortVideoTracker(
    private val gestureGapMs: Long = ShortVideoConfig.GESTURE_GAP_MS,
    private val exitGraceMs: Long = ShortVideoConfig.EXIT_GRACE_MS,
    private val maxSilentGapMs: Long = ShortVideoConfig.MAX_SILENT_GAP_MS,
    private val listener: Listener,
) {
    interface Listener {
        fun onVideoStarted(packageName: String, at: Long, reason: String)
        fun onVideoEnded(packageName: String, startedAt: Long, endedAt: Long)
    }

    var inShortVideos = false
        private set
    private var packageName: String? = null
    private var currentStart: Long? = null
    private var lastIndex: Int? = null
    private var lastScrollAt = Long.MIN_VALUE / 2
    private var notDetectedSince: Long? = null
    private var lastEventAt: Long? = null

    /** Llamar con cada evento recibido de una app vigilada, antes que el resto. */
    fun onAnyEvent(pkg: String, now: Long) {
        val last = lastEventAt
        if (inShortVideos && (pkg != packageName || (last != null && now - last > maxSilentGapMs))) {
            // El usuario se fue (no recibimos eventos de otras apps): cerramos en el último evento visto.
            exit(last ?: now)
        }
        lastEventAt = now
    }

    /** Resultado de inspeccionar la pantalla: ¿se ve la pantalla de vídeos cortos? */
    fun onScreenState(pkg: String, detected: Boolean, now: Long) {
        if (detected) {
            notDetectedSince = null
            if (!inShortVideos) enter(pkg, now)
        } else if (inShortVideos) {
            val since = notDetectedSince ?: now.also { notDetectedSince = it }
            if (now - since >= exitGraceMs) exit(since)
        }
    }

    /**
     * Evento de scroll del pager. [fromIndex]/[toIndex] valen -1 si la vista no los da.
     * Si llega un scroll del pager, por fuerza estamos en la pantalla de vídeos cortos.
     */
    fun onPagerScrolled(pkg: String, fromIndex: Int, toIndex: Int, now: Long) {
        val wasIn = inShortVideos
        onScreenState(pkg, detected = true, now = now)
        val gap = now - lastScrollAt
        lastScrollAt = now

        if (fromIndex >= 0 && toIndex >= 0) {
            val known = lastIndex
            if (fromIndex != toIndex) {
                // A mitad de gesto: si aún no sabíamos el índice, el de partida es el actual.
                if (known == null) lastIndex = fromIndex
                return
            }
            lastIndex = fromIndex
            if (known != null && known != fromIndex) newVideo(now, "índice $known→$fromIndex")
            return
        }

        // Sin índices: un gesto nuevo es un vídeo nuevo. El primer scroll tras entrar
        // pertenece al gesto que sale del vídeo ya contado al entrar, así que sí cuenta.
        if (gap > gestureGapMs) newVideo(now, if (wasIn) "gesto" else "gesto (entrada)")
    }

    /** Pantalla apagada o servicio parado: cerrar lo que esté abierto. */
    fun stop(now: Long) {
        if (inShortVideos) exit(now)
    }

    private fun enter(pkg: String, now: Long) {
        inShortVideos = true
        packageName = pkg
        lastIndex = null
        notDetectedSince = null
        start(now, "entrada en vídeos cortos")
    }

    private fun exit(at: Long) {
        endCurrent(at)
        inShortVideos = false
        lastIndex = null
        notDetectedSince = null
    }

    private fun newVideo(now: Long, reason: String) {
        endCurrent(now)
        start(now, reason)
    }

    private fun start(now: Long, reason: String) {
        // Dos vídeos no pueden empezar en el mismo milisegundo (clave única en Room).
        val prev = currentStart
        val at = if (prev != null && now <= prev) prev + 1 else now
        currentStart = at
        listener.onVideoStarted(packageName!!, at, reason)
    }

    private fun endCurrent(at: Long) {
        val start = currentStart ?: return
        currentStart = null
        listener.onVideoEnded(packageName!!, start, maxOf(at, start))
    }
}
