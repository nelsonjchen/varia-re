package dev.nelson.ridewatch.core;

import java.util.List;

public final class RadarFrame {
  public enum Kind {
    MEASUREMENT,
    ERROR,
    UNSUPPORTED
  }

  public record Target(int id, int threat, int meters, double speedMps) {}

  public final Kind kind;
  public final int sequence, error;
  public final List<Target> targets;

  public RadarFrame(Kind kind, int sequence, int error, List<Target> targets) {
    this.kind = kind;
    this.sequence = sequence;
    this.error = error;
    this.targets = List.copyOf(targets);
  }
}
