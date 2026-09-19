package dev.nelson.ridewatch;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import dev.nelson.ridewatch.core.TonePolicy.Tone;
import java.util.*;

/** One bounded audio event: optional chime followed by speech, with transient ducking. */
final class RadarAudio implements AutoCloseable {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Handler callbacks = new Handler(Looper.getMainLooper());
  private final AudioManager manager;
  private final AudioFocusRequest focus;
  private final SoundPool pool;
  private final Map<Tone, Integer> sounds = new EnumMap<>(Tone.class);
  private final Set<Integer> loaded = new HashSet<>();
  private TextToSpeech tts;
  private boolean ready, closed;
  private int stream;
  private long generation;

  RadarAudio(Context context) {
    var attributes =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    manager = context.getSystemService(AudioManager.class);
    focus =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(
                change -> {
                  if (change < 0) stop();
                },
                handler)
            .build();
    pool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attributes).build();
    pool.setOnLoadCompleteListener(
        (soundPool, id, result) -> {
          if (result == 0) loaded.add(id);
        });
    sounds.put(Tone.APPROACH, pool.load(context, R.raw.alert_approach, 1));
    sounds.put(Tone.URGENT, pool.load(context, R.raw.alert_urgent, 1));
    sounds.put(Tone.CONNECTED, pool.load(context, R.raw.alert_connected, 1));
    sounds.put(Tone.LOST, pool.load(context, R.raw.alert_lost, 1));
    sounds.put(Tone.EMPTY, pool.load(context, R.raw.alert_empty, 1));
    tts =
        new TextToSpeech(
            context,
            result ->
                callbacks.post(
                    () -> {
                      if (closed) return;
                      ready = result == TextToSpeech.SUCCESS;
                      if (ready) {
                        ready = tts.setLanguage(Locale.US) >= 0;
                        tts.setAudioAttributes(
                            new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build());
                      }
                    }));
    tts.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          public void onStart(String id) {}

          public void onDone(String id) {
            finish(id);
          }

          public void onError(String id) {
            finish(id);
          }

          private void finish(String id) {
            callbacks.post(
                () -> {
                  if (id.equals(Long.toString(generation))) stop();
                });
          }
        });
  }

  void play(Tone tone, String speech) {
    if (closed) return;
    stop();
    if (tone == null && (speech == null || !ready)) return;
    int focusResult = manager.requestAudioFocus(focus);
    android.util.Log.d("RideWatchAudio", "focus=" + focusResult + ", tone=" + tone);
    if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
    long token = generation;
    int delay = 0;
    if (tone != null) {
      int id = sounds.get(tone);
      if (loaded.contains(id)) {
        stream = pool.play(id, .85f, .85f, 1, 0, 1);
        android.util.Log.d("RideWatchAudio", "stream=" + stream);
        if (stream != 0) delay = tone == Tone.URGENT ? 680 : tone == Tone.EMPTY ? 420 : 500;
      }
    }
    handler.postDelayed(
        () -> {
          if (closed || token != generation) return;
          if (speech != null && ready) {
            if (tts.speak(speech, TextToSpeech.QUEUE_FLUSH, null, Long.toString(token))
                == TextToSpeech.ERROR) stop();
            else
              handler.postDelayed(
                  () -> {
                    if (token == generation) stop();
                  },
                  10000);
          } else stop();
        },
        delay);
  }

  void stop() {
    generation++;
    handler.removeCallbacksAndMessages(null);
    if (stream != 0) pool.stop(stream);
    stream = 0;
    if (tts != null) tts.stop();
    manager.abandonAudioFocusRequest(focus);
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    stop();
    pool.release();
    tts.shutdown();
  }
}
