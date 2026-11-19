package com.paralumo.provider;

/**
 * The languages a provider translates between, as ISO 639-1 lowercase codes.
 *
 * @param source the language of lemmas, transcriptions, meanings and example sentences
 * @param target the language of translations, synonyms and example translations
 */
public record LanguagePair(String source, String target) {}
