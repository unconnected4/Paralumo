package com.paralumo.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Computes the public {@code sense_id}: a hash of provider, lemma, part of speech and main
 * translation (backend/intent.md). It needs no database, so ids stay the same across resets.
 * Clients treat it as opaque.
 */
public final class SenseIds {

  private static final char SEPARATOR = '\u001f';

  private SenseIds() {}

  public static String of(String source, String lemma, String pos, String translationRu) {
    String key =
        String.join(
            String.valueOf(SEPARATOR),
            source,
            lemma.toLowerCase(Locale.ROOT),
            pos.toLowerCase(Locale.ROOT),
            translationRu.toLowerCase(Locale.ROOT));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
      // 80 bits is plenty for the number of senses a dictionary has, and keeps ids short.
      return "s_" + HexFormat.of().formatHex(digest, 0, 10);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JVM ships SHA-256", e);
    }
  }
}
