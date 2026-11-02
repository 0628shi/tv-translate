package com.tvtranslate.app.pipeline

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes the audio track of a video into 16 kHz mono WAV chunks that can be
 * uploaded to a speech-to-text endpoint. Uses only framework codecs so the APK
 * stays small; unsupported codecs (AC3/DTS/...) surface a readable error.
 */
object AudioExtractor {

    data class Chunk(val file: File, val startMs: Long, val durationMs: Long)

    private const val TARGET_RATE = 16000
    private const val CHUNK_MS = 10 * 60 * 1000L

    fun extract(
        context: Context,
        uri: Uri,
        outDir: File,
        onProgress: (Float) -> Unit = {},
        shouldStop: () -> Boolean = { false }
    ): List<Chunk> {
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("无法打开视频文件")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(pfd.fileDescriptor)

            var format: MediaFormat? = null
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) {
                    format = f
                    mime = m
                    extractor.selectTrack(i)
                    break
                }
            }
            if (format == null || mime == null) {
                throw IllegalStateException("这个文件没有音频轨")
            }
            val audioFormat: MediaFormat = format
            val audioMime: String = mime

            val durationMs = resolveDuration(context, uri, audioFormat)

            val dec: MediaCodec = try {
                MediaCodec.createDecoderByType(audioMime)
            } catch (e: Exception) {
                throw IllegalStateException("不支持该音频编码（$audioMime）。请把视频转成 AAC/MP3 音轨后重试。")
            }
            codec = dec
            dec.configure(audioFormat, null, null, 0)
            dec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = 0
            var channels = 0
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var resampler: MonoResampler? = null

            var writer: WavWriter? = null
            var currentFile: File? = null
            var chunkStartMs = 0L
            var chunkSamples = 0L
            var totalSamples = 0L
            var chunkIndex = 0
            val chunkTargetSamples = CHUNK_MS * TARGET_RATE / 1000
            val chunks = mutableListOf<Chunk>()

            fun openChunk() {
                if (writer == null) {
                    chunkStartMs = totalSamples * 1000 / TARGET_RATE
                    chunkSamples = 0
                    currentFile = File(outDir, "audio_%03d.wav".format(chunkIndex++))
                    writer = WavWriter(currentFile!!)
                }
            }

            while (!outputDone) {
                if (shouldStop()) throw InterruptedException("已取消")

                if (!inputDone) {
                    val inIndex = dec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = dec.getInputBuffer(inIndex)
                        val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (size < 0) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = dec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = dec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        resampler = MonoResampler(sampleRate, TARGET_RATE)
                    }

                    outIndex >= 0 -> {
                        if (info.size > 0 && channels > 0) {
                            val buf = dec.getOutputBuffer(outIndex)
                            if (buf != null) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                val mono = toMono(buf, channels, pcmEncoding)
                                val out = resampler?.process(mono) ?: mono
                                if (out.isNotEmpty()) {
                                    openChunk()
                                    writer?.write(out)
                                    totalSamples += out.size
                                    chunkSamples += out.size
                                    if (chunkSamples >= chunkTargetSamples) {
                                        writer?.close()
                                        chunks.add(
                                            Chunk(
                                                currentFile!!,
                                                chunkStartMs,
                                                chunkSamples * 1000 / TARGET_RATE
                                            )
                                        )
                                        writer = null
                                        currentFile = null
                                    }
                                }
                            }
                        }
                        dec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }

                if (durationMs > 0) {
                    onProgress((totalSamples * 1000f / durationMs).coerceIn(0f, 0.99f))
                }
            }

            writer?.close()
            if (currentFile != null && chunkSamples > 0) {
                chunks.add(Chunk(currentFile!!, chunkStartMs, chunkSamples * 1000 / TARGET_RATE))
            }
            writer = null
            if (chunks.isEmpty()) throw IllegalStateException("没能从视频里解出音频")
            return chunks
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveDuration(context: Context, uri: Uri, format: MediaFormat): Long {
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            val us = try {
                format.getLong(MediaFormat.KEY_DURATION)
            } catch (_: Exception) {
                0L
            }
            if (us > 0) return us / 1000
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun toMono(buf: ByteBuffer, channels: Int, encoding: Int): ShortArray {
        if (channels <= 0) return ShortArray(0)
        buf.order(ByteOrder.nativeOrder())
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val fb = buf.asFloatBuffer()
            val frames = fb.remaining() / channels
            if (frames <= 0) return ShortArray(0)
            val mono = ShortArray(frames)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += fb.get(f * channels + c)
                val v = (sum / channels * 32767f).toInt().coerceIn(-32768, 32767)
                mono[f] = v.toShort()
            }
            return mono
        }
        val sb = buf.asShortBuffer()
        val frames = sb.remaining() / channels
        if (frames <= 0) return ShortArray(0)
        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += sb.get(f * channels + c).toInt()
            mono[f] = (sum / channels).toShort()
        }
        return mono
    }
}
