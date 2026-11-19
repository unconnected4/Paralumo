package com.paralumo.api;

import com.paralumo.api.Dtos.ExampleSentenceDto;
import com.paralumo.api.Dtos.LexemeDto;
import com.paralumo.api.Dtos.LookupResponse;
import com.paralumo.api.Dtos.SenseDto;
import com.paralumo.provider.LanguagePair;
import com.paralumo.provider.ProviderSense;
import com.paralumo.service.LookupService;
import com.paralumo.service.LookupService.LookupResult;
import com.paralumo.service.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dictionary lookups. The response doesn't depend on the user, so a reverse proxy may cache it; the
 * headers set here replace Spring Security's default {@code no-store}.
 */
@RestController
@RequestMapping(path = "/api/dictionary", produces = MediaType.APPLICATION_JSON_VALUE)
class DictionaryController {

  // Proposals within the Yandex terms, which allow only temporary caching (root intent.md).
  private static final CacheControl FOUND = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
  private static final CacheControl NOT_FOUND =
      CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

  private final LookupService lookupService;

  DictionaryController(LookupService lookupService) {
    this.lookupService = lookupService;
  }

  @Operation(
      operationId = "lookup",
      summary = "Look up an English word",
      description =
          "An empty query returns no lexemes. The response is the same for every user and is"
              + " publicly cacheable; GET /api/cards/saved tells which senses the user saved.")
  @ApiResponse(responseCode = "200", description = "Entries grouped by part of speech")
  @ApiResponse(responseCode = "404", description = "Unknown word")
  @GetMapping("/lookup")
  ResponseEntity<LookupResponse> lookup(@RequestParam(defaultValue = "") String q) {
    LookupResult result = lookupService.lookup(q);
    LanguagePair pair = result.pair();
    LookupResponse body =
        new LookupResponse(
            result.query(),
            pair.source(),
            pair.target(),
            result.lexemes().stream()
                .map(
                    lexeme ->
                        new LexemeDto(
                            lexeme.lemma(),
                            pair.source(),
                            lexeme.pos(),
                            lexeme.transcription(),
                            lexeme.senses().stream().map(sense -> toDto(sense, pair)).toList()))
                .toList());
    return ResponseEntity.ok().cacheControl(FOUND).body(body);
  }

  /**
   * An unknown word is cacheable too. Handled here, not in {@link ApiExceptionHandler}, because the
   * card endpoints' 404s must stay uncached.
   */
  @ExceptionHandler
  ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .cacheControl(NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
  }

  private static SenseDto toDto(ProviderSense sense, LanguagePair pair) {
    return new SenseDto(
        sense.id(),
        pair.target(),
        sense.translation(),
        sense.synonyms(),
        sense.meanings(),
        sense.examples().stream()
            .map(e -> new ExampleSentenceDto(e.text(), e.translation()))
            .toList());
  }
}
