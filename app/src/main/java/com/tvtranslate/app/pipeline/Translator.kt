package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat-completions translator. Sends cues in small batches,
 * with a rolling window of previous lines and an optional glossary so names and
 * terms stay consistent across an episode.
 */
class Translator(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) {

    var lastError: String? = null
        private set

    fun translate(
        cues: MutableList<Cue>,
        targetLang: String,
        glossary: String,
        batchSize: Int = 20,
        log: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isActive: () -> Boolean = { true }
    ) {
        if (cues.isEmpty()) return

        val system = buildString {
            append("你是专业的影视字幕翻译。把用户给出的 JSON 字符串数组逐条翻译成")
            append(targetLang)
            append("，输出等长的 JSON 字符串数组，只输出数组本身，不要解释、不要代码块。\n")
            append("要求：口语化、简短；中文每条不超过 20 字，英文不超过 42 个字符；")
            append("保留人名和称谓习惯；不要合并、拆分或省略条目；无法翻译时保留原文。")
        }

        var index = 0
        while (index < cues.size) {
            if (!isActive()) throw InterruptedException("已取消")
            val end = minOf(index + batchSize, cues.size)
            val batch = cues.subList(index, end)
            val originals = batch.map { it.text }

            val context = buildString {
                val from = maxOf(0, index - 4)
                if (from < index) {
                    append("前文参考：\n")
                    for (i in from until index) {
                        append("原文：").append(cues[i].text).append('\n')
                        append("译文：").append(cues[i].translation).append('\n')
                    }
                    append('\n')
                }
                if (glossary.isNotBlank()) {
                    append("术语表：\n").append(glossary.trim()).append("\n\n")
                }
                append("待翻译（JSON 数组，共 ").append(originals.size).append(" 条）：\n")
                append(JSONArray(originals).toString())
            }

            try {
                val result = requestTranslation(system, context)
                val parsed = parseArray(result, originals.size, originals)
                for (i in originals.indices) {
                    cues[index + i].translation = parsed[i].trim()
                }
            } catch (e: Exception) {
                lastError = e.message
                log("第 ${index / batchSize + 1} 批翻译失败：${e.message}")
            }

            index = end
            onProgress(index, cues.size)
        }
    }

    private fun requestTranslation(system: String, user: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                }
            )
        }.toString()

        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("翻译请求失败 HTTP $code：${text.take(300)}")
            }
            val json = JSONObject(text)
            val choices = json.optJSONArray("choices")
                ?: throw IOException("翻译返回格式异常：${text.take(300)}")
            if (choices.length() == 0) throw IOException("翻译返回为空")
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: throw IOException("翻译返回缺少 message")
            return message.optString("content", "")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseArray(content: String, expected: Int, originals: List<String>): List<String> {
        var s = content.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            if (s.endsWith("```")) s = s.dropLast(3).trim()
        }
        val start = s.indexOf('[')
        val end = s.lastIndexOf(']')
        if (start >= 0 && end > start) {
            try {
                val arr = JSONArray(s.substring(start, end + 1))
                if (arr.length() == expected) {
                    return (0 until expected).map { i -> arr.optString(i, originals[i]) }
                }
            } catch (_: Exception) {
                // fall through to line based parsing
            }
        }
        val lines = s.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotEmpty() }
        if (lines.size == expected) return lines
        return originals
    }
}
