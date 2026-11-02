package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue

/** Parses SRT and ASS/SSA subtitle files into a flat cue list. */
object SubtitleParser {

    private val TIME_RE = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val TAG_RE = Regex("""<[^>]+>""")
    private val ASS_TAG_RE = Regex("""\{[^}]*}""")

    fun parse(content: String, fileName: String): List<Cue> {
        val name = fileName.lowercase()
        val cues = when {
            name.endsWith(".ass") || name.endsWith(".ssa") || content.contains("[Script Info]") ->
                parseAss(content)
            else -> parseSrt(content)
        }
        return cues
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
            .sortedBy { it.startMs }
    }

    private fun parseSrt(content: String): List<Cue> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        val out = mutableListOf<Cue>()
        val blocks = normalized.split(Regex("""\n\s*\n"""))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex < 0) continue
            val halves = lines[timeIndex].split("-->")
            if (halves.size < 2) continue
            val start = parseTime(halves[0]) ?: continue
            val end = parseTime(halves[1]) ?: continue
            val text = lines.drop(timeIndex + 1).joinToString(" ").clean()
            if (text.isNotBlank()) out.add(Cue(start, end, text))
        }
        return out
    }

    private fun parseAss(content: String): List<Cue> {
        val out = mutableListOf<Cue>()
        var inEvents = false
        for (raw in content.replace("\r\n", "\n").lines()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inEvents = line.contains("Events", ignoreCase = true)
                continue
            }
            if (!inEvents || !line.startsWith("Dialogue:", ignoreCase = true)) continue
            val body = line.substringAfter(":")
            val parts = body.split(",", limit = 10)
            if (parts.size < 10) continue
            val start = parseTime(parts[1].trim()) ?: continue
            val end = parseTime(parts[2].trim()) ?: continue
            val text = parts[9].replace("\\N", " ").replace("\\n", " ").clean()
            if (text.isNotBlank()) out.add(Cue(start, end, text))
        }
        return out
    }

    private fun parseTime(raw: String): Long? {
        val m = TIME_RE.find(raw) ?: return null
        val h = m.groupValues[1].toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val fracRaw = m.groupValues[4]
        val ms = when (fracRaw.length) {
            1 -> fracRaw.toLong() * 100
            2 -> fracRaw.toLong() * 10
            else -> fracRaw.take(3).toLong()
        }
        return ((h * 60 + min) * 60 + sec) * 1000 + ms
    }

    private fun String.clean(): String {
        var s = this
        s = s.replace(ASS_TAG_RE, "")
        s = s.replace(TAG_RE, "")
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        s = s.replace("&nbsp;", " ")
        s = s.replace(Regex("""\s+"""), " ")
        return s.trim()
    }
}
