package dev.nelson.ridewatch.core;

/** State-change chimes. Silence or failure must never emit the empty-stream tone. */
public final class TonePolicy {
  public enum Tone {
    CONNECTED,
    APPROACH,
    URGENT,
    EMPTY,
    LOST
  }

  private boolean live;
  private int previousThreat;
  private boolean hadTargets;

  public Tone update(RadarTracker.Snapshot snapshot) {
    if (snapshot.health() != RadarTracker.Health.LIVE) {
      boolean lost = live;
      reset();
      return lost ? Tone.LOST : null;
    }
    boolean targets = !snapshot.targets().isEmpty();
    int threat = snapshot.targets().stream().mapToInt(RadarFrame.Target::threat).max().orElse(0);
    Tone result = null;
    if (targets && (!hadTargets || threat > previousThreat))
      result = threat == 2 ? Tone.URGENT : Tone.APPROACH;
    else if (hadTargets && !targets) result = Tone.EMPTY;
    else if (!live) result = Tone.CONNECTED;
    live = true;
    hadTargets = targets;
    previousThreat = threat;
    return result;
  }

  public void reset() {
    live = false;
    hadTargets = false;
    previousThreat = 0;
  }
}
