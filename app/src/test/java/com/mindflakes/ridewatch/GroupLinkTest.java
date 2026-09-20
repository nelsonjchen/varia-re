package com.mindflakes.ridewatch;

import static org.junit.Assert.*;

import com.mindflakes.ridewatch.core.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.Test;

public class GroupLinkTest {
  @Test
  public void twoPeersReceiveHostStateOnRealUdpSocket() throws Exception {
    String key = GroupCodec.newKey();
    CountDownLatch received = new CountDownLatch(1), receivedSecond = new CountDownLatch(1);
    var expected =
        new RadarTracker.Snapshot(
            RadarTracker.Health.LIVE, List.of(new RadarFrame.Target(4, 2, 60, 20)), -1);
    var failures = new CopyOnWriteArrayList<String>();
    GroupLink.Listener listener =
        new GroupLink.Listener() {
          public void snapshot(RadarTracker.Snapshot value) {
            if (expected.equals(value)) received.countDown();
          }

          public void status(String text) {
            failures.add(text);
          }
        };
    try (GroupLink host = new GroupLink(true, "", key, listener);
        GroupLink first = new GroupLink(false, "127.0.0.1", key, listener);
        GroupLink second =
            new GroupLink(
                false,
                "127.0.0.1",
                key,
                new GroupLink.Listener() {
                  public void snapshot(RadarTracker.Snapshot value) {
                    if (expected.equals(value)) receivedSecond.countDown();
                  }

                  public void status(String text) {
                    failures.add(text);
                  }
                })) {
      host.publish(expected);
      host.start();
      first.start();
      second.start();
      assertTrue("Host packets should reach peers", received.await(4, TimeUnit.SECONDS));
      assertTrue("Second peer receives independently", receivedSecond.await(4, TimeUnit.SECONDS));
      assertTrue(failures.toString(), failures.isEmpty());
    }
  }
}
