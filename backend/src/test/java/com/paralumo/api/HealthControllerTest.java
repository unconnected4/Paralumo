package com.paralumo.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

  @Test
  void readyReturns503ProblemWithoutDatabaseInternalsWhenDatabaseIsUnreachable() throws Exception {
    JdbcTemplate unreachable =
        new JdbcTemplate() {
          @Override
          public <T> @Nullable T queryForObject(String sql, Class<T> requiredType)
              throws DataAccessException {
            throw new CannotGetJdbcConnectionException("FATAL: password authentication failed");
          }
        };
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new HealthController(unreachable))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    mvc.perform(get("/api/health/ready"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.detail").value("Database unreachable"))
        .andExpect(content().string(not(containsString("password authentication failed"))));
  }
}
