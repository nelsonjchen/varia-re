package dev.nelson.ridewatch.core;

import java.util.*;

/** Monotonic-clock freshness, independent of Android, transport and UI. */
public final class RadarTracker {
  public enum Health {
    DISCONNECTED,
    WAITING,
    LIVE,
    STALE,
    ERROR
  }

  public record Snapshot(Health health, List<RadarFrame.Target> targets, int error) {}

  private record Seen(RadarFrame.Target target, long at) {}

  private final Map<Integer, Seen> tracks = new HashMap<>();
  private boolean connected;
  private long lastFrame = -1;
  private int error = -1;

  public void connected() {
    reset();
    connected = true;
  }

  public void reset() {
    connected = false;
    lastFrame = -1;
    error = -1;
    tracks.clear();
  }

  public void accept(RadarFrame frame, long now) {
    if (!connected || frame.kind == RadarFrame.Kind.UNSUPPORTED) return;
    if (frame.kind == RadarFrame.Kind.ERROR) {
      error = frame.error;
      tracks.clear();
      return;
    }
    error = -1;
    lastFrame = now;
    for (var target : frame.targets) tracks.put(target.id(), new Seen(target, now));
  }

  public Snapshot snapshot(long now) {
    if (!connected) return new Snapshot(Health.DISCONNECTED, List.of(), -1);
    if (error >= 0) return new Snapshot(Health.ERROR, List.of(), error);
    if (lastFrame < 0) return new Snapshot(Health.WAITING, List.of(), -1);
    if (now - lastFrame >= 2000) {
      tracks.clear();
      return new Snapshot(Health.STALE, List.of(), -1);
    }
    tracks.values().removeIf(seen -> now - seen.at >= (seen.target.meters() < 5 ? 500 : 2000));
    var targets = new ArrayList<RadarFrame.Target>();
    for (var seen : tracks.values()) targets.add(seen.target);
    targets.sort(Comparator.comparingInt(RadarFrame.Target::meters));
    return new Snapshot(Health.LIVE, List.copyOf(targets), -1);
  }
}
