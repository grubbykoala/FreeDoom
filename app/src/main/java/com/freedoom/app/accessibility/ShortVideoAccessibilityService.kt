package com.freedoom.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.ConsentStore
import com.freedoom.app.data.ShortVideoEntity
import com.freedoom.app.debug.DebugLog
import com.freedoom.app.ui.MainActivity
import com.freedoom.app.usage.UsageReader
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cuenta vídeos cortos. Solo recibe eventos de las apps de [ShortVideoConfig] y
 * solo mira ids de vista y clases; nunca lee ni guarda textos ni imágenes.
 * Sin el consentimiento aceptado en la app no cuenta, no guarda y no registra nada.
 */
class ShortVideoAccessibilityService : AccessibilityService() {

    private val errorHandler = CoroutineExceptionHandler { _, e -> report("guardar", e) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1) + errorHandler)
    private val dao by lazy { AppDatabase.get(this).shortVideoDao() }
    private val handler = Handler(Looper.getMainLooper())
    private var lastScreenCheck = 0L
    private var lastIdsDump = Long.MIN_VALUE / 2
    private var lastSmallMatch: String? = null

    /** Plataforma del último evento: la que miran las comprobaciones programadas. */
    private var activePlatform: ShortVideoPlatform? = null
    private var watching = false
    private var trailingPending = false
    private var shutDown = false

    private val tracker = ShortVideoTracker(listener = object : ShortVideoTracker.Listener {
        override fun onVideoStarted(packageName: String, at: Long, reason: String) {
            LiveCounter.onVideo(at, reason)
            DebugLog.add("VÍDEO+1", reason, at)
            scope.launch { dao.insert(ShortVideoEntity(packageName = packageName, startedAt = at)) }
        }

        override fun onVideoEnded(packageName: String, startedAt: Long, endedAt: Long) {
            DebugLog.add("VÍDEO FIN", "visto ${(endedAt - startedAt) / 1000.0} s", endedAt)
            scope.launch { dao.setEnded(packageName, startedAt, endedAt) }
        }

        override fun onResumed(packageName: String, at: Long, reason: String) {
            DebugLog.add("VUELTA", reason, at)
        }
    })

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = guarded("pantalla apagada") {
            cancelChecks()
            tracker.stop(System.currentTimeMillis())
            LiveCounter.setInShortVideos(false)
        }
    }

    /** Mientras se está en vídeos cortos, mira la pantalla cada segundo: así se nota la salida aunque no lleguen eventos. */
    private val watchdog = Runnable {
        watching = false
        if (tracker.inShortVideos) guarded("vigilante") { recheck() }
    }

    /** Comprobación final cuando el limitador descartó una: si no, un reel visto sin deslizar podría no contarse. */
    private val trailingCheck = Runnable {
        trailingPending = false
        guarded("comprobación final") { recheck() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!ConsentStore.hasConsent(this)) {
            // Activado en Ajustes sin aceptar el aviso en la app: se apaga solo. Si nunca se aceptó,
            // se abre la app en el aviso; si el usuario lo retiró, se respeta y no se abre nada.
            DebugLog.add("SERVICIO", "sin consentimiento: se desactiva")
            if (ServiceHealth.connectedAt(this) == null) {
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_SHOW_CONSENT, true)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            ),
                    )
                }
            }
            disableSelf()
            return
        }
        val packages = ShortVideoConfig.enabledPlatforms.map { it.packageName }
        serviceInfo?.let {
            // Una lista vacía significaría "todas las apps": en ese caso, ningún evento.
            if (packages.isEmpty()) {
                it.eventTypes = 0
            } else {
                it.packageNames = packages.toTypedArray()
            }
            serviceInfo = it
        }
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        LiveCounter.setRunning(true)
        DebugLog.add("SERVICIO", "conectado")
        checkForGap(System.currentTimeMillis())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = guarded("evento") { handleEvent(event) }

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val platform = ShortVideoConfig.forPackage(pkg) ?: return
        val now = eventTime(event)
        if (!ConsentStore.hasConsent(this)) {
            // Sin consentimiento (o retirado): no se cuenta, no se guarda y no se registra nada.
            pauseCounting(now)
            return
        }
        activePlatform = platform
        ServiceHealth.recordHeartbeat(this, now)
        tracker.onAnyEvent(pkg, now)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event, platform, now)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                logEvent(event)
                checkScreen(platform, now, force = true)?.let { logScreenIds(it, platform) }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkScreen(platform, now, force = false)
        }
        LiveCounter.setInShortVideos(tracker.inShortVideos)
        ensureWatching()
    }

    /** Hora en que ocurrió el evento, no la de proceso: si el hilo va con retraso, no se comprimen los huecos entre gestos. */
    private fun eventTime(event: AccessibilityEvent): Long {
        val lag = if (event.eventTime > 0) {
            (SystemClock.uptimeMillis() - event.eventTime).coerceIn(0L, ShortVideoConfig.MAX_EVENT_LAG_MS)
        } else {
            0L
        }
        return System.currentTimeMillis() - lag
    }

    private fun onScrolled(event: AccessibilityEvent, platform: ShortVideoPlatform, now: Long) {
        val source = event.source
        val viewId = source?.viewIdResourceName
        // Identidad del nodo (id de vista accesible + ventana), no su contenido: distingue un visor de otro.
        val viewer = source?.hashCode()
        // Instagram mantiene el visor de Reels montado aunque no se vea: solo cuenta si es visible.
        // Sin nodo no se puede saber: se acepta solo si ya estábamos dentro.
        val visible = source?.isVisibleToUser ?: tracker.inShortVideos
        // Sin id, una colección horizontal (carrusel de fotos) no es el pager de vídeos.
        val horizontal = viewId == null &&
            source?.collectionInfo?.let { it.columnCount > 1 || it.rowCount <= 1 } == true
        @Suppress("DEPRECATION") source?.recycle()
        val className = event.className?.toString()
        logEvent(event, viewId, visible)

        val isPager = visible && if (viewId != null) {
            viewId in platform.pagerViewIds
        } else {
            tracker.inShortVideos && !horizontal && className != null && className in platform.pagerClassNames
        }
        if (isPager) {
            tracker.onPagerScrolled(platform.packageName, event.fromIndex, event.toIndex, now, viewer)
        }
    }

    /**
     * Busca en la ventana activa alguno de los ids que identifican la pantalla de vídeos cortos,
     * visible y a un tamaño de pantalla completa. Devuelve la raíz si se inspeccionó y era de [platform].
     */
    private fun checkScreen(platform: ShortVideoPlatform, now: Long, force: Boolean): AccessibilityNodeInfo? {
        val elapsed = SystemClock.elapsedRealtime()
        val sinceLast = elapsed - lastScreenCheck
        if (!force && sinceLast < ShortVideoConfig.SCREEN_CHECK_THROTTLE_MS) {
            scheduleTrailingCheck(ShortVideoConfig.SCREEN_CHECK_THROTTLE_MS - sinceLast)
            return null
        }
        val root = rootInActiveWindow ?: return null
        lastScreenCheck = elapsed
        val wasIn = tracker.inShortVideos
        if (root.packageName?.toString() != platform.packageName) {
            // Otra app (o la cortina de notificaciones) delante: no se ven vídeos cortos. No se mira nada más.
            tracker.onScreenState(platform.packageName, false, now)
            if (wasIn && !tracker.inShortVideos) DebugLog.add("PANTALLA", "fuera (otra ventana delante)")
            return null
        }

        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)
        val rootHeight = rootBounds.height().coerceAtLeast(1)
        val minHeight = rootHeight * platform.minScreenFraction
        val bounds = Rect()
        var matched: String? = null
        var matchedHeight = 0
        var matchedViewer: Int? = null
        var small: String? = null
        for (id in platform.screenViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            var tallest = 0
            var tallestViewer: Int? = null
            for (node in nodes) {
                if (node.isVisibleToUser) {
                    node.getBoundsInScreen(bounds)
                    if (bounds.height() > tallest) {
                        tallest = bounds.height()
                        tallestViewer = node.hashCode()
                    }
                }
            }
            @Suppress("DEPRECATION") nodes.forEach { it.recycle() }
            if (tallest <= 0) continue
            if (tallest >= minHeight) {
                matched = id
                matchedHeight = tallest
                matchedViewer = tallestViewer
                break
            }
            if (small == null) small = "${shortId(id)} (alto ${tallest * 100 / rootHeight} %)"
        }

        tracker.onScreenState(platform.packageName, matched != null, now, viewer = matchedViewer)
        if (wasIn != tracker.inShortVideos) {
            DebugLog.add(
                "PANTALLA",
                if (tracker.inShortVideos) {
                    "dentro (id ${matched?.let(::shortId)}, alto ${matchedHeight * 100 / rootHeight} %, visor $matchedViewer)"
                } else {
                    "fuera"
                },
            )
        }
        // Calibración: ¿aparece algún id en pequeño (miniatura, vídeo del feed)? Solo se anota al cambiar.
        if (DebugLog.enabled && small != lastSmallMatch) {
            lastSmallMatch = small
            if (small != null) DebugLog.add("PANTALLA", "visible pero pequeño: $small")
        }
        return root
    }

    private fun scheduleTrailingCheck(delayMs: Long) {
        // Dentro de vídeos cortos ya mira el vigilante.
        if (trailingPending || watching || shutDown) return
        trailingPending = true
        handler.postDelayed(trailingCheck, delayMs.coerceAtLeast(0L))
    }

    /** Comprobación programada (vigilante o final), fuera de los eventos. */
    private fun recheck() {
        val platform = activePlatform ?: return
        if (shutDown) return
        val now = System.currentTimeMillis()
        if (!ConsentStore.hasConsent(this)) {
            pauseCounting(now)
            return
        }
        checkScreen(platform, now, force = true)
        if (tracker.inShortVideos) ServiceHealth.recordHeartbeat(this, now)
        LiveCounter.setInShortVideos(tracker.inShortVideos)
        ensureWatching()
    }

    private fun ensureWatching() {
        if (tracker.inShortVideos && !watching && !shutDown) {
            watching = true
            handler.postDelayed(watchdog, ShortVideoConfig.WATCH_INTERVAL_MS)
        }
    }

    private fun cancelChecks() {
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(trailingCheck)
        watching = false
        trailingPending = false
    }

    /** Deja de contar: cierra el vídeo abierto y para las comprobaciones. */
    private fun pauseCounting(now: Long) {
        cancelChecks()
        if (tracker.inShortVideos) tracker.stop(now)
        LiveCounter.setInShortVideos(false)
    }

    /**
     * Al (re)conectar: si desde el último latido Instagram se abrió sin que el servicio lo viera y
     * estuvo en primer plano más de un minuto, el contador estuvo parado sin avisar. Se guarda el
     * hueco para que el panel lo explique. Exigir una apertura no vista evita falsos avisos por
     * ratos en pantallas quietas de Instagram, que no generan eventos ni latido.
     * Tras un apagado ordenado no hay latido (ver [shutdown]), así que no se avisa de nada.
     */
    private fun checkForGap(now: Long) {
        val previous = ServiceHealth.lastHeartbeatAt(this)
        ServiceHealth.recordConnected(this, now)
        val context = applicationContext
        scope.launch {
            if (previous != null && now - previous > ServiceHealth.HEARTBEAT_INTERVAL_MS) {
                val reader = UsageReader(context)
                val from = previous + ServiceHealth.HEARTBEAT_INTERVAL_MS
                val missedOpen = reader.lastResumeAfter(ShortVideoConfig.INSTAGRAM_PACKAGE, from + MISSED_OPEN_MARGIN_MS, now)
                val missedMs = if (missedOpen != null) {
                    reader.foregroundMs(ShortVideoConfig.INSTAGRAM_PACKAGE, from, now)
                } else {
                    0L
                }
                if (missedMs > MIN_GAP_MS) {
                    ServiceHealth.saveGap(context, ServiceHealth.Gap(previous, now, missedMs))
                    DebugLog.add("SERVICIO", "estuvo parado: ${missedMs / 1000} s de Instagram sin contar")
                }
            }
            ServiceHealth.recordHeartbeat(context, System.currentTimeMillis(), force = true)
        }
    }

    private fun logEvent(event: AccessibilityEvent, viewId: String? = null, visible: Boolean? = null) {
        if (!DebugLog.enabled) return
        val type = AccessibilityEvent.eventTypeToString(event.eventType).removePrefix("TYPE_")
        // Solo ids de recurso: una etiqueta libre podría llevar contenido.
        val id = when {
            viewId == null -> "-"
            ":id/" in viewId -> viewId
            else -> "<etiqueta>"
        }
        // -1 = desconocido (Android 8 no da el desplazamiento).
        val delta = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "${event.scrollDeltaX}/${event.scrollDeltaY}"
        } else {
            "-1/-1"
        }
        DebugLog.add(
            type,
            "clase=${event.className} id=$id vis=${visible ?: "-"} " +
                "from=${event.fromIndex} to=${event.toIndex} n=${event.itemCount} dx/dy=$delta",
        )
    }

    /** Solo depuración: ids de vista visibles en la app vigilada (sin textos), como mucho cada pocos segundos. */
    private fun logScreenIds(root: AccessibilityNodeInfo, platform: ShortVideoPlatform) {
        if (!DebugLog.enabled || root.packageName?.toString() != platform.packageName) return
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastIdsDump < IDS_DUMP_INTERVAL_MS) return
        lastIdsDump = elapsed
        val ids = sortedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            // Solo ids de recurso (":id/"), nunca etiquetas libres.
            node.viewIdResourceName
                ?.takeIf { node.isVisibleToUser && ":id/" in it }
                ?.let { ids += shortId(it) }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        DebugLog.add("IDS", ids.joinToString(", "))
    }

    private fun shortId(id: String) = id.substringAfter(":id/")

    /** Un fallo aquí no debe tumbar el proceso (y con él el contador y el panel). */
    private fun guarded(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            report(where, e)
        }
    }

    private fun report(where: String, e: Throwable) {
        Log.w(TAG, "fallo en $where", e)
        DebugLog.add("ERROR", "$where: ${e.javaClass.simpleName}")
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (shutDown) return
        shutDown = true
        handler.removeCallbacksAndMessages(null)
        watching = false
        trailingPending = false
        tracker.stop(System.currentTimeMillis())
        // Apagado ordenado (interruptor de Ajustes o sin consentimiento): no es un hueco que avisar.
        // Forzar la detención o que el sistema mate el proceso no pasa por aquí y deja el latido.
        ServiceHealth.clearHeartbeat(this)
        runCatching { unregisterReceiver(screenOffReceiver) }
        LiveCounter.setRunning(false)
        DebugLog.add("SERVICIO", "detenido")
        // El scope no se cancela: así terminan las escrituras pendientes (como el último setEnded).
    }

    private companion object {
        const val TAG = "FreeDoom"

        /** Volcado de ids en depuración: como mucho uno cada este tiempo. */
        const val IDS_DUMP_INTERVAL_MS = 2_500L

        /** Tiempo de Instagram sin contar a partir del cual se guarda el hueco. */
        const val MIN_GAP_MS = 60_000L

        /** Margen por el ajuste de la hora de los eventos al buscar una apertura de Instagram no vista. */
        const val MISSED_OPEN_MARGIN_MS = 5_000L
    }
}
