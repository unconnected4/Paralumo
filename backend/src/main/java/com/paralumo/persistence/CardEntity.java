package com.paralumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** A row of {@code user_cards}: a sense a user saved, with a copy of the sense data. */
@Entity
@Table(name = "user_cards")
public class CardEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "sense_id", nullable = false)
  private Long senseId;

  @Column(name = "sense_key", nullable = false)
  private String senseKey;

  @Column(nullable = false)
  private String lemma;

  @Column(nullable = false)
  private String pos;

  private @Nullable String transcription;

  @Column(name = "translation_ru", nullable = false)
  private String translationRu;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "synonyms_ru", nullable = false)
  private List<String> synonymsRu;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "meanings_en", nullable = false)
  private List<String> meaningsEn;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private List<Example> examples;

  private @Nullable String notes;

  @Column(name = "custom_example")
  private @Nullable String customExample;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private @Nullable Instant deletedAt;

  @SuppressWarnings("NullAway.Init") // populated by JPA
  protected CardEntity() {}

  /** A new card holding a copy of the sense's current data. */
  public CardEntity(UUID id, UUID userId, long senseId, SenseEntity sense, Instant createdAt) {
    LexemeEntity lexeme = sense.getLexeme();
    this.id = id;
    this.userId = userId;
    this.senseId = senseId;
    this.senseKey = sense.getSourceKey();
    this.lemma = lexeme.getLemma();
    this.pos = lexeme.getPos();
    this.transcription = lexeme.getTranscription();
    this.translationRu = sense.getTranslationRu();
    this.synonymsRu = List.copyOf(sense.getSynonymsRu());
    this.meaningsEn = List.copyOf(sense.getMeaningsEn());
    this.examples = List.copyOf(sense.getExamples());
    this.createdAt = createdAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void markDeleted(Instant when) {
    this.deletedAt = when;
  }

  public void restore() {
    this.deletedAt = null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getSenseKey() {
    return senseKey;
  }

  public String getLemma() {
    return lemma;
  }

  public String getPos() {
    return pos;
  }

  public @Nullable String getTranscription() {
    return transcription;
  }

  public String getTranslationRu() {
    return translationRu;
  }

  public List<String> getSynonymsRu() {
    return synonymsRu;
  }

  public List<String> getMeaningsEn() {
    return meaningsEn;
  }

  public List<Example> getExamples() {
    return examples;
  }

  public @Nullable String getNotes() {
    return notes;
  }

  public @Nullable String getCustomExample() {
    return customExample;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
