package com.freedoom.app.accessibility

/**
 * Decide cuándo empieza y termina cada vídeo corto a partir de señales simples.
 * No depende de Android para poder probarlo con tests normales.
 *
 * Reglas:
 *  - Entrar en la pantalla de vídeos cortos cuenta el vídeo que ya se está viendo.
 *  - Volver al mismo vídeo antes de [resumeWindowMs] (pantalla apagada, otra app, un
 *    perfil...) es una reanudación: no cuenta otra vez. El siguiente deslizamiento sí.
 *    "El mismo" = mismo visor (identidad de la vista, sin leer contenido) y mismo índice:
 *    un reel abierto desde un mensaje crea otro visor y sí cuenta.
 *  - Si el pager informa del índice, cada índice "asentado" (from == to) distinto
 *    del anterior es un vídeo nuevo. Los eventos a mitad de deslizamiento se ignoran,
 *    así un mismo gesto nunca cuenta dos veces. Durante [openSettleMs] tras entrar, los
 *    índices solo fijan la referencia: al abrir un reel, Instagram coloca el pager a saltos.
 *  - Si no hay índice, cada ráfaga de eventos de scroll separada de la anterior por
 *    más de [gestureGapMs] cuenta como un gesto = un vídeo.
 *  - Volver atrás deslizando a un vídeo anterior también cuenta: es un vídeo visto otra vez.
 */
