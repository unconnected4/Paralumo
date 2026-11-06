package com.paralumo.provider;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One part of speech of a looked-up word, as a provider returns it. */
public record ProviderLexeme(
    String lemma, String pos, @Nullable String transcription, List<ProviderSense> senses) {

  public ProviderLexeme {
    senses = List.copyOf(senses);
  }
}
