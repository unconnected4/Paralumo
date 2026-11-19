package com.paralumo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code GET /api/cards/saved}: which senses of a lookup the signed-in user has saved. */
@IntegrationTest
class SavedSensesApiTest extends ApiSupport {

  private Cookie learner;
  private String firstSenseId;
  private String secondSenseId;

  @BeforeEach
  void signInAndLookUp() throws Exception {
    learner = register("learner@example.com");
    MvcResult lookup =
        mvc.perform(get("/api/dictionary/lookup").param("q", "run"))
            .andExpect(status().isOk())
            .andReturn();
    firstSenseId = read(lookup, "$.lexemes[0].senses[0].id");
    secondSenseId = read(lookup, "$.lexemes[0].senses[1].id");
  }

  @Test
  void listsOnlyTheSavedSensesWithTheirCards() throws Exception {
    String cardId = save(learner, firstSenseId);

    mvc.perform(savedState(learner, firstSenseId, secondSenseId, "s_unknown"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].sense_id").value(firstSenseId))
        .andExpect(jsonPath("$[0].card_id").value(cardId));
  }

  @Test
  void nothingSavedIsAnEmptyList() throws Exception {
    mvc.perform(savedState(learner, firstSenseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void deletedCardsAreNotSaved() throws Exception {
    String cardId = save(learner, firstSenseId);
    mvc.perform(delete("/api/cards/{id}", cardId).cookie(learner))
        .andExpect(status().isNoContent());

    mvc.perform(savedState(learner, firstSenseId)).andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void duplicateIdsGiveOneEntry() throws Exception {
    save(learner, firstSenseId);

    mvc.perform(savedState(learner, firstSenseId, firstSenseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void acceptsUpTo100Ids() throws Exception {
    save(learner, firstSenseId);
    String[] ids = Collections.nCopies(100, firstSenseId).toArray(String[]::new);

    mvc.perform(savedState(learner, ids))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void isNeverCached() throws Exception {
    mvc.perform(savedState(learner, firstSenseId))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")));
  }

  @Test
  void missingTooManyOrBlankIdsAre422() throws Exception {
    mvc.perform(get("/api/cards/saved").cookie(learner))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("sense_id is required"));
    mvc.perform(savedState(learner, Collections.nCopies(101, firstSenseId).toArray(String[]::new)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("sense_id must be given 1 to 100 times"));
    mvc.perform(savedState(learner, firstSenseId, " "))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("sense_id must not be blank"));
  }

  @Test
  void signedOutIs401EvenWhenTheRequestIsInvalid() throws Exception {
    mvc.perform(get("/api/cards/saved").param("sense_id", firstSenseId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Authentication required"));
    mvc.perform(get("/api/cards/saved")).andExpect(status().isUnauthorized());
  }

  @Test
  void usersOnlySeeTheirOwnSavedSenses() throws Exception {
    String learnersCard = save(learner, firstSenseId);
    Cookie other = register("other@example.com");

    mvc.perform(savedState(other, firstSenseId, secondSenseId)).andExpect(jsonPath("$").isEmpty());

    String othersCard = save(other, secondSenseId);
    mvc.perform(savedState(other, firstSenseId, secondSenseId))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].sense_id").value(secondSenseId))
        .andExpect(jsonPath("$[0].card_id").value(othersCard));
    mvc.perform(savedState(learner, firstSenseId, secondSenseId))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].sense_id").value(firstSenseId))
        .andExpect(jsonPath("$[0].card_id").value(learnersCard));
  }

  private String save(Cookie session, String senseId) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/cards")
                    .cookie(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sense_id\":\"%s\"}".formatted(senseId)))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.id");
  }

  private static MockHttpServletRequestBuilder savedState(Cookie session, String... senseIds) {
    return get("/api/cards/saved").cookie(session).param("sense_id", senseIds);
  }
}
