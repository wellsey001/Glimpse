package com.wellsey.glimpse.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private val dayThisYear = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val dayOtherYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())

/** "Today", "Yesterday", "Mon 3 Nov", or "Mon 3 Nov 2024". */
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now(zone)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(dayThisYear)
        else -> date.format(dayOtherYear)
    }
}

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(dateTime)

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
