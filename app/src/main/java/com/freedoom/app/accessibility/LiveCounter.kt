package com.freedoom.app.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Estado en memoria del servicio, para el panel y el modo depuración. */
object LiveCounter {
    data class State(
        val serviceRunning: Boolean = false,
        val inShortVideos: Boolean = false,
        /** Vídeos contados desde que arrancó el servicio. */
        val countSinceStart: Int = 0,
        /** Tanda de calibración: null si no hay ninguna en marcha. */
        val calibrationCount: Int? = null,
        val lastReason: String? = null,
        val lastVideoAt: Long? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setRunning(running: Boolean) = _state.update { it.copy(serviceRunning = running, inShortVideos = false) }

    fun setInShortVideos(value: Boolean) {
        if (_state.value.inShortVideos != value) _state.update { it.copy(inShortVideos = value) }
    }

    fun onVideo(at: Long, reason: String) = _state.update {
        it.copy(
            countSinceStart = it.countSinceStart + 1,
            calibrationCount = it.calibrationCount?.plus(1),
            lastReason = reason,
            lastVideoAt = at,
        )
    }

    fun startCalibration() = _state.update { it.copy(calibrationCount = 0) }
    fun stopCalibration() = _state.update { it.copy(calibrationCount = null) }
}
