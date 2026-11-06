package com.paralumo.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Health checks for the host (docs/engineering.md, Observability). */
@RestController
@RequestMapping(path = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
class HealthController {

  private final JdbcTemplate jdbc;

  HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Operation(operationId = "health", summary = "The process is up")
  @GetMapping
  Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @Operation(operationId = "ready", summary = "The database is reachable")
  @ApiResponse(responseCode = "200", description = "Ready")
  @ApiResponse(responseCode = "503", description = "Database unreachable")
  @GetMapping("/ready")
  Map<String, String> ready() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
    } catch (DataAccessException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database unreachable", e);
    }
    return Map.of("status", "ok");
  }
}
