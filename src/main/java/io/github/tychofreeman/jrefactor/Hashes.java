package io.github.tychofreeman.jrefactor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 digests in hexadecimal, for cache keys and change checks. */
final class Hashes {

  private Hashes() {}

  /** The SHA-256 of {@code parts}, one after another, as 64 lowercase hex digits. */
  static String sha256Hex(byte[]... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] part : parts) {
        digest.update(part);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
