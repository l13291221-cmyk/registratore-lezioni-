package com.registratorelezioni

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Codifica PCM 16 bit mono in AAC e lo salva in un file .m4a.
 * Il microfono NON è gestito qui: questa classe riceve solo i campioni, così
 * possiamo chiudere un file e aprirne un altro senza mai spegnere il microfono.
 */
class EncoderAac(
    private val file: File,
    private val sampleRate: Int,
    bitRate: Int
) {

    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var traccia = -1
    private var muxerAvviato = false
    private var campioniScritti = 0L
    private var chiuso = false

    init {
        val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        f.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        try {
            codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            try { codec.release() } catch (_: Exception) { }
            try { muxer.release() } catch (_: Exception) { }
            file.delete()
            throw e
        }
    }

    /** Aggiunge [len] byte di PCM 16 bit mono. */
    fun scrivi(pcm: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val idx = codec.dequeueInputBuffer(10_000)
            if (idx < 0) { drena(false); continue }
            val ib = codec.getInputBuffer(idx) ?: continue
            ib.clear()
            val n = minOf(ib.remaining(), len - off) and 1.inv() // campioni interi (2 byte)
            ib.put(pcm, off, n)
            codec.queueInputBuffer(idx, 0, n, ptsUs(), 0)
            campioniScritti += n / 2
            off += n
            drena(false)
        }
    }

    /** Chiude il file. Se non contiene audio lo elimina. */
    fun chiudi() {
        if (chiuso) return
        chiuso = true
        try {
            var idx = -1
            for (i in 0 until 100) {
                idx = codec.dequeueInputBuffer(10_000)
                if (idx >= 0) break
                drena(false)
            }
            if (idx >= 0) {
                codec.queueInputBuffer(idx, 0, 0, ptsUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drena(true)
            }
        } catch (_: Exception) { }
        try { codec.stop() } catch (_: Exception) { }
        try { codec.release() } catch (_: Exception) { }
        var ok = false
        try {
            if (muxerAvviato) { muxer.stop(); ok = true }
        } catch (_: Exception) { }
        try { muxer.release() } catch (_: Exception) { }
        if (!ok || campioniScritti == 0L) file.delete()
    }

    private fun ptsUs(): Long = campioniScritti * 1_000_000L / sampleRate

    private fun drena(fineStream: Boolean) {
        var attese = 0
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (fineStream) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!fineStream || ++attese > 300) return
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerAvviato) {
                        traccia = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerAvviato = true
                    }
                }
                idx >= 0 -> {
                    val ob = codec.getOutputBuffer(idx)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (ob != null && !config && info.size > 0 && muxerAvviato) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        muxer.writeSampleData(traccia, ob, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}
