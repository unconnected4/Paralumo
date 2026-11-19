package com.paralumo.provider;

import java.util.List;

/**
 * One meaning of a lexeme with its translation. Translation and synonyms are in the provider's
 * target language, meanings in its source language.
 *
 * @param id the public, opaque sense id, computed with {@link SenseIds}
 */
public record ProviderSense(
    String id,
    String translation,
    List<String> synonyms,
    List<String> meanings,
    List<ExampleSentence> examples) {

  public ProviderSense {
    synonyms = List.copyOf(synonyms);
    meanings = List.copyOf(meanings);
    examples = List.copyOf(examples);
  }
}
