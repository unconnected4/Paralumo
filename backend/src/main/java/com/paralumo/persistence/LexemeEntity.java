package com.paralumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "lexemes")
public class LexemeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(nullable = false)
  private String lemma;

  @Column(name = "source_lang", nullable = false)
  private String sourceLang;

  @Column(nullable = false)
  private String pos;

  private @Nullable String transcription;

  @SuppressWarnings("NullAway.Init") // populated by JPA
  protected LexemeEntity() {}

  public LexemeEntity(String lemma, String sourceLang, String pos, @Nullable String transcription) {
    this.lemma = lemma;
    this.sourceLang = sourceLang;
    this.pos = pos;
    this.transcription = transcription;
  }

  public @Nullable Long getId() {
    return id;
  }

  public String getLemma() {
    return lemma;
  }

  public String getSourceLang() {
    return sourceLang;
  }

  public String getPos() {
    return pos;
  }

  public @Nullable String getTranscription() {
    return transcription;
  }

  public void setTranscription(@Nullable String transcription) {
    this.transcription = transcription;
  }
}
