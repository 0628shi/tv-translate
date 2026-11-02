package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal OpenAI-compatible speech-to-text client (`POST /audio/transcriptions`).
 * Works with OpenAI, SiliconFlow, self-hosted faster-whisper gateways, etc.
 */
class AsrClient(private val baseUrl: String, private val apiKey: String) {

    /** True when the last response had no segment timestamps and timing was estimated. */
    var lastFallback: Boolean = false
        private set

    fun transcribe(
        file: File,
        model: String,
        language: String?,
        offsetMs: Long,
        fallbackDurationMs: Long
    ): List<Cue> {
        val boundary = "----TvTranslate" + System.nanoTime()
        val head = ByteArrayOutputStream()

        fun textPart(name: String, value: String) {
            head.write(("--$boundary\r\n").toByteArray())
            head.write(("Content-Disposition: form-data; name=\"$name\"\r\n\r\n").toByteArray())
            head.write(value.toByteArray())
            head.write("\r\n".toByteArray())
        }

        textPart("model", model)
        textPart("response_format", "verbose_json")
        textPart("temperature", "0")
        if (!language.isNullOrBlank()) textPart("language", language)

        head.write(("--$boundary\r\n").toByteArray())
        head.write(
            ("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n").toByteArray()
        )
        head.write("Content-Type: audio/wav\r\n\r\n".toByteArray())

        val headBytes = head.toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val totalLength = headBytes.size.toLong() + file.length() + tail.size

        val endpoint = baseUrl.trimEnd('/') + "/audio/transcriptions"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 300_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setFixedLengthStreamingMode(totalLength)
        }

        try {
            conn.outputStream.use { out ->
                out.write(headBytes)
                file.inputStream().use { it.copyTo(out) }
                out.write(tail)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("语音识别失败 HTTP $code：${text.take(300)}")
            }
            return parseResponse(text, offsetMs, fallbackDurationMs)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseResponse(raw: String, offsetMs: Long, fallbackDurationMs: Long): List<Cue> {
        val root = JSONObject(raw)
        val out = mutableListOf<Cue>()
        lastFallback = false
        val segments = root.optJSONArray("segments")
        if (segments != null && segments.length() > 0) {
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONObject(i) ?: continue
                val start = offsetMs + (seg.optDouble("start", 0.0) * 1000).toLong()
                val end = offsetMs + (seg.optDouble("end", 0.0) * 1000).toLong()
                val text = seg.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    out.add(Cue(start, maxOf(end, start + 300), text))
                }
            }
        }
        if (out.isEmpty()) {
            val text = root.optString("text", "").trim()
            if (text.isNotEmpty()) {
                lastFallback = true
                out.addAll(splitIntoCues(text, offsetMs, fallbackDurationMs))
            }
        }
        return out
    }

    private fun splitIntoCues(text: String, offsetMs: Long, durationMs: Long): List<Cue> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            if (ch in "。！？!?；;\n") {
                val s = sb.toString().trim()
                if (s.isNotEmpty()) parts.add(s)
                sb.clear()
            }
        }
        if (sb.isNotBlank()) parts.add(sb.toString().trim())
        if (parts.isEmpty()) return emptyList()

        val totalChars = parts.sumOf { it.length }.coerceAtLeast(1)
        val safeDuration = if (durationMs > 0) durationMs else parts.size * 2000L
        var cursor = offsetMs
        val endLimit = offsetMs + safeDuration
        return parts.map { part ->
            val d = (safeDuration * part.length / totalChars).coerceIn(800L, 8000L)
            val end = (cursor + d).coerceAtMost(endLimit)
            val cue = Cue(cursor, maxOf(end, cursor + 500), part)
            cursor = end
            cue
        }
    }
}
