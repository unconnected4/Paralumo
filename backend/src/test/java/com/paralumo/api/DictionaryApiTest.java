package com.paralumo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import com.paralumo.config.SessionTokens;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class DictionaryApiTest extends ApiSupport {

  @Test
  void returnsLexemesGroupedByPartOfSpeechInSnakeCase() throws Exception {
    mvc.perform(get("/api/dictionary/lookup").param("q", "  Run "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value("run"))
        .andExpect(jsonPath("$.source_lang").value("en"))
        .andExpect(jsonPath("$.target_lang").value("ru"))
        .andExpect(jsonPath("$.lexemes[0].lemma").value("run"))
        .andExpect(jsonPath("$.lexemes[0].source_lang").value("en"))
        .andExpect(jsonPath("$.lexemes[0].pos").value("verb"))
        .andExpect(jsonPath("$.lexemes[0].transcription").value("rʌn"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].id").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].target_lang").value("ru"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].translation").value("бежать"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].synonyms[0]").value("мчаться"))
        .andExpect(jsonPath("$.lexemes[0].senses[0].meanings[0]").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].examples[0].text").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].examples[0].translation").isString())
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").doesNotExist())
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
        .andExpect(jsonPath("$.source_lang").value("en"))
        .andExpect(jsonPath("$.target_lang").value("ru"))
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
          .executeUpdate(
              "INSERT INTO lexemes (lemma, source_lang, pos) VALUES ('run', 'en', 'verb')");

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
  void isTheSameForEveryoneWhateverTheCredentials() throws Exception {
    String signedOut = lookUpRun(get("/api/dictionary/lookup"));
    Cookie learner = register("learner@example.com");
    String senseId =
        read(
            mvc.perform(get("/api/dictionary/lookup").param("q", "run")).andReturn(),
            "$.lexemes[0].senses[0].id");
    mvc.perform(
            post("/api/cards")
                .cookie(learner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sense_id\":\"%s\"}".formatted(senseId)))
        .andExpect(status().isCreated());

    assertThat(lookUpRun(get("/api/dictionary/lookup").cookie(learner))).isEqualTo(signedOut);
    assertThat(
            lookUpRun(
                get("/api/dictionary/lookup")
                    .cookie(new Cookie(SessionTokens.COOKIE_NAME, "expired-or-forged"))))
        .isEqualTo(signedOut);
    assertThat(
            lookUpRun(
                get("/api/dictionary/lookup")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-forged")))
        .isEqualTo(signedOut);
  }

  @Test
  void foundWordsAreCacheableBySharedCachesForAnHour() throws Exception {
    Cookie learner = register("learner@example.com");

    expectSharedCacheable(
        mvc.perform(get("/api/dictionary/lookup").param("q", "run")).andExpect(status().isOk()),
        "max-age=3600, public");
    expectSharedCacheable(
        mvc.perform(get("/api/dictionary/lookup").param("q", "run").cookie(learner))
            .andExpect(status().isOk()),
        "max-age=3600, public");
    expectSharedCacheable(
        mvc.perform(get("/api/dictionary/lookup").param("q", "")).andExpect(status().isOk()),
        "max-age=3600, public");
  }

  @Test
  void unknownWordsAreCacheableBySharedCachesForFiveMinutes() throws Exception {
    expectSharedCacheable(
        mvc.perform(get("/api/dictionary/lookup").param("q", "zzzz"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Word \"zzzz\" not found")),
        "max-age=300, public");
  }

  @Test
  void otherNotFoundsStayUncached() throws Exception {
    Cookie learner = register("learner@example.com");

    mvc.perform(
            post("/api/cards")
                .cookie(learner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sense_id\":\"s_unknown\"}"))
        .andExpect(status().isNotFound())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
  }

  private String lookUpRun(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.param("q", "run"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static void expectSharedCacheable(ResultActions response, String cacheControl)
      throws Exception {
    // Vary: Origin from CORS is fine; varying on credentials would defeat a shared cache.
    response
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, cacheControl))
        .andExpect(header().doesNotExist(HttpHeaders.PRAGMA))
        .andExpect(header().doesNotExist(HttpHeaders.EXPIRES))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.VARY, everyItem(not(containsStringIgnoringCase("Cookie")))))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.VARY, everyItem(not(containsStringIgnoringCase("Authorization")))));
  }
}
