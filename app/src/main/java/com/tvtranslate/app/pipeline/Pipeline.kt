package com.tvtranslate.app.pipeline

import android.content.Context
import android.net.Uri
import com.tvtranslate.app.Settings
import com.tvtranslate.app.model.Cue
import java.io.File

/**
 * Orchestrates one run: subtitle file (if any) -> ASR -> translation.
 * All callbacks are invoked from the calling (background) thread.
 */
class Pipeline(private val context: Context) {

    fun run(
        videoUri: Uri,
        videoName: String,
        subtitleUri: Uri?,
        subtitleName: String,
        settings: Settings,
        onStage: (String, Float) -> Unit,
        onLog: (String) -> Unit,
        shouldStop: () -> Boolean = { false }
    ): List<Cue> {
        onLog("视频：$videoName")

        val cues: MutableList<Cue>
        if (subtitleUri != null) {
            onStage("读取字幕文件", 0.05f)
            val content = readText(subtitleUri)
            val parsed = SubtitleParser.parse(content, subtitleName)
            if (parsed.isEmpty()) {
                throw IllegalStateException("字幕文件里没有解析到台词：$subtitleName")
            }
            cues = parsed.toMutableList()
            onLog("已读取 ${cues.size} 条字幕，跳过语音识别")
        } else {
            if (!settings.asrReady) {
                throw IllegalStateException("请先在设置里填写语音识别 API Key（或选择字幕文件）")
            }
            onStage("提取音频", 0.02f)
            val outDir = File(context.cacheDir, "audio_chunks")
            val chunks = AudioExtractor.extract(
                context = context,
                uri = videoUri,
                outDir = outDir,
                onProgress = { p -> onStage("提取音频", 0.02f + p * 0.28f) },
                shouldStop = shouldStop
            )
            onLog("音频切成 ${chunks.size} 段，开始识别")

            val asr = AsrClient(settings.asrBaseUrl, settings.asrApiKey)
            val language = if (settings.sourceHint == "auto") null else settings.sourceHint
            val collected = mutableListOf<Cue>()
            chunks.forEachIndexed { i, chunk ->
                if (shouldStop()) throw InterruptedException("已取消")
                onStage("识别语音 ${i + 1}/${chunks.size}", 0.30f + 0.42f * i / chunks.size)
                val part = asr.transcribe(
                    file = chunk.file,
                    model = settings.asrModel,
                    language = language,
                    offsetMs = chunk.startMs,
                    fallbackDurationMs = chunk.durationMs
                )
                collected.addAll(part)
                if (asr.lastFallback) {
                    onLog("警告：第 ${i + 1} 段没有返回时间戳，时间轴是估算的，字幕可能不同步")
                }
                onLog("第 ${i + 1} 段识别出 ${part.size} 条")
                chunk.file.delete()
            }
            cues = CueNormalizer.normalize(collected)
            if (cues.isEmpty()) throw IllegalStateException("没有识别到对白，请换一个视频或用字幕文件")
            onLog("共 ${cues.size} 条台词")
        }

        if (!settings.mtReady) {
            throw IllegalStateException("请先在设置里填写翻译 API Key")
        }

        onStage("翻译字幕", 0.74f)
        val translator = Translator(settings.mtBaseUrl, settings.mtApiKey, settings.mtModel)
        translator.translate(
            cues = cues,
            targetLang = settings.targetLang,
            glossary = settings.glossary,
            log = onLog,
            onProgress = { done, total ->
                onStage("翻译字幕 $done/$total", 0.74f + 0.25f * done / total.coerceAtLeast(1))
            },
            isActive = { !shouldStop() }
        )
        if (cues.none { it.translation.isNotBlank() } && translator.lastError != null) {
            throw IllegalStateException("翻译失败：${translator.lastError}")
        }

        onStage("完成", 1f)
        onLog("完成，共 ${cues.size} 条字幕")
        return cues
    }

    private fun readText(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取字幕文件")
        val utf8 = String(bytes, Charsets.UTF_8)
        val text = if (utf8.contains('\uFFFD')) String(bytes, charset("GBK")) else utf8
        return text.trimStart('\uFEFF')
    }
}
