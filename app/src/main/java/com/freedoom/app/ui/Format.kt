package com.freedoom.app.ui

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 -> "$h h $m min"
        totalMinutes > 0 -> "$m min"
        else -> "${ms / 1000} s"
    }
}
