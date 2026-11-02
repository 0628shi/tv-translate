package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue
import java.util.Locale

/** Serializes cues to SRT. */
object SrtCodec {

    fun toSrt(cues: List<Cue>): String {
        val sb = StringBuilder()
        cues.forEachIndexed { i, c ->
            sb.append(i + 1).append('\n')
            sb.append(formatTime(c.startMs)).append(" --> ").append(formatTime(c.endMs)).append('\n')
            sb.append(c.translation.ifBlank { c.text }).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3_600_000
        val m = (total % 3_600_000) / 60_000
        val s = (total % 60_000) / 1000
        val milli = total % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, milli)
    }
}
