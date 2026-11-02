package com.tvtranslate.app.model

/**
 * One subtitle cue. [text] is the recognized/imported source line,
 * [translation] is filled in by the translator.
 */
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    var translation: String = ""
) {
    val display: String get() = translation.ifBlank { text }
}
