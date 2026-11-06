package com.paralumo.service;

import com.paralumo.persistence.Example;
import com.paralumo.persistence.LexemeEntity;
import com.paralumo.persistence.LexemeRepository;
import com.paralumo.persistence.SenseEntity;
import com.paralumo.persistence.SenseRepository;
import com.paralumo.provider.ProviderLexeme;
import com.paralumo.provider.ProviderSense;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code lexemes} and {@code senses} in step with what providers return, so a sense id seen
 * in a lookup can later be saved as a card. Sense ids are hashes and can't be reversed, which is
 * why every looked-up sense is recorded.
 */
@Service
class SenseCatalog {

  private static final String LANG_EN = "en";

  private final LexemeRepository lexemes;
  private final SenseRepository senses;

  SenseCatalog(LexemeRepository lexemes, SenseRepository senses) {
    this.lexemes = lexemes;
    this.senses = senses;
  }

  @Transactional
  public void record(String source, List<ProviderLexeme> found) {
    for (ProviderLexeme lexeme : found) {
      LexemeEntity lexemeRow =
          lexemes
              .findByLemmaAndPos(lexeme.lemma(), lexeme.pos())
              .orElseGet(
                  () ->
                      lexemes.save(
                          new LexemeEntity(
                              lexeme.lemma(), LANG_EN, lexeme.pos(), lexeme.transcription())));
      lexemeRow.setTranscription(lexeme.transcription());
      for (ProviderSense sense : lexeme.senses()) {
        SenseEntity senseRow =
            senses
                .findBySourceKey(sense.id())
                .orElseGet(() -> senses.save(new SenseEntity(lexemeRow, source, sense.id())));
        senseRow.update(
            sense.translationRu(),
            sense.synonymsRu(),
            sense.meaningsEn(),
            sense.examples().stream().map(e -> new Example(e.en(), e.ru())).toList());
      }
    }
  }
}
