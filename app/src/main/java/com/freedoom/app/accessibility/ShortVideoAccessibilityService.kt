package com.freedoom.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.ShortVideoEntity
import com.freedoom.app.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Cuenta vídeos cortos. Solo recibe eventos de las apps de [ShortVideoConfig] y
 * solo mira ids de vista y clases; nunca lee ni guarda textos ni imágenes.
 */
class ShortVideoAccessibilityService : AccessibilityService() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val dao by lazy { AppDatabase.get(this).shortVideoDao() }
    private var lastScreenCheck = 0L

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
    })

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tracker.stop(System.currentTimeMillis())
            LiveCounter.setInShortVideos(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            packageNames = ShortVideoConfig.enabledPlatforms.map { it.packageName }.toTypedArray()
        }
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        LiveCounter.setRunning(true)
        DebugLog.add("SERVICIO", "conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val platform = ShortVideoConfig.forPackage(pkg) ?: return
        val now = System.currentTimeMillis()
        tracker.onAnyEvent(pkg, now)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event, platform, now)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                logEvent(event)
                if (DebugLog.enabled) logScreenIds()
                checkScreen(platform, now, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkScreen(platform, now, force = false)
        }
        LiveCounter.setInShortVideos(tracker.inShortVideos)
    }

    private fun onScrolled(event: AccessibilityEvent, platform: ShortVideoPlatform, now: Long) {
        val source = event.source
        val viewId = source?.viewIdResourceName
        @Suppress("DEPRECATION") source?.recycle()
        val className = event.className?.toString()
        logEvent(event, viewId)

        val isPager = if (viewId != null) {
            viewId in platform.pagerViewIds
        } else {
            tracker.inShortVideos && className != null && className in platform.pagerClassNames
        }
        if (isPager) {
            tracker.onPagerScrolled(platform.packageName, event.fromIndex, event.toIndex, now)
        }
    }

    /** Busca en la ventana activa alguno de los ids que identifican la pantalla de vídeos cortos. */
    private fun checkScreen(platform: ShortVideoPlatform, now: Long, force: Boolean) {
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastScreenCheck < ShortVideoConfig.SCREEN_CHECK_THROTTLE_MS) return
        lastScreenCheck = elapsed
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != platform.packageName) return
        val matched = platform.screenViewIds.firstOrNull { id ->
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val visible = nodes.any { it.isVisibleToUser }
            @Suppress("DEPRECATION") nodes.forEach { it.recycle() }
            visible
        }
        val wasIn = tracker.inShortVideos
        tracker.onScreenState(platform.packageName, matched != null, now)
        if (wasIn != tracker.inShortVideos) {
            DebugLog.add("PANTALLA", if (tracker.inShortVideos) "dentro (id $matched)" else "fuera")
        }
    }

    private fun logEvent(event: AccessibilityEvent, viewId: String? = null) {
        if (!DebugLog.enabled) return
        val type = AccessibilityEvent.eventTypeToString(event.eventType).removePrefix("TYPE_")
        DebugLog.add(
            type,
            "clase=${event.className} id=${viewId ?: "-"} " +
                "from=${event.fromIndex} to=${event.toIndex} n=${event.itemCount} scrollY=${event.scrollY}",
        )
    }

    /** Solo depuración: lista los ids de vista visibles en la ventana actual (sin textos). */
    private fun logScreenIds() {
        val root = rootInActiveWindow ?: return
        val ids = sortedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            node.viewIdResourceName?.let { if (node.isVisibleToUser) ids += it.substringAfter(":id/") }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        DebugLog.add("IDS", ids.joinToString(", "))
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

    private var shutDown = false

    private fun shutdown() {
        if (shutDown) return
        shutDown = true
        tracker.stop(System.currentTimeMillis())
        runCatching { unregisterReceiver(screenOffReceiver) }
        LiveCounter.setRunning(false)
        DebugLog.add("SERVICIO", "detenido")
        // Deja terminar las escrituras pendientes antes de cancelar.
        scope.launch { scope.cancel() }
    }
}
