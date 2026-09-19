package dev.nelson.ridewatch.core;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Versioned authenticated local-network packets. Join code is a random 128-bit AES key. */
public final class GroupCodec {
  private static final byte[] MAGIC = {82, 87, 1};
  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  public record Message(
      long session, long sequence, long sentMs, boolean join, RadarTracker.Snapshot snapshot) {}

  public GroupCodec(String hex) {
    if (hex == null || !hex.matches("[0-9a-fA-F]{32}"))
      throw new IllegalArgumentException("Join code must have 32 hex characters");
    key = new byte[16];
    for (int i = 0; i < 16; i++)
      key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
  }

  public static String newKey() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    StringBuilder hex = new StringBuilder();
    for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
    return hex.toString();
  }

  public byte[] encode(Message message) throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(body);
    out.writeLong(message.session);
    out.writeLong(message.sequence);
    out.writeLong(message.sentMs);
    out.writeBoolean(message.join);
    if (!message.join) {
      var snapshot = message.snapshot;
      if (snapshot.targets().size() > 64) throw new IOException("Too many targets");
      out.writeByte(snapshot.health().ordinal());
      out.writeInt(snapshot.error());
      out.writeByte(snapshot.targets().size());
      for (var target : snapshot.targets()) {
        out.writeByte(target.id());
        out.writeByte(target.threat());
        out.writeByte(target.meters());
        out.writeDouble(target.speedMps());
      }
    }
    byte[] nonce = new byte[12];
    random.nextBytes(nonce);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
    cipher.updateAAD(MAGIC);
    ByteArrayOutputStream packet = new ByteArrayOutputStream();
    packet.write(MAGIC);
    packet.write(nonce);
    packet.write(cipher.doFinal(body.toByteArray()));
    return packet.toByteArray();
  }

  public Message decode(byte[] packet, long wallMs) throws Exception {
    if (packet.length < 56
        || packet.length > 1024
        || !Arrays.equals(Arrays.copyOf(packet, 3), MAGIC)) throw new IOException("Packet shape");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Arrays.copyOfRange(packet, 3, 15)));
    cipher.updateAAD(MAGIC);
    DataInputStream in =
        new DataInputStream(
            new ByteArrayInputStream(
                cipher.doFinal(Arrays.copyOfRange(packet, 15, packet.length))));
    long session = in.readLong(), seq = in.readLong(), sent = in.readLong();
    boolean join = in.readBoolean();
    if (seq < 0 || sent > wallMs + 2000 || sent < wallMs - 3000)
      throw new IOException("Expired packet or clocks differ");
    RadarTracker.Snapshot snapshot = null;
    if (!join) {
      int health = in.readUnsignedByte(), error = in.readInt(), count = in.readUnsignedByte();
      if (health >= RadarTracker.Health.values().length || count > 64)
        throw new IOException("Invalid state");
      var targets = new ArrayList<RadarFrame.Target>();
      var ids = new HashSet<Integer>();
      for (int i = 0; i < count; i++) {
        int id = in.readUnsignedByte(),
            threat = in.readUnsignedByte(),
            meters = in.readUnsignedByte();
        double speed = in.readDouble();
        if (id > 63
            || threat > 2
            || meters < 1
            || meters > 154
            || !Double.isFinite(speed)
            || speed < 0
            || speed > 63.75
            || !ids.add(id)) throw new IOException("Invalid target");
        targets.add(new RadarFrame.Target(id, threat, meters, speed));
      }
      if (health != RadarTracker.Health.LIVE.ordinal() && count != 0)
        throw new IOException("Targets on unhealthy stream");
      snapshot =
          new RadarTracker.Snapshot(
              RadarTracker.Health.values()[health], List.copyOf(targets), error);
    }
    if (in.available() != 0) throw new IOException("Trailing data");
    return new Message(session, seq, sent, join, snapshot);
  }
}
