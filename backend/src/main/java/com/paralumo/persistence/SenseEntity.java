package com.paralumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "senses")
public class SenseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lexeme_id", nullable = false)
  private LexemeEntity lexeme;

  @Column(nullable = false)
  private String source;

  @Column(name = "source_key", nullable = false)
  private String sourceKey;

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

  @SuppressWarnings("NullAway.Init") // populated by JPA
  protected SenseEntity() {}

  public SenseEntity(LexemeEntity lexeme, String source, String sourceKey) {
    this.lexeme = lexeme;
    this.source = source;
    this.sourceKey = sourceKey;
    this.translationRu = "";
    this.synonymsRu = List.of();
    this.meaningsEn = List.of();
    this.examples = List.of();
  }

  /** Replaces the sense data with what the provider returned most recently. */
  public void update(
      String translationRu,
      List<String> synonymsRu,
      List<String> meaningsEn,
      List<Example> examples) {
    this.translationRu = translationRu;
    this.synonymsRu = List.copyOf(synonymsRu);
    this.meaningsEn = List.copyOf(meaningsEn);
    this.examples = List.copyOf(examples);
  }

  public @Nullable Long getId() {
    return id;
  }

  public LexemeEntity getLexeme() {
    return lexeme;
  }

  public String getSource() {
    return source;
  }

  public String getSourceKey() {
    return sourceKey;
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
}
