package dev.nelson.ridewatch.core;

import java.util.ArrayList;
import java.util.List;

/** Independently implemented from Varia 5.1.3 static evidence; see protocol report. */
public final class LegacyDecoder {
  public RadarFrame decode(byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Empty frame");
    int header = bytes[0] & 255;
    if ((header & 1) != 0) {
      if (bytes.length != 2) throw new IllegalArgumentException("Invalid error frame");
      return new RadarFrame(RadarFrame.Kind.ERROR, header >>> 4, bytes[1] & 255, List.of());
    }
    // A known six-byte diagnostic shape is not a target stream.
    // Other even headers follow Garmin's bit-0 dispatch, including potential fragments.
    if (header == 6 && bytes.length == 6)
      return new RadarFrame(RadarFrame.Kind.UNSUPPORTED, header >>> 4, -1, List.of());
    if ((bytes.length - 1) % 3 != 0 || bytes.length > 244)
      throw new IllegalArgumentException("Invalid target frame length");
    var targets = new ArrayList<RadarFrame.Target>();
    for (int offset = 1; offset < bytes.length; offset += 3) {
      int flags = bytes[offset] & 255, range = bytes[offset + 1] & 255;
      if (range == 0 || range > 154) continue;
      int wireThreat = flags >>> 6;
      int threat = wireThreat == 3 ? 2 : wireThreat == 2 ? 1 : 0;
      targets.add(
          new RadarFrame.Target(flags & 63, threat, range, (bytes[offset + 2] & 255) * .25));
    }
    return new RadarFrame(RadarFrame.Kind.MEASUREMENT, header >>> 4, -1, targets);
  }
}
