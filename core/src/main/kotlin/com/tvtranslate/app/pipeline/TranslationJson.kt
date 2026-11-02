package com.tvtranslate.app.pipeline

/**
 * Extracts the JSON string array returned by an LLM, tolerating code fences and
 * surrounding prose. Falls back to line splitting, then to the originals.
 */
object TranslationJson {

    fun parseArray(content: String, expected: Int, originals: List<String>): List<String> {
        var s = content.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            if (s.endsWith("```")) s = s.dropLast(3).trim()
        }
        val start = s.indexOf('[')
        if (start >= 0) {
            val parsed = tryParseArray(s, start)
            if (parsed != null && parsed.size == expected) return parsed
        }
        val lines = s.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotEmpty() }
        if (lines.size == expected) return lines
        return originals
    }

    private fun tryParseArray(s: String, start: Int): List<String>? {
        val out = mutableListOf<String>()
        var i = start + 1
        val n = s.length
        while (i < n) {
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n) return null
            when (s[i]) {
                ']' -> return out
                ',' -> {
                    i++
                    continue
                }
                '"' -> Unit
                else -> return null
            }
            i++
            val sb = StringBuilder()
            var closed = false
            while (i < n) {
                val c = s[i]
                if (c == '\\') {
                    if (i + 1 >= n) return null
                    val e = s[i + 1]
                    i += 2
                    when (e) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > n) return null
                            val code = s.substring(i, i + 4).toIntOrNull(16) ?: return null
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                } else if (c == '"') {
                    i++
                    closed = true
                    break
                } else {
                    sb.append(c)
                    i++
                }
            }
            if (!closed) return null
            out.add(sb.toString())
        }
        return null
    }
}
