package com.paralumo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paralumo.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps {@code docs/api/openapi.json} equal to what springdoc generates from the controllers.
 * {@code ./gradlew exportOpenApi} runs this test with {@code -Dopenapi.export=true} to rewrite the
 * file; otherwise ({@code check}, CI) it fails on any difference, which is the contract drift
 * check.
 */
@IntegrationTest
class OpenApiContractTest {

  private static final JsonMapper PRETTY =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  @Autowired MockMvc mvc;

  @Test
  void committedSpecMatchesTheControllers() throws Exception {
    String generated =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    // The pretty printer ends lines with the platform separator; the file always uses LF.
    String formatted =
        PRETTY.writeValueAsString(PRETTY.readValue(generated, JsonNode.class)).replace("\r\n", "\n")
            + "\n";
    Path file = Path.of(System.getProperty("openapi.file", "../docs/api/openapi.json"));

    if (Boolean.getBoolean("openapi.export")) {
      Files.writeString(file, formatted, StandardCharsets.UTF_8);
      return;
    }
    assertThat(file).as("run ./gradlew exportOpenApi").exists();
    // Git may check the file out with CRLF line endings (core.autocrlf on Windows).
    assertThat(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"))
        .as("docs/api/openapi.json is out of date: run ./gradlew exportOpenApi")
        .isEqualTo(formatted);
  }
}
