package com.tvtranslate.app.pipeline

import com.tvtranslate.app.model.Cue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local persistence + SRT export. */
object CueStore {

    fun save(context: android.content.Context, key: String, cues: List<Cue>) {
        val dir = File(context.filesDir, "cues")
        dir.mkdirs()
        val arr = JSONArray()
        cues.forEach { c ->
            arr.put(
                JSONObject()
                    .put("s", c.startMs)
                    .put("e", c.endMs)
                    .put("t", c.text)
                    .put("r", c.translation)
            )
        }
        File(dir, "$key.json").writeText(arr.toString(), Charsets.UTF_8)
    }

    fun load(context: android.content.Context, key: String): List<Cue>? {
        val f = File(File(context.filesDir, "cues"), "$key.json")
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Cue(o.optLong("s"), o.optLong("e"), o.optString("t"), o.optString("r"))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun toSrt(cues: List<Cue>): String = SrtCodec.toSrt(cues)
}
