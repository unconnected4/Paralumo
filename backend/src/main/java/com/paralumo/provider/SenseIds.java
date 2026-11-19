package com.paralumo.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Computes the public {@code sense_id}: a hash of provider, language pair, lemma, part of speech
 * and main translation (backend/intent.md). The pair keeps senses apart that would otherwise
 * collide across pairs, such as a lemma whose translation is spelled the same in two related target
 * languages. It needs no database, so ids stay the same across resets. Clients treat it as opaque.
 */
public final class SenseIds {

  private static final char SEPARATOR = '\u001f';

  private SenseIds() {}

  public static String of(
      String source, LanguagePair pair, String lemma, String pos, String translation) {
    String key =
        String.join(
            String.valueOf(SEPARATOR),
            source,
            pair.source(),
            pair.target(),
            lemma.toLowerCase(Locale.ROOT),
            pos.toLowerCase(Locale.ROOT),
            translation.toLowerCase(Locale.ROOT));
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
