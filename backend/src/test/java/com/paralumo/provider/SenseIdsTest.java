package com.paralumo.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SenseIdsTest {

  private static final LanguagePair PAIR = new LanguagePair("en", "ru");

  @Test
  void isDeterministicAndOpaque() {
    String id = SenseIds.of("fake", PAIR, "run", "verb", "бежать");

    assertThat(SenseIds.of("fake", PAIR, "run", "verb", "бежать")).isEqualTo(id);
    assertThat(id).matches("s_[0-9a-f]{20}");
  }

  @Test
  void ignoresCaseOfLemmaPosAndTranslation() {
    assertThat(SenseIds.of("fake", PAIR, "Run", "Verb", "Бежать"))
        .isEqualTo(SenseIds.of("fake", PAIR, "run", "verb", "бежать"));
  }

  @Test
  void differsByEveryPart() {
    String id = SenseIds.of("fake", PAIR, "run", "verb", "бежать");

    assertThat(SenseIds.of("yandex", PAIR, "run", "verb", "бежать")).isNotEqualTo(id);
    assertThat(SenseIds.of("fake", new LanguagePair("de", "ru"), "run", "verb", "бежать"))
        .isNotEqualTo(id);
    assertThat(SenseIds.of("fake", new LanguagePair("en", "uk"), "run", "verb", "бежать"))
        .isNotEqualTo(id);
    assertThat(SenseIds.of("fake", PAIR, "ran", "verb", "бежать")).isNotEqualTo(id);
    assertThat(SenseIds.of("fake", PAIR, "run", "noun", "бежать")).isNotEqualTo(id);
    assertThat(SenseIds.of("fake", PAIR, "run", "verb", "управлять")).isNotEqualTo(id);
  }

  @Test
  void pinsTheIdsAlreadyHandedOut() {
    // Saved cards and cached lookups hold these ids; changing the hash input orphans them.
    assertThat(SenseIds.of("fake", PAIR, "run", "verb", "бежать"))
        .isEqualTo("s_3746a995dfb8c96b1113");
  }

  @Test
  void separatesParts() {
    assertThat(SenseIds.of("fake", PAIR, "ab", "c", "d"))
        .isNotEqualTo(SenseIds.of("fake", PAIR, "a", "bc", "d"));
    assertThat(SenseIds.of("fake", new LanguagePair("en", "rub"), "c", "d", "e"))
        .isNotEqualTo(SenseIds.of("fake", new LanguagePair("enr", "ub"), "c", "d", "e"));
  }
}
