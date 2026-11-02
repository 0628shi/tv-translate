package com.tvtranslate.app.pipeline

/**
 * Streaming mono down-mix + linear resampler to 16 kHz.
 * Keeps one sample of state between buffers so the phase stays continuous.
 */
class MonoResampler(private val srcRate: Int, private val dstRate: Int = 16000) {

    private var tail = ShortArray(0)

    fun process(input: ShortArray): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        if (srcRate == dstRate) return input
        if (srcRate <= 0) return ShortArray(0)

        val ext = ShortArray(tail.size + input.size)
        tail.copyInto(ext, 0)
        input.copyInto(ext, tail.size)

        if (ext.size < 2) {
            tail = ext
            return ShortArray(0)
        }

        val step = srcRate.toDouble() / dstRate
        val maxOut = ((ext.size - 1) / step).toInt()
        if (maxOut <= 0) {
            tail = ext
            return ShortArray(0)
        }

        val out = ShortArray(maxOut)
        var pos = 0.0
        for (i in 0 until maxOut) {
            val idx = pos.toInt()
            val frac = pos - idx
            val a = ext[idx].toInt()
            val b = ext[idx + 1].toInt()
            out[i] = (a + (b - a) * frac).toInt().toShort()
            pos += step
        }
        val consumed = pos.toInt().coerceAtMost(ext.size)
        tail = ext.copyOfRange(consumed, ext.size)
        return out
    }
}
