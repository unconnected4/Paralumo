package com.paralumo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class CardApiTest extends ApiSupport {

  private Cookie learner;
  private String runSenseId;
  private String bankSenseId;

  @BeforeEach
  void signInAndLookUp() throws Exception {
    learner = register("learner@example.com");
    runSenseId = firstSenseId("run");
    bankSenseId = firstSenseId("bank");
  }

  @Test
  void savingASenseCopiesItIntoACard() throws Exception {
    save(learner, runSenseId)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.sense_id").value(runSenseId))
        .andExpect(jsonPath("$.lemma").value("run"))
        .andExpect(jsonPath("$.pos").value("verb"))
        .andExpect(jsonPath("$.transcription").value("rʌn"))
        .andExpect(jsonPath("$.translation_ru").value("бежать"))
        .andExpect(jsonPath("$.synonyms_ru[0]").value("мчаться"))
        .andExpect(jsonPath("$.meanings_en[0]").isString())
        .andExpect(jsonPath("$.examples[0].ru").isString())
        .andExpect(jsonPath("$.created_at").isString())
        .andExpect(jsonPath("$.notes").doesNotExist());

    mvc.perform(get("/api/dictionary/lookup").param("q", "run").cookie(learner))
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").value(true))
        .andExpect(jsonPath("$.lexemes[0].senses[1].saved").value(false));
  }

  @Test
  void savingTwiceReturnsTheExistingCardWith200() throws Exception {
    String id = read(save(learner, runSenseId).andExpect(status().isCreated()).andReturn(), "$.id");

    save(learner, runSenseId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    mvc.perform(get("/api/cards").cookie(learner)).andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void listsNewestFirst() throws Exception {
    save(learner, runSenseId);
    save(learner, bankSenseId);

    mvc.perform(get("/api/cards").cookie(learner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].lemma").value("bank"))
        .andExpect(jsonPath("$[1].lemma").value("run"));
  }

  @Test
  void deleteRemovesTheCardAndSavingAgainRestoresIt() throws Exception {
    String id = read(save(learner, runSenseId).andReturn(), "$.id");

    mvc.perform(delete("/api/cards/{id}", id).cookie(learner)).andExpect(status().isNoContent());
    mvc.perform(get("/api/cards").cookie(learner)).andExpect(jsonPath("$").isEmpty());
    mvc.perform(get("/api/dictionary/lookup").param("q", "run").cookie(learner))
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").value(false));
    mvc.perform(delete("/api/cards/{id}", id).cookie(learner)).andExpect(status().isNotFound());

    save(learner, runSenseId).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
    mvc.perform(get("/api/cards").cookie(learner)).andExpect(jsonPath("$[0].id").value(id));
  }

  @Test
  void deletedCardsKeepTheirRow() throws Exception {
    String id = read(save(learner, runSenseId).andReturn(), "$.id");

    mvc.perform(delete("/api/cards/{id}", id).cookie(learner));

    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM user_cards WHERE id = ?",
                Boolean.class,
                UUID.fromString(id)))
        .isTrue();
  }

  @Test
  void unknownSenseIs404AndMissingSenseIdIs422() throws Exception {
    save(learner, "s_unknown")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Sense not found"));
    mvc.perform(
            post("/api/cards")
                .cookie(learner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("sense_id is required"));
    mvc.perform(post("/api/cards").cookie(learner).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void unknownOrMalformedCardIdIs404() throws Exception {
    mvc.perform(delete("/api/cards/{id}", UUID.randomUUID()).cookie(learner))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Card not found"));
    mvc.perform(delete("/api/cards/not-a-uuid").cookie(learner)).andExpect(status().isNotFound());
  }

  @Test
  void everyRouteIs401WhenSignedOut() throws Exception {
    mvc.perform(get("/api/cards"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Authentication required"));
    mvc.perform(
            post("/api/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(senseBody(runSenseId)))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/cards/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
  }

  @Test
  void usersOnlySeeAndChangeTheirOwnCards() throws Exception {
    String learnersCard = read(save(learner, runSenseId).andReturn(), "$.id");
    Cookie other = register("other@example.com");

    mvc.perform(get("/api/cards").cookie(other)).andExpect(jsonPath("$").isEmpty());
    mvc.perform(get("/api/dictionary/lookup").param("q", "run").cookie(other))
        .andExpect(jsonPath("$.lexemes[0].senses[0].saved").value(false));
    mvc.perform(delete("/api/cards/{id}", learnersCard).cookie(other))
        .andExpect(status().isNotFound());

    save(other, runSenseId)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(not(learnersCard)));
    save(other, bankSenseId);

    mvc.perform(get("/api/cards").cookie(learner))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(learnersCard));
    mvc.perform(get("/api/cards").cookie(other)).andExpect(jsonPath("$.length()").value(2));
  }

  private String firstSenseId(String word) throws Exception {
    MvcResult result =
        mvc.perform(get("/api/dictionary/lookup").param("q", word))
            .andExpect(status().isOk())
            .andReturn();
    return read(result, "$.lexemes[0].senses[0].id");
  }

  private ResultActions save(Cookie session, String senseId) throws Exception {
    return mvc.perform(
        post("/api/cards")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(senseBody(senseId)));
  }

  private static String senseBody(String senseId) {
    return "{\"sense_id\":\"%s\"}".formatted(senseId);
  }
}
