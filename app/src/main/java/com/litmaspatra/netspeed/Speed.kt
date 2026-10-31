package com.litmaspatra.netspeed

import java.util.Locale

object Speed {
    /** bytes/s -> (number, unit). KB/s up to 999.9, then MB/s. */
    fun fmt(b: Long): Pair<String, String> {
        val kb = b / 1000.0
        if (kb < 1000) return Pair(String.format(Locale.US, "%.1f", kb), "KB/s")
        val mb = kb / 1000
        val f = if (mb < 10) "%.2f" else if (mb < 100) "%.1f" else "%.0f"
        return Pair(String.format(Locale.US, f, mb), "MB/s")
    }

    fun full(b: Long): String {
        val (n, u) = fmt(b)
        return "$n $u"
    }
}
