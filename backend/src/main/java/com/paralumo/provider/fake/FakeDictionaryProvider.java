package com.paralumo.provider.fake;

import com.paralumo.provider.DictionaryProvider;
import com.paralumo.provider.ExampleSentence;
import com.paralumo.provider.ProviderLexeme;
import com.paralumo.provider.ProviderSense;
import com.paralumo.provider.SenseIds;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serves hand-authored entries from {@code fake-data/<word>.json} for local work, tests and CI, so
 * nothing depends on the network or the Yandex quota.
 */
public final class FakeDictionaryProvider implements DictionaryProvider {

  static final String SOURCE = "fake";

  private final Map<String, List<ProviderLexeme>> entries;

  private FakeDictionaryProvider(Map<String, List<ProviderLexeme>> entries) {
    this.entries = Map.copyOf(entries);
  }

  /** Loads every file under {@code classpath:fake-data/}; a malformed file stops startup. */
  public static FakeDictionaryProvider fromClasspath() {
    // Same field names as the API (translation_ru, ...), so the files read like lookup responses.
    JsonMapper json =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    Map<String, List<ProviderLexeme>> entries = new HashMap<>();
    try {
      Resource[] files =
          new PathMatchingResourcePatternResolver().getResources("classpath:fake-data/*.json");
      for (Resource file : files) {
        String name = file.getFilename();
        if (name == null) {
          continue;
        }
        String word = name.substring(0, name.length() - ".json".length());
        try (InputStream in = file.getInputStream()) {
          FakeLexeme[] lexemes = json.readValue(in, FakeLexeme[].class);
          entries.put(word.toLowerCase(Locale.ROOT), toProviderLexemes(word, List.of(lexemes)));
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read fake dictionary data", e);
    }
    return new FakeDictionaryProvider(entries);
  }

  @Override
  public String source() {
    return SOURCE;
  }

  @Override
  public Optional<List<ProviderLexeme>> lookup(String query) {
    return Optional.ofNullable(entries.get(query));
  }

  private static List<ProviderLexeme> toProviderLexemes(String word, List<FakeLexeme> lexemes) {
    Set<String> ids = new HashSet<>();
    return lexemes.stream()
        .map(
            lexeme ->
                new ProviderLexeme(
                    lexeme.lemma(),
                    lexeme.pos(),
                    lexeme.transcription(),
                    lexeme.senses().stream()
                        .map(
                            sense -> {
                              String id =
                                  SenseIds.of(
                                      SOURCE, lexeme.lemma(), lexeme.pos(), sense.translationRu());
                              if (!ids.add(id)) {
                                throw new IllegalStateException(
                                    "fake-data/%s.json: two %s senses translate as \"%s\""
                                        .formatted(word, lexeme.pos(), sense.translationRu()));
                              }
                              return new ProviderSense(
                                  id,
                                  sense.translationRu(),
                                  sense.synonymsRu(),
                                  sense.meaningsEn(),
                                  sense.examples());
                            })
                        .toList()))
        .toList();
  }

  /** File format of {@code fake-data/<word>.json}: an array of these. */
  record FakeLexeme(
      String lemma, String pos, @Nullable String transcription, List<FakeSense> senses) {}

  record FakeSense(
      String translationRu,
      List<String> synonymsRu,
      List<String> meaningsEn,
      List<ExampleSentence> examples) {}
}
