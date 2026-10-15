package com.freedoom.app.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freedoom.app.accessibility.ServiceHealth
import com.freedoom.app.accessibility.ShortVideoConfig
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.DayRange
import com.freedoom.app.usage.DayUsage
import com.freedoom.app.usage.UsageReader
import com.freedoom.app.usage.UsageRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** Una fila de "Por app". [packageName] es la clave de la lista (o una clave propia para filas agrupadas). */
data class AppRow(val packageName: String, val label: String, val ms: Long, val icon: ImageBitmap? = null)

data class UsageUi(
    val totalMs: Long,
    val unlocks: Int?,
    val unlocksAreScreenOns: Boolean,
    val apps: List<AppRow>,
)

/** Señales de que el contador de reels no ha estado contando. */
data class CounterHealth(
    /** Hueco ya terminado que detectó el servicio al volver a conectarse. */
    val gap: ServiceHealth.Gap? = null,
    /** Último paso de Instagram a primer plano que el servicio no llegó a ver, o null si todo cuadra. */
    val missedInstagramAt: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UsageRepository(app)
    private val reader = UsageReader(app)
    private val videoDao = AppDatabase.get(app).shortVideoDao()
    private val day = MutableStateFlow(DayRange.today())

    // Un fallo inesperado no debe tumbar el proceso: el contador de reels vive en el mismo.
    private val errorHandler = CoroutineExceptionHandler { _, e -> Log.w(TAG, "fallo en el panel", e) }

    private var refreshJob: Job? = null
    private val iconCache = HashMap<String, ImageBitmap?>()
    private var dismissedGap: ServiceHealth.Gap? = null

    private val _usage = MutableStateFlow<UsageUi?>(null)
    val usage: StateFlow<UsageUi?> = _usage

    private val _health = MutableStateFlow(CounterHealth())
    val health: StateFlow<CounterHealth> = _health

    /** null mientras se lee la base de datos. */
    val reelsToday: StateFlow<Int?> = day
        .flatMapLatest { videoDao.observeCount(it.start, it.end) }
        .stateIn(viewModelScope + errorHandler, SharingStarted.WhileSubscribed(5_000), null)

    val averageWatchMs: StateFlow<Double?> = day
        .flatMapLatest { videoDao.observeAverageWatchMs(it.start, it.end) }
        .stateIn(viewModelScope + errorHandler, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        val today = DayRange.today()
        if (today != day.value) day.value = today
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(errorHandler) {
            try {
                _usage.value = withContext(Dispatchers.IO) { repo.refresh(today)?.let(::toUi) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo leer el uso", e)
            }
            try {
                _health.value = withContext(Dispatchers.IO) { readHealth(System.currentTimeMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo comprobar el contador", e)
            }
        }
    }

    /** El usuario ya ha visto el aviso del hueco. */
    fun dismissGap() {
        dismissedGap = _health.value.gap
        _health.update { it.copy(gap = null) }
        ServiceHealth.clearGap(getApplication<Application>())
    }

    private fun readHealth(now: Long): CounterHealth {
        val app = getApplication<Application>()
        val gap = ServiceHealth.lastGap(app)?.takeIf { it != dismissedGap }
        val beat = ServiceHealth.lastHeartbeatAt(app) ?: return CounterHealth(gap = gap)
        // Si Instagram pasó a primer plano después del último latido (+ margen), el servicio
        // tendría que haber recibido eventos y escrito otro latido: no los está recibiendo.
        val base = maxOf(beat, ServiceHealth.connectedAt(app) ?: beat)
        val after = maxOf(base + ServiceHealth.HEARTBEAT_INTERVAL_MS + 5_000, now - LIVE_CHECK_WINDOW_MS)
        val missed = ShortVideoConfig.enabledPlatforms
            .mapNotNull { reader.lastResumeAfter(it.packageName, after, now) }
            .maxOrNull()
        return CounterHealth(gap = gap, missedInstagramAt = missed)
    }

    private fun toUi(usage: DayUsage): UsageUi {
        val app = getApplication<Application>()
        val pm = app.packageManager
        val iconPx = (ICON_DP * app.resources.displayMetrics.density).roundToInt()
        val rows = ArrayList<AppRow>()
        // Paquetes que Android no deja consultar o que ya no existen: se agrupan por nombre legible.
        val fallback = LinkedHashMap<String, Long>()
        for ((pkg, ms) in usage.perApp) {
            val info = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            if (info != null) {
                rows += AppRow(pkg, pm.getApplicationLabel(info).toString(), ms, iconOf(pm, info, iconPx))
            } else {
                val label = systemPackageLabel(pkg) ?: OTHERS_LABEL
                fallback[label] = (fallback[label] ?: 0L) + ms
            }
        }
        for ((label, ms) in fallback) rows += AppRow("$GROUP_KEY_PREFIX$label", label, ms)
        rows.sortByDescending { it.ms }
        return UsageUi(usage.totalMs, usage.unlocks, usage.unlocksAreScreenOns, rows)
    }

    /** Icono de la app, convertido una sola vez por paquete. */
    private fun iconOf(pm: PackageManager, info: ApplicationInfo, sizePx: Int): ImageBitmap? {
        synchronized(iconCache) {
            if (iconCache.containsKey(info.packageName)) return iconCache[info.packageName]
        }
        val icon = try {
            pm.getApplicationIcon(info).toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (e: Exception) {
            null
        }
        synchronized(iconCache) { iconCache[info.packageName] = icon }
        return icon
    }

    private companion object {
        const val TAG = "FreeDoom"
        const val ICON_DP = 32
        const val OTHERS_LABEL = "Sistema y otras"
        const val GROUP_KEY_PREFIX = "grupo:"
        /** La comprobación en vivo solo mira el último día. */
        const val LIVE_CHECK_WINDOW_MS = 24 * 3_600_000L
    }
}
