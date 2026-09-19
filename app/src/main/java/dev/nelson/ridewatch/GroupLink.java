package dev.nelson.ridewatch;

import dev.nelson.ridewatch.core.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;

/** LAN/hotspot-only prototype; one radar owner, up to eight authenticated listeners. */
final class GroupLink implements AutoCloseable {
  static final int PORT = 43157;

  interface Listener {
    void snapshot(RadarTracker.Snapshot value);

    void status(String text);
  }

  private final boolean host;
  private final String hostAddress;
  private final GroupCodec codec;
  private final Listener listener;
  private volatile boolean running;
  private volatile RadarTracker.Snapshot latest =
      new RadarTracker.Snapshot(RadarTracker.Health.DISCONNECTED, List.of(), -1);
  private volatile DatagramSocket socket;
  private final long session = new SecureRandom().nextLong();

  GroupLink(boolean host, String address, String key, Listener listener) {
    this.host = host;
    hostAddress = address;
    codec = new GroupCodec(key);
    this.listener = listener;
  }

  void publish(RadarTracker.Snapshot snapshot) {
    latest = snapshot;
  }

  void start() {
    running = true;
    new Thread(this::loop, "radar-group").start();
  }

  private void loop() {
    Map<SocketAddress, Long> peers = new HashMap<>();
    Map<String, Long> sequences = new HashMap<>();
    long seq = 0, nextSend = 0;
    InetSocketAddress destination = null;
    try {
      if (!host) {
        InetAddress address = InetAddress.getByName(hostAddress);
        if (!address.isSiteLocalAddress()
            && !address.isLinkLocalAddress()
            && !address.isLoopbackAddress())
          throw new IllegalArgumentException("Use a local Wi-Fi or hotspot address");
        destination = new InetSocketAddress(address, PORT);
      }
      synchronized (this) {
        if (!running) return;
        socket = new DatagramSocket(host ? PORT : 0);
      }
      socket.setSoTimeout(100);
      while (running) {
        long now = System.currentTimeMillis();
        if (now >= nextSend) {
          byte[] bytes =
              codec.encode(
                  new GroupCodec.Message(session, seq++, now, !host, host ? latest : null));
          if (host) {
            peers.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
            for (var peer : peers.keySet())
              socket.send(new DatagramPacket(bytes, bytes.length, peer));
          } else socket.send(new DatagramPacket(bytes, bytes.length, destination));
          nextSend = now + 500;
        }
        byte[] buffer = new byte[1025];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
          socket.receive(packet);
        } catch (SocketTimeoutException ignored) {
          continue;
        }
        if (!host && !packet.getSocketAddress().equals(destination)) continue;
        try {
          var message =
              codec.decode(Arrays.copyOf(buffer, packet.getLength()), System.currentTimeMillis());
          String identity = packet.getSocketAddress() + ":" + message.session();
          if (message.sequence() <= sequences.getOrDefault(identity, -1L)) continue;
          if (sequences.size() > 128) sequences.clear();
          sequences.put(identity, message.sequence());
          if (host
              && message.join()
              && (peers.containsKey(packet.getSocketAddress()) || peers.size() < 8))
            peers.put(packet.getSocketAddress(), System.currentTimeMillis());
          else if (!host && !message.join()) listener.snapshot(message.snapshot());
        } catch (Exception ignored) {
          /* Unauthenticated, expired, malformed or replayed input does not update health. */
        }
      }
    } catch (Exception error) {
      if (running) listener.status("Group connection unavailable: " + error.getMessage());
    } finally {
      DatagramSocket open = socket;
      if (open != null) open.close();
    }
  }

  public synchronized void close() {
    running = false;
    DatagramSocket open = socket;
    if (open != null) open.close();
  }
}
