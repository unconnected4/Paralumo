package com.paralumo.api;

import com.paralumo.api.Dtos.ExampleSentenceDto;
import com.paralumo.api.Dtos.LexemeDto;
import com.paralumo.api.Dtos.LookupResponse;
import com.paralumo.api.Dtos.SenseDto;
import com.paralumo.config.CurrentUser;
import com.paralumo.service.LookupService;
import com.paralumo.service.LookupService.LookupResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/dictionary", produces = MediaType.APPLICATION_JSON_VALUE)
class DictionaryController {

  private final LookupService lookupService;

  DictionaryController(LookupService lookupService) {
    this.lookupService = lookupService;
  }

  @Operation(
      operationId = "lookup",
      summary = "Look up an English word",
      description = "An empty query returns no lexemes. `saved` reflects the signed-in user.")
  @ApiResponse(responseCode = "200", description = "Entries grouped by part of speech")
  @ApiResponse(responseCode = "404", description = "Unknown word")
  @GetMapping("/lookup")
  LookupResponse lookup(
      @RequestParam(defaultValue = "") String q,
      @AuthenticationPrincipal @Nullable CurrentUser user) {
    LookupResult result = lookupService.lookup(q, user == null ? null : user.id());
    return new LookupResponse(
        result.query(),
        result.lexemes().stream()
            .map(
                lexeme ->
                    new LexemeDto(
                        lexeme.lemma(),
                        lexeme.pos(),
                        lexeme.transcription(),
                        lexeme.senses().stream()
                            .map(
                                sense ->
                                    new SenseDto(
                                        sense.id(),
                                        sense.translationRu(),
                                        sense.synonymsRu(),
                                        sense.meaningsEn(),
                                        sense.examples().stream()
                                            .map(e -> new ExampleSentenceDto(e.en(), e.ru()))
                                            .toList(),
                                        result.savedSenseIds().contains(sense.id())))
                            .toList()))
            .toList());
  }
}
