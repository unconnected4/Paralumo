package com.paralumo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class HealthApiTest {

  @Autowired MockMvc mvc;

  @Test
  void upAndReady() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
    mvc.perform(get("/api/health/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void unknownRoutesAreProblem404s() throws Exception {
    mvc.perform(get("/api/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").isString());
  }
}
