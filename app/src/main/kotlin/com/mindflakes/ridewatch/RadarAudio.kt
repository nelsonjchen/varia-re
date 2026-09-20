package com.mindflakes.ridewatch

import android.content.Context
import android.content.SharedPreferences
import android.media.*
import android.os.*
import android.speech.tts.*
import android.util.Log
import com.mindflakes.ridewatch.core.TonePolicy.Tone
import java.util.Locale

/** One bounded audio event: chime then speech. Disconnect announcements survive reconnect ticks. */
class RadarAudio(context: Context, private val automaticSpeech: Boolean = true) : AutoCloseable {
    private val prefs = context.getSharedPreferences("ride", 0)

    // Priority speech is the direct disconnect/battery announcement.
    private fun speechEnabled(disconnectAnnouncement: Boolean = critical) =
        !automaticSpeech ||
            (prefs.getBoolean("speech", true) &&
                (disconnectAnnouncement || prefs.getBoolean("speed", false)))

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if ((key == "speech" || key == "speed") && !speechEnabled() && speechPending) stop()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ if (it < 0) stop() }, handler)
            .build()
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attributes).build()
    private val sounds = mutableMapOf<Tone, Int>()
    private val loaded = mutableSetOf<Int>()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var stream = 0
    private var generation = 0L
    private var critical = false
    private var speechPending = false

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        pool.setOnLoadCompleteListener { _, id, result -> if (result == 0) loaded.add(id) }
        sounds[Tone.APPROACH] = pool.load(context, R.raw.alert_approach, 1)
        sounds[Tone.URGENT] = pool.load(context, R.raw.alert_urgent, 1)
        sounds[Tone.CONNECTED] = pool.load(context, R.raw.alert_connected, 1)
        sounds[Tone.LOST] = pool.load(context, R.raw.alert_lost, 1)
        sounds[Tone.EMPTY] = pool.load(context, R.raw.alert_empty, 1)
        tts =
            TextToSpeech(context) { result ->
                callbacks.post {
                    if (!closed) {
                        ready =
                            result == TextToSpeech.SUCCESS &&
                                (tts?.setLanguage(Locale.US) ?: -1) >= 0
                        if (ready) {
                            tts?.setSpeechRate(1.4f)
                            tts?.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                        }
                    }
                }
            }
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String) {
                    Log.d("RideWatchAudio", "speech started event=$id")
                }

                override fun onDone(id: String) {
                    Log.d("RideWatchAudio", "speech completed event=$id")
                    finish(id)
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(id: String) {
                    Log.w("RideWatchAudio", "speech failed event=$id")
                    finish(id)
                }

                private fun finish(id: String) {
                    callbacks.post { if (id == generation.toString()) stop() }
                }
            }
        )
    }

    @JvmOverloads
    fun play(tone: Tone?, speech: String?, priority: Boolean = false) {
        val allowedSpeech = speech.takeIf { speechEnabled(priority) }
        if (closed || (critical && !priority)) return
        // Faster numeric refresh must not chop off a number already queued behind a chime.
        // Drop this refresh; the next tick supplies the current speed, with no speech backlog.
        if (!priority && tone == null && allowedSpeech?.toIntOrNull() != null && speechPending)
            return
        stop()
        if (tone == null && allowedSpeech == null) return
        critical = priority
        speechPending = allowedSpeech != null
        val token = generation
        val deadline = SystemClock.elapsedRealtime() + 2000
        begin(token, tone, allowedSpeech, deadline)
    }

    private fun begin(token: Long, tone: Tone?, speech: String?, deadline: Long) {
        if (closed || token != generation) return
        val id = sounds[tone]
        // SoundPool loads asynchronously: don't discard a one-shot disconnect during startup.
        if (id != null && id !in loaded && SystemClock.elapsedRealtime() < deadline) {
            handler.postDelayed({ begin(token, tone, speech, deadline) }, 50)
            return
        }
        val focusResult = manager.requestAudioFocus(focus)
        Log.d("RideWatchAudio", "focus=$focusResult, tone=$tone, priority=$critical")
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (critical && SystemClock.elapsedRealtime() < deadline)
                handler.postDelayed({ begin(token, tone, speech, deadline) }, 200)
            else {
                Log.w("RideWatchAudio", "Audio focus unavailable; event could not play")
                stop()
            }
            return
        }
        var delay = 0L
        if (id != null && id in loaded) {
            stream = pool.play(id, 1f, 1f, 1, 0, 1f)
            Log.d("RideWatchAudio", "stream=$stream")
            if (stream != 0)
                delay =
                    when (tone) {
                        Tone.URGENT -> 680
                        Tone.EMPTY -> 420
                        else -> 500
                    }
        }
        handler.postDelayed({ speak(token, speech, SystemClock.elapsedRealtime() + 3000) }, delay)
    }

    private fun speak(token: Long, speech: String?, deadline: Long) {
        if (closed || token != generation) return
        if (speech == null || !speechEnabled()) {
            stop()
            return
        }
        if (!ready) {
            if (SystemClock.elapsedRealtime() < deadline)
                handler.postDelayed({ speak(token, speech, deadline) }, 100)
            else {
                Log.w("RideWatchAudio", "Speech engine unavailable")
                stop()
            }
            return
        }
        if (
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, token.toString()) ==
                TextToSpeech.ERROR
        )
            stop()
        else handler.postDelayed({ if (token == generation) stop() }, 10000)
    }

    fun stop() {
        generation++
        critical = false
        speechPending = false
        handler.removeCallbacksAndMessages(null)
        if (stream != 0) pool.stop(stream)
        stream = 0
        tts?.stop()
        manager.abandonAudioFocusRequest(focus)
    }

    override fun close() {
        if (closed) return
        closed = true
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        stop()
        pool.release()
        tts?.shutdown()
        callbacks.removeCallbacksAndMessages(null)
    }
}
