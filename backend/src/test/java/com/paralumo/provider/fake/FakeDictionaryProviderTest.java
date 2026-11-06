package com.paralumo.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.paralumo.provider.ProviderLexeme;
import com.paralumo.provider.ProviderSense;
import com.paralumo.provider.SenseIds;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeDictionaryProviderTest {

  private final FakeDictionaryProvider provider = FakeDictionaryProvider.fromClasspath();

  @Test
  void servesTheSampleWordsTheWebUiOffers() {
    for (String word : List.of("run", "bank", "light")) {
      assertThat(provider.lookup(word)).as(word).isPresent();
    }
  }

  @Test
  void mapsTheFileIntoLexemesAndSenses() {
    List<ProviderLexeme> run = provider.lookup("run").orElseThrow();

    assertThat(run).extracting(ProviderLexeme::pos).containsExactly("verb", "noun");
    ProviderLexeme verb = run.getFirst();
    assertThat(verb.lemma()).isEqualTo("run");
    assertThat(verb.transcription()).isEqualTo("rʌn");
    ProviderSense first = verb.senses().getFirst();
    assertThat(first.translationRu()).isEqualTo("бежать");
    assertThat(first.synonymsRu()).containsExactly("мчаться", "нестись");
    assertThat(first.meaningsEn()).containsExactly("move at a speed faster than a walk");
    assertThat(first.examples()).singleElement().satisfies(e -> assertThat(e.ru()).isNotBlank());
    assertThat(first.id()).isEqualTo(SenseIds.of("fake", "run", "verb", "бежать"));
  }

  @Test
  void unknownWordIsEmpty() {
    assertThat(provider.lookup("zzzz")).isEmpty();
  }
}
