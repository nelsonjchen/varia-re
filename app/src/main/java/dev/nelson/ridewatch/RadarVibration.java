package dev.nelson.ridewatch;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import dev.nelson.ridewatch.core.TonePolicy.Tone;

/** Finite, event-based patterns; never a continuous vibration loop. */
final class RadarVibration {
  private final Vibrator vibrator;
  private final VibrationAttributes attributes =
      new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_NOTIFICATION).build();

  RadarVibration(Context context) {
    vibrator = context.getSystemService(VibratorManager.class).getDefaultVibrator();
  }

  void play(Tone tone) {
    cancel();
    if (tone == null || !vibrator.hasVibrator()) return;
    long[] timings =
        switch (tone) {
          case APPROACH -> new long[] {0, 180, 120, 180};
          case URGENT -> new long[] {0, 300, 100, 300, 100, 300};
          case LOST -> new long[] {0, 650};
          default -> null;
        };
    if (timings != null) vibrator.vibrate(VibrationEffect.createWaveform(timings, -1), attributes);
  }

  void cancel() {
    vibrator.cancel();
  }
}
