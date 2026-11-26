package com.metrolist.music.wavy

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Switches for the 3D / 8D engine stage. Set by WavyFx from the settings and the connected output (earphones only). */
object WavySpatial {
    @Volatile var mode = 0          // 0 off, 1 = 3D, 2 = 8D
    @Volatile var rotationHz = 0.12f // 8D: turns per second around the head
    @Volatile var night = false      // Night mode: gentle compressor that evens out loud and quiet parts
}

/**
 * 3D and 8D for earphones, done on the PCM stream (16-bit stereo) with float math.
 * 8D: the sound circles the head. Pan, a tiny inter-ear delay on the far ear, a darker far ear (head shadow) and a small front/back level cue.
 * 3D: wider stereo image with a little crosstalk and delay between the ears.
 * Output keeps headroom (-1.5 dB) and a soft limiter above 0.9, so it does not clip. When the mode is off the audio is copied untouched.
 */
class WavySpatialProcessor : BaseAudioProcessor() {
    private var rate = 44100
    private var phase = 0.0
    private val dl = FloatArray(128); private val dr = FloatArray(128); private var wp = 0
    private var lpL = 0f; private var lpR = 0f
    private var lastMode = 0
    private var env = 0f; private var gainS = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        rate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) { dl.fill(0f); dr.fill(0f); lpL = 0f; lpR = 0f }

    private fun limit(x: Float): Float {
        val a = abs(x)
        return if (a <= 0.9f) x else (if (x < 0f) -1f else 1f) * (0.9f + 0.1f * tanh((a - 0.9f) / 0.1f))
    }

    private fun tap(buf: FloatArray, delay: Float): Float {
        val d = delay.coerceIn(0f, 120f); val i = d.toInt(); val f = d - i
        val a = buf[(wp - i) and 127]; val b = buf[(wp - i - 1) and 127]
        return a + (b - a) * f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        val mode = WavySpatial.mode
        if (mode != lastMode) { dl.fill(0f); dr.fill(0f); lpL = 0f; lpR = 0f; lastMode = mode }
        val night = WavySpatial.night
        if (mode == 0 && !night) { out.put(inputBuffer); out.flip(); gainS = 1f; env = 0f; return }
        val inc = 2.0 * PI * WavySpatial.rotationHz.coerceIn(0.03f, 0.6f) / rate
        val itdMax = 0.0006f * rate           // about 0.6 ms
        val cross = 0.00025f * rate
        val k = 1f / 32768f
        var i = 0
        val frames = n / 4
        while (i < frames) {
            val l = inputBuffer.short * k; val r = inputBuffer.short * k
            dl[wp and 127] = l; dr[wp and 127] = r
            var ol: Float; var or: Float
            if (mode == 0) { ol = l; or = r }
            else if (mode == 2) {
                val th = phase; phase += inc; if (phase > 2 * PI) phase -= 2 * PI
                val p = sin(th).toFloat()                       // -1 left .. +1 right
                val m = (l + r) * 0.5f; val s = (l - r) * 0.5f
                val ang = (p + 1f) * (PI.toFloat() / 4f)
                val gl = cos(ang) * 1.4142f; val gr = sin(ang) * 1.4142f
                val front = 0.92f + 0.08f * cos(th).toFloat()      // slightly quieter behind
                // far ear is delayed and darker
                val dLeft = itdMax * maxOf(p, 0f); val dRight = itdMax * maxOf(-p, 0f)
                val ml = tapMono(dl, dr, dLeft); val mr = tapMono(dl, dr, dRight)
                var xl = (ml * gl) * front + s * 0.5f
                var xr = (mr * gr) * front - s * 0.5f
                val shade = abs(p)
                val a = 0.2f + 0.6f * (1f - shade)               // lowpass coefficient: far ear gets darker
                if (p > 0f) { lpL += a * (xl - lpL); xl = xl * (1f - shade) + lpL * shade }
                else { lpR += a * (xr - lpR); xr = xr * (1f - shade) + lpR * shade }
                if (p > 0f) lpR = xr else lpL = xl
                ol = xl; or = xr
            } else {
                val m = (l + r) * 0.5f; val s = (l - r) * 0.5f
                val cl = tap(dl, cross); val cr = tap(dr, cross)
                ol = m * 0.9f + s * 1.6f - 0.22f * cr
                or = m * 0.9f - s * 1.6f - 0.22f * cl
            }
            wp = (wp + 1) and 127
            if (night) {
                val pk = maxOf(abs(ol), abs(or))
                env = if (pk > env) env + (pk - env) * 0.02f else env + (pk - env) * 0.0004f   // fast attack, slow release
                val tgt = if (env > 0.3f) (0.3f + (env - 0.3f) / 3f) / env else 1f           // 3:1 above 0.3
                gainS += (tgt - gainS) * 0.01f
                ol *= gainS * 1.15f; or *= gainS * 1.15f
            }
            out.putShort((limit(ol * (if (mode == 0) 1f else 0.84f)) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            out.putShort((limit(or * (if (mode == 0) 1f else 0.84f)) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            i++
        }
        out.flip()
    }

    private fun tapMono(a: FloatArray, b: FloatArray, delay: Float) = (tap(a, delay) + tap(b, delay)) * 0.5f
}
