package com.paralumo.api;

import com.paralumo.api.Dtos.CardDto;
import com.paralumo.api.Dtos.ExampleSentenceDto;
import com.paralumo.api.Dtos.SaveCardRequest;
import com.paralumo.config.CurrentUser;
import com.paralumo.persistence.CardEntity;
import com.paralumo.service.CardService;
import com.paralumo.service.CardService.SaveResult;
import com.paralumo.service.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's saved cards. Every route needs a session (401 otherwise). */
@RestController
@RequestMapping(path = "/api/cards", produces = MediaType.APPLICATION_JSON_VALUE)
class CardController {

  private final CardService cardService;

  CardController(CardService cardService) {
    this.cardService = cardService;
  }

  @Operation(operationId = "listCards", summary = "List saved cards, newest first")
  @ApiResponse(responseCode = "200", description = "The user's cards")
  @ApiResponse(responseCode = "401", description = "Not signed in")
  @GetMapping
  List<CardDto> list(@AuthenticationPrincipal CurrentUser user) {
    return cardService.list(user.id()).stream().map(CardController::toDto).toList();
  }

  @Operation(
      operationId = "saveCard",
      summary = "Save a sense as a card",
      description =
          "201 with the new card, or 200 with the existing card if the sense is already saved."
              + " Saving a deleted card's sense restores it.")
  @ApiResponse(responseCode = "201", description = "Saved")
  @ApiResponse(responseCode = "200", description = "Already saved")
  @ApiResponse(responseCode = "401", description = "Not signed in")
  @ApiResponse(responseCode = "404", description = "Unknown sense")
  @ApiResponse(responseCode = "422", description = "Missing sense_id")
  @PostMapping
  ResponseEntity<CardDto> save(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SaveCardRequest request) {
    String senseId = Objects.requireNonNull(request.senseId(), "validated as @NotBlank");
    SaveResult result = cardService.save(user.id(), senseId);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(toDto(result.card()));
  }

  @Operation(operationId = "deleteCard", summary = "Delete a card")
  @ApiResponse(responseCode = "204", description = "Deleted")
  @ApiResponse(responseCode = "401", description = "Not signed in")
  @ApiResponse(responseCode = "404", description = "Unknown card")
  @DeleteMapping("/{cardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable String cardId) {
    cardService.delete(user.id(), parseCardId(cardId));
  }

  // Card ids are opaque to clients: a malformed id is just an unknown card, not a bad request.
  private static UUID parseCardId(String cardId) {
    try {
      return UUID.fromString(cardId);
    } catch (IllegalArgumentException e) {
      throw new NotFoundException("Card not found");
    }
  }

  private static CardDto toDto(CardEntity card) {
    return new CardDto(
        card.getId().toString(),
        card.getSenseKey(),
        card.getLemma(),
        card.getPos(),
        card.getTranscription(),
        card.getTranslationRu(),
        card.getSynonymsRu(),
        card.getMeaningsEn(),
        card.getExamples().stream().map(e -> new ExampleSentenceDto(e.en(), e.ru())).toList(),
        card.getCreatedAt(),
        card.getNotes());
  }
}
