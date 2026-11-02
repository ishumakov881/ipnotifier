package com.example.ipnotifier.ui

/** Compact duration like `5 ч 12 мин`, `45 сек`, `2 д 3 ч`. */
fun formatDuration(durationMs: Long): String {
    val totalSec = (durationMs.coerceAtLeast(0L) / 1000L)
    val days = totalSec / 86_400L
    val hours = (totalSec % 86_400L) / 3_600L
    val minutes = (totalSec % 3_600L) / 60L
    val seconds = totalSec % 60L
    return when {
        days > 0L -> buildString {
            append("$days д")
            if (hours > 0L) append(" $hours ч")
        }
        hours > 0L -> buildString {
            append("$hours ч")
            if (minutes > 0L) append(" $minutes мин")
        }
        minutes > 0L -> "$minutes мин"
        else -> "$seconds сек"
    }
}
