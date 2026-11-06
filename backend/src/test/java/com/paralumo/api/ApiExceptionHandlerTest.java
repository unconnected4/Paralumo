package com.paralumo.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

  @RestController
  static class FailingController {
    @GetMapping("/fail")
    String fail() {
      throw new IllegalStateException("secret internals");
    }
  }

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new FailingController())
          .setControllerAdvice(new ApiExceptionHandler())
          .build();

  @Test
  void unexpectedErrorsAreProblem500sWithoutInternals() throws Exception {
    mvc.perform(get("/fail"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again."))
        .andExpect(content().string(not(containsString("secret"))));
  }
}
