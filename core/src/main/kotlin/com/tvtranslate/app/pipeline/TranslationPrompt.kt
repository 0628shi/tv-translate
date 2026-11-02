package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue

/** Builds the prompts sent to the translation model. Pure, so it can be unit tested. */
object TranslationPrompt {

    fun system(targetLang: String): String = buildString {
        append("你是专业的影视字幕翻译。把用户给出的 JSON 字符串数组逐条翻译成")
        append(targetLang)
        append("，输出等长的 JSON 字符串数组，只输出数组本身，不要解释、不要代码块。\n")
        append("要求：口语化、简短；中文每条不超过 20 字，英文不超过 42 个字符；")
        append("保留人名和称谓习惯；不要合并、拆分或省略条目；无法翻译时保留原文。")
    }

    fun user(originals: List<String>, context: List<Cue>, glossary: String): String = buildString {
        if (context.isNotEmpty()) {
            append("前文参考：\n")
            for (cue in context) {
                append("原文：").append(cue.text).append('\n')
                append("译文：").append(cue.translation).append('\n')
            }
            append('\n')
        }
        if (glossary.isNotBlank()) {
            append("术语表：\n").append(glossary.trim()).append("\n\n")
        }
        append("待翻译（JSON 数组，共 ").append(originals.size).append(" 条）：\n")
        append('[')
        originals.forEachIndexed { i, text ->
            if (i > 0) append(',')
            append('"').append(escapeJson(text)).append('"')
        }
        append(']')
    }

    fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }
}
