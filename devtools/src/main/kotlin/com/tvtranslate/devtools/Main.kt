package com.tvtranslate.devtools

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tvtranslate.app.model.Cue
import com.tvtranslate.app.pipeline.CueNormalizer
import com.tvtranslate.app.pipeline.MonoResampler
import com.tvtranslate.app.pipeline.SrtCodec
import com.tvtranslate.app.pipeline.SubtitleParser
import com.tvtranslate.app.pipeline.TranslationJson
import com.tvtranslate.app.pipeline.TranslationPrompt
import com.tvtranslate.app.pipeline.WavWriter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Local dev harness. Runs on the JVM without a phone:
 *
 *   gradlew :devtools:run --args="selftest"
 *   gradlew :devtools:run --args="translate --in ep01.srt --out ep01.zh.srt \
 *       --base-url https://api.deepseek.com/v1 --api-key sk-... \
 *       --model deepseek-chat --target 简体中文"
 */
fun main(args: Array<String>) {
    when (val cmd = args.firstOrNull() ?: "selftest") {
        "selftest" -> runSelfTest()
        "translate" -> runTranslate(args.drop(1))
        else -> {
            println("未知命令：$cmd")
            println("用法：selftest | translate --in <srt> --out <srt> --base-url <url> --api-key <key> --model <model> --target <语言>")
            exitProcess(2)
        }
    }
}

// ---------------------------------------------------------------- self test

private var failures = 0
private var checks = 0

private fun check(name: String, block: () -> Unit) {
    checks++
    try {
        block()
        println("PASS  $name")
    } catch (t: Throwable) {
        failures++
        println("FAIL  $name -> ${t.message}")
    }
}

private fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

