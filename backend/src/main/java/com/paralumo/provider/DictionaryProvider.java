package com.paralumo.provider;

import java.util.List;
import java.util.Optional;

/**
 * A dictionary source. Implementations live in their own package ({@code fake/}, later {@code
 * yandex/}) and are selected by {@code DICTIONARY_PROVIDER}; callers never know which one runs.
 */
public interface DictionaryProvider {

  /** Stored as {@code senses.source} and mixed into every sense id this provider computes. */
  String source();

  /**
   * Looks up a normalized (trimmed, lowercased, non-empty) query.
   *
   * @return the entries grouped by part of speech, or empty when the word is unknown
   */
  Optional<List<ProviderLexeme>> lookup(String query);
}
