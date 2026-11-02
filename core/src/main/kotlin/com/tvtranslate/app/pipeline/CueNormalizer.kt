package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue

/**
 * Cleans up ASR output: sorts by time, removes empty/overlapping duplicates and
 * clamps overlapping cues so the player never shows two lines fighting.
 */
object CueNormalizer {

    fun normalize(input: List<Cue>): MutableList<Cue> {
        val out = mutableListOf<Cue>()
        for (cue in input.sortedBy { it.startMs }) {
            if (cue.text.isBlank() || cue.endMs <= cue.startMs) continue
            val prev = out.lastOrNull()
            if (prev != null) {
                if (cue.startMs < prev.endMs) {
                    val fixedEnd = maxOf(prev.startMs + 200, cue.startMs - 50)
                    if (fixedEnd <= prev.startMs) continue
                    out[out.size - 1] = prev.copy(endMs = fixedEnd)
                }
                val last = out.lastOrNull()
                if (last != null && last.text == cue.text && cue.startMs - last.endMs < 400) {
                    continue
                }
            }
            out.add(cue)
        }
        return out
    }
}
