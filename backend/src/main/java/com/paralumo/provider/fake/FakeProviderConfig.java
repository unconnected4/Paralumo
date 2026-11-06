package com.paralumo.provider.fake;

import com.paralumo.provider.DictionaryProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "paralumo.dictionary.provider", havingValue = "fake")
class FakeProviderConfig {

  @Bean
  DictionaryProvider fakeDictionaryProvider() {
    return FakeDictionaryProvider.fromClasspath();
  }
}
