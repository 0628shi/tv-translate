package com.tvtranslate.app.pipeline

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a mono 16-bit PCM WAV file, patching the header on close. */
class WavWriter(private val file: File) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(ByteArray(HEADER_SIZE))
    }

    fun write(samples: ShortArray, count: Int = samples.size) {
        if (count <= 0) return
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = samples[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        raf.write(bytes)
        dataBytes += bytes.size
    }

    fun close() {
        try {
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36 + dataBytes).toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)                       // PCM
            header.putShort(1)                       // mono
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * 2)           // byte rate
            header.putShort(2)                       // block align
            header.putShort(16)                      // bits per sample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes.toInt())
            raf.seek(0)
            raf.write(header.array())
        } finally {
            raf.close()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val HEADER_SIZE = 44
    }
}
