package com.freedoom.app.debug

import com.freedoom.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Registro de eventos de accesibilidad para descubrir cómo se identifica cada pantalla.
 * Solo en builds debug y solo en memoria. Guarda tipo, clase e id de vista: nunca textos.
 */
object DebugLog {
    data class Entry(val time: Long, val kind: String, val detail: String)

    private const val MAX_ENTRIES = 400
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    val enabled: Boolean get() = BuildConfig.DEBUG

    fun add(kind: String, detail: String, time: Long = System.currentTimeMillis()) {
        if (!enabled) return
        _entries.update { (it + Entry(time, kind, detail)).takeLast(MAX_ENTRIES) }
    }

    fun clear() = _entries.update { emptyList() }

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    fun format(entry: Entry) = "${timeFormat.format(Instant.ofEpochMilli(entry.time))}  ${entry.kind}  ${entry.detail}"

    fun export(): String = _entries.value.joinToString("\n") { format(it) }
}
