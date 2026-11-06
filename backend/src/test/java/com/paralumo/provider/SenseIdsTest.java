package com.paralumo.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SenseIdsTest {

  @Test
  void isDeterministicAndOpaque() {
    String id = SenseIds.of("fake", "run", "verb", "бежать");

    assertThat(SenseIds.of("fake", "run", "verb", "бежать")).isEqualTo(id);
    assertThat(id).matches("s_[0-9a-f]{20}");
  }

  @Test
  void ignoresCaseOfLemmaPosAndTranslation() {
    assertThat(SenseIds.of("fake", "Run", "Verb", "Бежать"))
        .isEqualTo(SenseIds.of("fake", "run", "verb", "бежать"));
  }

  @Test
  void differsByEveryPart() {
    String id = SenseIds.of("fake", "run", "verb", "бежать");

    assertThat(SenseIds.of("yandex", "run", "verb", "бежать")).isNotEqualTo(id);
    assertThat(SenseIds.of("fake", "ran", "verb", "бежать")).isNotEqualTo(id);
    assertThat(SenseIds.of("fake", "run", "noun", "бежать")).isNotEqualTo(id);
    assertThat(SenseIds.of("fake", "run", "verb", "управлять")).isNotEqualTo(id);
  }

  @Test
  void separatesParts() {
    assertThat(SenseIds.of("fake", "ab", "c", "d"))
        .isNotEqualTo(SenseIds.of("fake", "a", "bc", "d"));
  }
}
