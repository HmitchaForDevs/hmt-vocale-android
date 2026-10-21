package com.hmtautosolutions.hmttvocale.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.vosk.Model
import org.vosk.android.StorageService

/**
 * Offline speech models bundled in the APK (Vosk small models, Apache 2.0).
 * French and English are bundled. There is no small Arabic model, so Arabic commands
 * still need the tablet's own recognizer; the French model is used to hear "Geely" in Arabic mode.
 */
object VoskModels {
    const val SAMPLE_RATE = 16000f

    fun assetFor(lang: String): String = if (lang == "en") "model-en" else "model-fr"

    /** Languages whose commands can be understood fully offline. */
    fun commandsOffline(lang: String) = lang == "fr" || lang == "en"

    fun isBundled(ctx: Context, asset: String): Boolean = try {
        ctx.assets.list(asset)?.isNotEmpty() == true
    } catch (_: Exception) { false }

    private val loaded = HashMap<String, Model>()
    private val waiting = HashMap<String, MutableList<(Model?, String?) -> Unit>>()

    /** Loads (and on first run unpacks) a model. Callback runs on the main thread. */
    fun load(ctx: Context, asset: String, cb: (Model?, String?) -> Unit) {
        loaded[asset]?.let { cb(it, null); return }
        if (!isBundled(ctx, asset)) { cb(null, "Offline model $asset not bundled"); return }
        val list = waiting[asset]
        if (list != null) { list.add(cb); return }
        waiting[asset] = mutableListOf(cb)
        StorageService.unpack(ctx.applicationContext, asset, asset,
            { m ->
                loaded[asset] = m
                waiting.remove(asset)?.forEach { it(m, null) }
            },
            { e ->
                waiting.remove(asset)?.forEach { it(null, e.message ?: "model error") }
            })
    }
}

/** Reads the microphone at 16 kHz on a background thread and hands chunks to [onChunk]. */
class VoskMic(private val onChunk: (ShortArray, Int) -> Unit, private val onFail: (String) -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val rate = VoskModels.SAMPLE_RATE.toInt()
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
            } catch (e: Exception) { null }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                rec?.release(); running = false
                main.post { onFail("Microphone unavailable") }
                return@Thread
            }
            val buf = ShortArray(rate / 10)
            try {
                rec.startRecording()
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) onChunk(buf, n)
                    else if (n < 0) { main.post { onFail("Microphone read error $n") }; break }
                }
            } catch (e: Exception) {
                main.post { onFail("Microphone error: ${e.message}") }
            } finally {
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
                running = false
            }
        }, "hmt-mic").also { it.start() }
    }

    fun stop() {
        running = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) try { t.join(500) } catch (_: Exception) {}
    }
}
