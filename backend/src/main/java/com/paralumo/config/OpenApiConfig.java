package com.paralumo.config;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Objects;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shapes the generated spec ({@code docs/api/openapi.json}) to match what goes over the wire. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  private static final String PROBLEM = "Problem";

  /**
   * springdoc builds schemas with swagger-core's own Jackson 2 mapper, which doesn't see the
   * application's SNAKE_CASE naming strategy (spring.jackson.property-naming-strategy). Give it the
   * same strategy so the spec names fields as the API sends them.
   */
  @Bean
  ModelResolver snakeCaseModelResolver() {
    return new ModelResolver(
        Json.mapper().copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
  }

  /** Every error response is an RFC 9457 problem body whose {@code detail} clients display. */
  @Bean
  OpenApiCustomizer problemResponses() {
    return openApi -> {
      openApi.info(new Info().title("Paralumo API").version("1"));
      // Clients call the API on their own origin (the web app through the Vite proxy).
      openApi.servers(List.of(new Server().url("/")));
      Components components =
          Objects.requireNonNullElseGet(openApi.getComponents(), Components::new);
      openApi.components(components.addSchemas(PROBLEM, problemSchema()));
      Content problemContent =
          new Content()
              .addMediaType(
                  APPLICATION_PROBLEM_JSON_VALUE,
                  new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)));
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation ->
                              operation
                                  .getResponses()
                                  .forEach(
                                      (code, response) -> {
                                        if (code.startsWith("4") || code.startsWith("5")) {
                                          response.setContent(problemContent);
                                        }
                                      })));
    };
  }

  private static Schema<?> problemSchema() {
    ObjectSchema problem = new ObjectSchema();
    problem.setDescription("RFC 9457 problem details");
    problem.addProperty("type", new StringSchema());
    problem.addProperty("title", new StringSchema());
    problem.addProperty("status", new IntegerSchema());
    problem.addProperty("detail", new StringSchema().description("Human-readable message"));
    problem.addProperty("instance", new StringSchema());
    problem.setRequired(List.of("detail"));
    return problem;
  }
}
