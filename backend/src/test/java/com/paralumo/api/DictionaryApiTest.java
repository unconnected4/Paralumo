package com.paralumo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import com.paralumo.config.SessionTokens;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class DictionaryApiTest extends ApiSupport {

  @Test
  void returnsLexemesGroupedByPartOfSpeechInSnakeCase() throws Exception {
    mvc.perform(get("/api/dictionary/lookup").param("q", "  Run "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value("run"))
        .andExpect(jsonPath("$.lexemes[0].lemma").value("run"))
        .andExpect(jsonPath("$.lexemes[0].pos").value("verb"))
        .andExpect(jsonPath("$.lexemes[0].transcription").value("rʌn"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].id").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].translation_ru").value("бежать"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].synonyms_ru[0]").value("мчаться"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].meanings_en[0]").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].examples[0].en").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").value(false))
        .andExpect(jsonPath("$.lexemes[0].senses[0].card_id").doesNotExist())
        .andExpect(jsonPath("$.lexemes[1].pos").value("noun"));
  }

  @Test
  void servesEverySampleWord() throws Exception {
    for (String word : new String[] {"run", "bank", "light"}) {
      mvc.perform(get("/api/dictionary/lookup").param("q", word)).andExpect(status().isOk());
    }
  }

  @Test
  void unknownWordIs404WithDetail() throws Exception {
    mvc.perform(get("/api/dictionary/lookup").param("q", "zzzz"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Word \"zzzz\" not found"));
  }

  @Test
  void emptyQueryHasNoLexemes() throws Exception {
    mvc.perform(get("/api/dictionary/lookup").param("q", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value(""))
        .andExpect(jsonPath("$.lexemes").isEmpty());
  }

  @Test
  void repeatedLookupsAreStable() throws Exception {
    String first =
        mvc.perform(get("/api/dictionary/lookup").param("q", "bank"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(get("/api/dictionary/lookup").param("q", "bank"))
        .andExpect(status().isOk())
        .andExpect(content().json(first));
  }

  @Test
  void firstLookupOfAWordWhileAnotherRequestRecordsItSucceeds() throws Exception {
    try (Connection other = dataSource.getConnection()) {
      // The other lookup has recorded the lexeme but not committed yet.
      other.setAutoCommit(false);
      other
          .createStatement()
          .executeUpdate("INSERT INTO lexemes (lemma, lang, pos) VALUES ('run', 'en', 'verb')");

      CompletableFuture<MvcResult> lookingUp =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return mvc.perform(get("/api/dictionary/lookup").param("q", "run")).andReturn();
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      awaitABlockedQuery();
      other.commit();

      MvcResult result = lookingUp.get(10, TimeUnit.SECONDS);
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM lexemes", Integer.class)).isEqualTo(2);
  }

  @Test
  void worksWithAStaleCookie() throws Exception {
    mvc.perform(
            get("/api/dictionary/lookup")
                .param("q", "run")
                .cookie(new Cookie(SessionTokens.COOKIE_NAME, "expired-or-forged")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").value(false));
  }
}
