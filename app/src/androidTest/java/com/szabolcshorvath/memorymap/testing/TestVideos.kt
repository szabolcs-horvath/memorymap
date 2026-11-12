package com.szabolcshorvath.memorymap.testing

import android.graphics.Color
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.szabolcshorvath.memorymap.testing.TestVideos.FRAMES
import com.szabolcshorvath.memorymap.testing.TestVideos.HEIGHT
import com.szabolcshorvath.memorymap.testing.TestVideos.SECONDS
import com.szabolcshorvath.memorymap.testing.TestVideos.WIDTH
import java.io.File

/**
 * Short MP4 videos of a single colour, made on the phone with its own H.264 encoder: [WIDTH] x [HEIGHT] pixels,
 * [SECONDS] seconds long. Long enough to see it play, small enough to make in a moment.
 */
internal object TestVideos {
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val WIDTH = 320
    private const val HEIGHT = 240
    private const val FRAMES_PER_SECOND = 10
    private const val SECONDS = 5
    private const val FRAMES = FRAMES_PER_SECOND * SECONDS
    private const val BIT_RATE = 250_000
    private const val TIMEOUT_US = 10_000L
    private const val MICROS_PER_SECOND = 1_000_000L

    // The size of a YUV 4:2:0 frame: full-size brightness, and the two colour planes at half width and height.
    private const val FRAME_BYTES = WIDTH * HEIGHT * 3 / 2

    /** The MP4 file's bytes. [cacheDir] holds the file while it is made. */
    fun mp4(color: Int, cacheDir: File): ByteArray {
        val file = File.createTempFile("ui_test_video", ".mp4", cacheDir)
        try {
            Encoder(color, file).run()
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    /** Feeds [FRAMES] frames of [color] to the encoder and writes what comes out into an MP4 [file]. */
    private class Encoder(color: Int, file: File) {
        private val yuv = Yuv.of(color)
        private val codec = MediaCodec.createEncoderByType(MIME)
        private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var framesQueued = 0
        private var inputDone = false
        private var outputDone = false

        fun run() {
            val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAMES_PER_SECOND)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            try {
                while (!outputDone) {
                    if (!inputDone) feedFrame()
                    drainOutput()
                }
                muxer.stop()
            } finally {
                codec.stop()
                codec.release()
                muxer.release()
            }
        }

        private fun feedFrame() {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return
            val time = framesQueued * MICROS_PER_SECOND / FRAMES_PER_SECOND
            if (framesQueued == FRAMES) {
                codec.queueInputBuffer(index, 0, 0, time, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                yuv.paint(checkNotNull(codec.getInputImage(index)) { "The encoder gave no input image" })
                codec.queueInputBuffer(index, 0, FRAME_BYTES, time, 0)
                framesQueued++
            }
        }

        private fun drainOutput() {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (index >= 0) {
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!isConfig && info.size > 0) {
                    muxer.writeSampleData(track, checkNotNull(codec.getOutputBuffer(index)), info)
                }
                codec.releaseOutputBuffer(index, false)
                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
    }

    /** A colour as brightness (Y) and colour difference (U, V) values, as video stores it (BT.601). */
    private class Yuv(val y: Int, val u: Int, val v: Int) {
        /** Fills a frame from the encoder with this colour. */
        fun paint(image: Image) {
            val values = intArrayOf(y, u, v)
            image.planes.forEachIndexed { index, plane ->
                // The colour planes have half the brightness plane's width and height.
                val scale = if (index == 0) 1 else 2
                val buffer = plane.buffer
                for (row in 0 until HEIGHT / scale) {
                    for (column in 0 until WIDTH / scale) {
                        buffer.put(row * plane.rowStride + column * plane.pixelStride, values[index].toByte())
                    }
                }
            }
        }

        companion object {
            private const val CENTER = 128

            fun of(color: Int): Yuv {
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                return Yuv(
                    y = (0.299 * r + 0.587 * g + 0.114 * b).toInt(),
                    u = (-0.169 * r - 0.331 * g + 0.5 * b + CENTER).toInt().coerceIn(0, 255),
                    v = (0.5 * r - 0.419 * g - 0.081 * b + CENTER).toInt().coerceIn(0, 255),
                )
            }
        }
    }
}
