package com.tvtranslate.app

import android.content.Context

/**
 * Persisted user configuration. Everything is optional except the API keys,
 * which the user brings themselves (BYOK).
 */
data class Settings(
    val asrBaseUrl: String = "https://api.openai.com/v1",
    val asrApiKey: String = "",
    val asrModel: String = "whisper-1",
    val mtBaseUrl: String = "https://api.deepseek.com/v1",
    val mtApiKey: String = "",
    val mtModel: String = "deepseek-chat",
    val targetLang: String = "简体中文",
    val sourceHint: String = "auto",
    val glossary: String = ""
) {
    val asrReady: Boolean get() = asrApiKey.isNotBlank() && asrBaseUrl.isNotBlank()
    val mtReady: Boolean get() = mtApiKey.isNotBlank() && mtBaseUrl.isNotBlank()

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("asr_base", asrBaseUrl)
            .putString("asr_key", asrApiKey)
            .putString("asr_model", asrModel)
            .putString("mt_base", mtBaseUrl)
            .putString("mt_key", mtApiKey)
            .putString("mt_model", mtModel)
            .putString("target", targetLang)
            .putString("source_hint", sourceHint)
            .putString("glossary", glossary)
            .apply()
    }

    companion object {
        private const val PREFS = "tv_translate_settings"

        fun load(ctx: Context): Settings {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Settings(
                asrBaseUrl = sp.getString("asr_base", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
                asrApiKey = sp.getString("asr_key", "") ?: "",
                asrModel = sp.getString("asr_model", "whisper-1") ?: "whisper-1",
                mtBaseUrl = sp.getString("mt_base", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1",
                mtApiKey = sp.getString("mt_key", "") ?: "",
                mtModel = sp.getString("mt_model", "deepseek-chat") ?: "deepseek-chat",
                targetLang = sp.getString("target", "简体中文") ?: "简体中文",
                sourceHint = sp.getString("source_hint", "auto") ?: "auto",
                glossary = sp.getString("glossary", "") ?: ""
            )
        }

        val SOURCE_LABELS = linkedMapOf(
            "auto" to "自动",
            "ja" to "日语",
            "ko" to "韩语",
            "en" to "英语",
            "zh" to "中文"
        )

        val TARGET_LABELS = listOf("简体中文", "English")
    }
}