class ShortVideoTracker(
    private val gestureGapMs: Long = ShortVideoConfig.GESTURE_GAP_MS,
    private val exitGraceMs: Long = ShortVideoConfig.EXIT_GRACE_MS,
    private val maxSilentGapMs: Long = ShortVideoConfig.MAX_SILENT_GAP_MS,
    private val resumeWindowMs: Long = ShortVideoConfig.RESUME_WINDOW_MS,
    private val openSettleMs: Long = ShortVideoConfig.OPEN_SETTLE_MS,
    private val listener: Listener,
) {
    interface Listener {
        fun onVideoStarted(packageName: String, at: Long, reason: String)
        fun onVideoEnded(packageName: String, startedAt: Long, endedAt: Long)

        /** Vuelta al mismo vídeo: no cuenta nada, solo sirve para el registro de depuración. */
        fun onResumed(packageName: String, at: Long, reason: String) {}
    }

    private data class Exit(val pkg: String, val index: Int?, val viewer: Int?, val at: Long)

    var inShortVideos = false
        private set
    private var packageName: String? = null
    private var currentStart: Long? = null
    private var lastIndex: Int? = null

    /** Identidad del visor que se está viendo (no su contenido), si se conoce. */
    private var currentViewer: Int? = null
    private var lastScrollAt = Long.MIN_VALUE / 2
    private var notDetectedSince: Long? = null

    /** Último momento en que sabemos que el usuario estaba en la app: un evento o la pantalla vista. */
    private var lastSeenAt: Long? = null
    private var enteredAt = Long.MIN_VALUE / 2
    private var lastStartAt = Long.MIN_VALUE / 2
    private var lastExit: Exit? = null

    /** Índice con el que se salió, si se acaba de reanudar (para reconocer otro reel abierto al volver). */
    private var resumedIndex: Int? = null

    /** Llamar con cada evento recibido de una app vigilada, antes que el resto. */
    fun onAnyEvent(pkg: String, now: Long) {
        val last = lastSeenAt
        if (inShortVideos && (pkg != packageName || (last != null && now - last > maxSilentGapMs))) {
            // El usuario se fue (no recibimos eventos de otras apps): cerramos en el último momento visto.
            exit(last ?: now)
        }
        lastSeenAt = now
    }

    /**
     * Resultado de inspeccionar la pantalla: ¿se ve la pantalla de vídeos cortos?
     * [pageIndex] es el índice del vídeo visible y [viewer] la identidad del visor, si se conocen.
     */
    fun onScreenState(pkg: String, detected: Boolean, now: Long, pageIndex: Int? = null, viewer: Int? = null) {
        if (detected) {
            notDetectedSince = null
            if (!inShortVideos) enter(pkg, now, pageIndex, viewer) else if (viewer != null) currentViewer = viewer
            lastSeenAt = now
        } else if (inShortVideos) {
            val since = notDetectedSince ?: now.also { notDetectedSince = it }
            if (now - since >= exitGraceMs) exit(since)
        }
    }

    /**
     * Evento de scroll del pager. [fromIndex]/[toIndex] valen -1 si la vista no los da.
     * Solo se llama con el pager visible en pantalla, así que también sirve para entrar.
     */
    fun onPagerScrolled(pkg: String, fromIndex: Int, toIndex: Int, now: Long, viewer: Int? = null) {
        val wasIn = inShortVideos
        onScreenState(pkg, detected = true, now = now, viewer = viewer)
        val gap = now - lastScrollAt
        lastScrollAt = now

        if (fromIndex >= 0 && toIndex >= 0) {
            if (settling(now)) {
                val resumed = resumedIndex
                if (resumed != null) {
                    // A mitad de gesto tras volver: aún no se sabe dónde acaba (puede rebotar).
                    if (fromIndex != toIndex) return
                    if (toIndex != resumed) {
                        // Al poco de volver, el visor está en otro reel: ese sí cuenta.
                        resumedIndex = null
                        newVideo(now, "otro vídeo al volver (índice $resumed→$toIndex)")
                    }
                }
                lastIndex = toIndex
                return
            }
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

    /** Pantalla apagada o servicio parado: cerrar lo que esté abierto en el último momento visto. */
    fun stop(now: Long) {
        if (inShortVideos) exit(minOf(now, lastSeenAt ?: now))
    }

    /** Primeros [openSettleMs] tras entrar. Tolera eventos que se procesan con algo de retraso. */
    private fun settling(now: Long) = now - enteredAt in -ShortVideoConfig.MAX_EVENT_LAG_MS until openSettleMs

    private fun enter(pkg: String, now: Long, pageIndex: Int?, viewer: Int?) {
        val prev = lastExit
        lastExit = null
        inShortVideos = true
        packageName = pkg
        notDetectedSince = null
        enteredAt = now
        currentViewer = viewer
        if (prev != null && prev.pkg == pkg && now - prev.at <= resumeWindowMs &&
            (pageIndex == null || prev.index == null || pageIndex == prev.index) &&
            (viewer == null || prev.viewer == null || viewer == prev.viewer)
        ) {
            // El mismo vídeo de antes: no es uno nuevo. Este tramo no se mide.
            lastIndex = pageIndex ?: prev.index
            resumedIndex = lastIndex
            if (currentViewer == null) currentViewer = prev.viewer
            listener.onResumed(pkg, now, "reanudación (índice ${lastIndex ?: "?"}, visor ${currentViewer ?: "?"})")
        } else {
            lastIndex = pageIndex
            resumedIndex = null
            start(now, "entrada en vídeos cortos")
        }
    }

    private fun exit(at: Long) {
        packageName?.let { lastExit = Exit(it, lastIndex, currentViewer, at) }
        endCurrent(at)
        inShortVideos = false
        lastIndex = null
        currentViewer = null
        resumedIndex = null
        notDetectedSince = null
    }

    private fun newVideo(now: Long, reason: String) {
        endCurrent(now)
        start(now, reason)
    }

    private fun start(now: Long, reason: String) {
        // Dos vídeos no pueden empezar en el mismo milisegundo (clave única en Room). Solo se
        // corrige un choque cercano, para que un retroceso del reloj no encadene marcas.
        val at = if (now <= lastStartAt && lastStartAt - now < 1_000) lastStartAt + 1 else now
        lastStartAt = at
        currentStart = at
        listener.onVideoStarted(packageName!!, at, reason)
    }

    private fun endCurrent(at: Long) {
        val start = currentStart ?: return
        currentStart = null
        listener.onVideoEnded(packageName!!, start, maxOf(at, start))
    }
}
