package com.paralumo.provider;

import java.util.List;

/**
 * One meaning of a lexeme with its Russian translation.
 *
 * @param id the public, opaque sense id, computed with {@link SenseIds}
 */
public record ProviderSense(
    String id,
    String translationRu,
    List<String> synonymsRu,
    List<String> meaningsEn,
    List<ExampleSentence> examples) {

  public ProviderSense {
    synonymsRu = List.copyOf(synonymsRu);
    meaningsEn = List.copyOf(meaningsEn);
    examples = List.copyOf(examples);
  }
}