private fun runSelfTest() {
    println("== 剧译本地自检 ==\n")

    check("SRT 解析：时间、标签、多行") {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            <i>Hello</i> world

            2
            00:00:04,000 --> 00:00:06,000
            第二句
        """.trimIndent()
        val cues = SubtitleParser.parse(srt, "a.srt")
        expect(cues.size == 2, "条数=${cues.size}")
        expect(cues[0].startMs == 1000L && cues[0].endMs == 3500L, "时间=${cues[0].startMs}-${cues[0].endMs}")
        expect(cues[0].text == "Hello world", "文本=${cues[0].text}")
        expect(cues[1].text == "第二句", "文本=${cues[1].text}")
    }

    check("ASS 解析：Dialogue 行与 \\N") {
        val ass = """
            [Script Info]
            Title: test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\an8}Hello\Nworld
        """.trimIndent()
        val cues = SubtitleParser.parse(ass, "a.ass")
        expect(cues.size == 1, "条数=${cues.size}")
        expect(cues[0].startMs == 1000L && cues[0].endMs == 2500L, "时间=${cues[0].startMs}-${cues[0].endMs}")
        expect(cues[0].text == "Hello world", "文本=${cues[0].text}")
    }

    check("SRT 往返：解析 -> 生成 -> 再解析") {
        val cues = listOf(
            Cue(1000, 2000, "one", "一"),
            Cue(3000, 4500, "two", "二")
        )
        val text = SrtCodec.toSrt(cues)
        val again = SubtitleParser.parse(text, "x.srt")
        expect(again.size == 2, "条数=${again.size}")
        expect(again[0].startMs == 1000L && again[0].endMs == 2000L, "时间漂移")
        expect(again[1].translation.isBlank(), "SRT 不应包含译文列")
    }

    check("时间格式化") {
        expect(SrtCodec.formatTime(3_723_456) == "01:02:03,456", SrtCodec.formatTime(3_723_456))
    }

    check("归一化：修复重叠") {
        val cues = CueNormalizer.normalize(
            listOf(Cue(0, 3000, "a"), Cue(2000, 4000, "b"))
        )
        expect(cues.size == 2, "条数=${cues.size}")
        expect(cues[0].endMs <= 2000, "重叠未修复：${cues[0].endMs}")
    }

    check("归一化：去掉紧邻重复") {
        val cues = CueNormalizer.normalize(
            listOf(Cue(0, 1000, "same"), Cue(1200, 2000, "same"))
        )
        expect(cues.size == 1, "重复未去掉，条数=${cues.size}")
    }

    check("重采样：48k -> 16k 长度与波形") {
        val src = ShortArray(48000) { i ->
            (sin(2 * PI * 440 * i / 48000.0) * 10000).toInt().toShort()
        }
        val out = MonoResampler(48000, 16000).process(src)
        expect(out.size in 15995..16005, "输出长度=${out.size}")
        expect(out.any { it.toInt() != 0 }, "输出全零")
    }

    check("WAV 头：采样率与数据长度") {
        val f = File.createTempFile("tvtranslate", ".wav")
        try {
            val writer = WavWriter(f)
            writer.write(ShortArray(16000) { 100 })
            writer.close()
            val bytes = f.readBytes()
            expect(bytes.size == 44 + 32000, "文件大小=${bytes.size}")
            expect(String(bytes, 0, 4) == "RIFF", "RIFF")
            expect(String(bytes, 8, 4) == "WAVE", "WAVE")
            val rate = (bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or
                ((bytes[26].toInt() and 0xFF) shl 16) or ((bytes[27].toInt() and 0xFF) shl 24)
            expect(rate == 16000, "采样率=$rate")
            val data = (bytes[40].toInt() and 0xFF) or ((bytes[41].toInt() and 0xFF) shl 8) or
                ((bytes[42].toInt() and 0xFF) shl 16) or ((bytes[43].toInt() and 0xFF) shl 24)
            expect(data == 32000, "data=$data")
        } finally {
            f.delete()
        }
    }

    check("翻译 JSON：代码块与转义") {
        val originals = listOf("a\"b", "c\\d", "e\nf")
        val content = "```json\n[\"a\\\"b\",\"c\\\\d\",\"e\\nf\"]\n```"
        val parsed = TranslationJson.parseArray(content, 3, originals)
        expect(parsed[0] == "a\"b", parsed[0])
        expect(parsed[1] == "c\\d", parsed[1])
        expect(parsed[2] == "e\nf", parsed[2])
    }

    check("翻译 JSON：条数不符时回退原文") {
        val originals = listOf("x", "y")
        val parsed = TranslationJson.parseArray("[\"only one\"]", 2, originals)
        expect(parsed == originals, parsed.toString())
    }

    check("提示词：包含目标语言、上下文、术语表与转义") {
        val sys = TranslationPrompt.system("简体中文")
        expect(sys.contains("简体中文"), "system 缺目标语言")
        val user = TranslationPrompt.user(
            listOf("he said \"hi\""),
            listOf(Cue(0, 1000, "hello", "你好")),
            "金秀贤=김수현"
        )
        expect(user.contains("前文参考"), "缺上下文")
        expect(user.contains("金秀贤=김수현"), "缺术语表")
        expect(user.contains("\\\""), "引号未转义：$user")
    }

    println("\n$checks 项检查，$failures 项失败")
    if (failures > 0) exitProcess(1) else println("全部通过 ✅")
}

// ---------------------------------------------------------------- translate

private fun runTranslate(args: List<String>) {
    val opts = parseOptions(args)
    val input = opts["in"] ?: fail("缺少 --in")
    val output = opts["out"] ?: fail("缺少 --out")
    val baseUrl = opts["base-url"] ?: fail("缺少 --base-url")
    val apiKey = opts["api-key"] ?: fail("缺少 --api-key")
    val model = opts["model"] ?: fail("缺少 --model")
    val target = opts["target"] ?: "简体中文"
    val glossary = opts["glossary-file"]?.let { File(it).readText() } ?: opts["glossary"] ?: ""

    val cues = SubtitleParser.parse(File(input).readText(), input).toMutableList()
    if (cues.isEmpty()) fail("$input 里没有解析到字幕")
    println("读取 ${cues.size} 条字幕，目标语言：$target")

    val gson = Gson()
    var done = 0
    while (done < cues.size) {
        val end = minOf(done + 20, cues.size)
        val batch = cues.subList(done, end)
        val originals = batch.map { it.text }
        val context = cues.subList(maxOf(0, done - 4), done)
        val system = TranslationPrompt.system(target)
        val user = TranslationPrompt.user(originals, context, glossary)
        val content = chat(gson, baseUrl, apiKey, model, system, user)
        val translated = TranslationJson.parseArray(content, originals.size, originals)
        translated.forEachIndexed { i, t -> cues[done + i].translation = t }
        done = end
        println("  翻译 ${done}/${cues.size}")
    }

    File(output).writeText(SrtCodec.toSrt(cues), Charsets.UTF_8)
    println("已写入 $output")
}

private fun chat(
    gson: Gson,
    baseUrl: String,
    apiKey: String,
    model: String,
    system: String,
    user: String
): String {
    val root = JsonObject().apply {
        addProperty("model", model)
        addProperty("temperature", 0.2)
        add("messages", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", system)
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", user)
            })
        })
    }
    val payload = gson.toJson(root).toByteArray(Charsets.UTF_8)

    val conn = (URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 120_000
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }
    try {
        conn.outputStream.use { it.write(payload) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) fail("HTTP $code: ${text.take(300)}")
        val json = JsonParser.parseString(text).asJsonObject
        return json.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
    } finally {
        conn.disconnect()
    }
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--") && i + 1 < args.size) {
            out[a.removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return out
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(2)
}
