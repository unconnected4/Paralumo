package com.paralumo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import com.paralumo.config.SessionTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

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
  void worksWithAStaleCookie() throws Exception {
    mvc.perform(
            get("/api/dictionary/lookup")
                .param("q", "run")
                .cookie(new Cookie(SessionTokens.COOKIE_NAME, "expired-or-forged")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").value(false));
  }
}
