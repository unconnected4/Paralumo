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

  @Column(name = "target_lang", nullable = false)
  private String targetLang;

  @Column(nullable = false)
  private String translation;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> synonyms;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> meanings;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private List<Example> examples;

  @SuppressWarnings("NullAway.Init") // populated by JPA
  protected SenseEntity() {}

  public SenseEntity(LexemeEntity lexeme, String source, String sourceKey, String targetLang) {
    this.lexeme = lexeme;
    this.source = source;
    this.sourceKey = sourceKey;
    this.targetLang = targetLang;
    this.translation = "";
    this.synonyms = List.of();
    this.meanings = List.of();
    this.examples = List.of();
  }

  /** Replaces the sense data with what the provider returned most recently. */
  public void update(
      String translation, List<String> synonyms, List<String> meanings, List<Example> examples) {
    this.translation = translation;
    this.synonyms = List.copyOf(synonyms);
    this.meanings = List.copyOf(meanings);
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

  public String getTargetLang() {
    return targetLang;
  }

  public String getTranslation() {
    return translation;
  }

  public List<String> getSynonyms() {
    return synonyms;
  }

  public List<String> getMeanings() {
    return meanings;
  }

  public List<Example> getExamples() {
    return examples;
  }
}
