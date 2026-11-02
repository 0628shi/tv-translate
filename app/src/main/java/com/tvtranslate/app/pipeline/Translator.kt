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

        val system = TranslationPrompt.system(targetLang)

        var index = 0
        while (index < cues.size) {
            if (!isActive()) throw InterruptedException("已取消")
            val end = minOf(index + batchSize, cues.size)
            val originals = cues.subList(index, end).map { it.text }
            val context = cues.subList(maxOf(0, index - 4), index)
            val user = TranslationPrompt.user(originals, context, glossary)

            try {
                val result = requestTranslation(system, user)
                val parsed = TranslationJson.parseArray(result, originals.size, originals)
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
}
