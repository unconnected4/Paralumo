package com.paralumo.service;

import com.paralumo.provider.DictionaryProvider;
import com.paralumo.provider.ProviderLexeme;
import com.paralumo.provider.ProviderSense;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class LookupService {

  private final DictionaryProvider provider;
  private final SenseCatalog catalog;
  private final CardService cards;

  public LookupService(DictionaryProvider provider, SenseCatalog catalog, CardService cards) {
    this.provider = provider;
    this.catalog = catalog;
    this.cards = cards;
  }

  /**
   * The entries found for a word, and the user's cards for the senses they have saved (sense id to
   * card id).
   */
  public record LookupResult(
      String query, List<ProviderLexeme> lexemes, Map<String, UUID> savedCardIds) {}

  /**
   * Looks up a word. An empty query has no entries; an unknown word is a {@link NotFoundException}.
   *
   * @param userId the signed-in user, or null when signed out (then nothing is saved)
   */
  public LookupResult lookup(String rawQuery, @Nullable UUID userId) {
    String query = rawQuery.trim().toLowerCase(Locale.ROOT);
    if (query.isEmpty()) {
      return new LookupResult("", List.of(), Map.of());
    }
    List<ProviderLexeme> found =
        provider
            .lookup(query)
            .orElseThrow(() -> new NotFoundException("Word \"%s\" not found".formatted(query)));
    try {
      catalog.record(provider.source(), found);
    } catch (DataIntegrityViolationException e) {
      // A concurrent lookup of the same new word inserted the rows first; now they exist.
      catalog.record(provider.source(), found);
    }
    Map<String, UUID> saved =
        userId == null
            ? Map.of()
            : cards.savedCardIds(
                userId,
                found.stream()
                    .flatMap(lexeme -> lexeme.senses().stream())
                    .map(ProviderSense::id)
                    .toList());
    return new LookupResult(query, found, saved);
  }
}
