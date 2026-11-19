package com.paralumo.service;

import com.paralumo.provider.DictionaryProvider;
import com.paralumo.provider.LanguagePair;
import com.paralumo.provider.ProviderLexeme;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Dictionary lookups. The result never depends on who asks, so it can be cached by a reverse proxy;
 * which senses a user saved is a separate request ({@link CardService#savedCardIds}).
 */
@Service
public class LookupService {

  private final DictionaryProvider provider;
  private final SenseCatalog catalog;

  public LookupService(DictionaryProvider provider, SenseCatalog catalog) {
    this.provider = provider;
    this.catalog = catalog;
  }

  /** The entries found for a word, and the languages they are in. */
  public record LookupResult(String query, LanguagePair pair, List<ProviderLexeme> lexemes) {}

  /**
   * Looks up a word. An empty query has no entries; an unknown word is a {@link NotFoundException}.
   */
  public LookupResult lookup(String rawQuery) {
    String query = rawQuery.trim().toLowerCase(Locale.ROOT);
    LanguagePair pair = provider.pair();
    if (query.isEmpty()) {
      return new LookupResult("", pair, List.of());
    }
    List<ProviderLexeme> found =
        provider
            .lookup(query)
            .orElseThrow(() -> new NotFoundException("Word \"%s\" not found".formatted(query)));
    try {
      catalog.record(provider.source(), pair, found);
    } catch (DataIntegrityViolationException e) {
      // A concurrent lookup of the same new word inserted the rows first; now they exist.
      catalog.record(provider.source(), pair, found);
    }
    return new LookupResult(query, pair, found);
  }
}
