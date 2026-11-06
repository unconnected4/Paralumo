package com.paralumo.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code DICTIONARY_PROVIDER} selects the {@link com.paralumo.provider.DictionaryProvider}
 * implementation ({@code fake} for now). The bean is chosen by {@code @ConditionalOnProperty} on
 * {@code paralumo.dictionary.provider}; this record makes the setting required.
 */
@Validated
@ConfigurationProperties("paralumo.dictionary")
public record DictionaryProperties(@NotBlank String provider) {}
