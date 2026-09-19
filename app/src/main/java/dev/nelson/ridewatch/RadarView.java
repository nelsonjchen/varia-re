package dev.nelson.ridewatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.Arrays;

/** Distance plot only: horizontal position does not imply a measured traffic lane. */
final class RadarView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Drawable bike, car;
  private String health = "DISCONNECTED";
  private int[] meters = {}, threats = {};
  private int accent = Color.GRAY;
  private boolean group, demo;
  private final float density;

  RadarView(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    bike = context.getDrawable(R.drawable.ic_bike).mutate();
    car = context.getDrawable(R.drawable.ic_car).mutate();
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setContentDescription("Radar off. No live vehicle data.");
  }

  void update(String health, int[] meters, int[] threats, int accent, boolean group, boolean demo) {
    this.health = health;
    this.meters = "LIVE".equals(health) ? meters.clone() : new int[0];
    this.threats = threats.clone();
    this.accent = accent;
    this.group = group;
    this.demo = demo;
    setContentDescription(
        (demo ? "Simulation. " : "")
            + (group ? "Host radar. " : "")
            + health
            + ". "
            + this.meters.length
            + " vehicles. Distances in meters: "
            + Arrays.toString(this.meters));
    invalidate();
  }

  private float d(float value) {
    return value * density;
  }

  private void label(
      Canvas canvas, String text, float x, float y, int size, int color, Paint.Align align) {
    paint.setColor(color);
    paint.setTextSize(d(size));
    paint.setTextAlign(align);
    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    canvas.drawText(text, x, y, paint);
  }

  private void icon(Canvas canvas, Drawable icon, float x, float y, float size, int color) {
    icon.setTint(color);
    icon.setBounds(
        (int) (x - size / 2), (int) (y - size / 2), (int) (x + size / 2), (int) (y + size / 2));
    icon.draw(canvas);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth(), h = getHeight(), center = w * .53f;
    if (h < d(90)) return;
    float top = d(68), bottom = h - d(34), span = Math.max(d(20), bottom - top);
    int muted = Color.rgb(137, 155, 165), line = Color.rgb(46, 60, 69);
    paint.setColor(accent);
    canvas.drawRect(0, d(12), d(4), h - d(10), paint);
    canvas.drawRect(w - d(4), d(12), w, h - d(10), paint);
    paint.setColor(line);
    paint.setStrokeWidth(d(2));
    canvas.drawLine(center - d(43), top, center - d(43), bottom, paint);
    canvas.drawLine(center + d(43), top, center + d(43), bottom, paint);
    for (int distance = 40; distance <= 140; distance += 50) {
      float y = top + distance / 154f * span;
      paint.setColor(line);
      paint.setStrokeWidth(d(1));
      canvas.drawLine(center - d(49), y, center - d(38), y, paint);
      label(canvas, distance + " m", center - d(58), y + d(4), 12, muted, Paint.Align.RIGHT);
    }
    icon(canvas, bike, center, d(33), d(38), Color.WHITE);
    label(canvas, group ? "HOST" : "YOU", center + d(40), d(37), 11, muted, Paint.Align.LEFT);
    if (meters.length > 0) {
      int nearest = Arrays.stream(meters).min().orElse(0);
      label(canvas, nearest + " m", d(16), d(30), 25, Color.WHITE, Paint.Align.LEFT);
      label(canvas, "NEAREST", d(16), d(48), 10, muted, Paint.Align.LEFT);
      // Draw farthest first, so close targets remain visible when detections overlap.
      Integer[] order = new Integer[meters.length];
      for (int i = 0; i < order.length; i++) order[i] = i;
      Arrays.sort(order, (a, b) -> Integer.compare(meters[b], meters[a]));
      for (int i : order) {
        float y = top + Math.min(154, Math.max(0, meters[i])) / 154f * span;
        int color =
            i < threats.length && threats[i] == 2
                ? Color.rgb(255, 104, 94)
                : Color.rgb(255, 196, 85);
        paint.setColor(Color.rgb(9, 13, 16));
        canvas.drawRoundRect(
            center - d(22), y - d(22), center + d(22), y + d(22), d(8), d(8), paint);
        icon(canvas, car, center, y, d(37), color);
      }
    } else {
      label(
          canvas,
          "LIVE".equals(health) ? "No vehicles detected" : "No live radar data",
          center,
          top + span * .5f,
          15,
          muted,
          Paint.Align.CENTER);
    }
    label(
        canvas,
        meters.length > 0 ? meters.length + " VEHICLES BEHIND" : "DISTANCE BEHIND",
        w / 2,
        h - d(10),
        11,
        muted,
        Paint.Align.CENTER);
  }
}
