package com.paralumo.service;

import com.paralumo.persistence.CardEntity;
import com.paralumo.persistence.CardRepository;
import com.paralumo.persistence.SenseEntity;
import com.paralumo.persistence.SenseRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A user's saved cards. This is the only place that reads {@code user_cards}, so the active-card
 * filter ({@code deleted_at IS NULL}) lives here and nowhere else.
 */
@Service
public class CardService {

  private final CardRepository cards;
  private final SenseRepository senses;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public CardService(
      CardRepository cards,
      SenseRepository senses,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.cards = cards;
    this.senses = senses;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /** The outcome of saving a sense: the card, and whether it was newly saved. */
  public record SaveResult(CardEntity card, boolean created) {}

  @Transactional(readOnly = true)
  public List<CardEntity> list(UUID userId) {
    return cards.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
  }

  /**
   * Saves a sense as a card. Saving an already saved sense returns the existing card; saving a
   * deleted one restores it with its data, notes and history intact.
   */
  public SaveResult save(UUID userId, String senseId) {
    try {
      return Objects.requireNonNull(transaction.execute(status -> saveOnce(userId, senseId)));
    } catch (DataIntegrityViolationException e) {
      // A concurrent request saved the same sense between the check and the insert, and the
      // unique constraint on (user_id, sense_id) stopped this one. The card exists now.
      return Objects.requireNonNull(transaction.execute(status -> saveOnce(userId, senseId)));
    }
  }

  private SaveResult saveOnce(UUID userId, String senseId) {
    SenseEntity sense =
        senses.findBySourceKey(senseId).orElseThrow(() -> new NotFoundException("Sense not found"));
    long senseRowId = Objects.requireNonNull(sense.getId(), "persisted sense has an id");
    return cards
        .findByUserIdAndSenseId(userId, senseRowId)
        .map(
            existing -> {
              if (!existing.isDeleted()) {
                return new SaveResult(existing, false);
              }
              existing.restore();
              return new SaveResult(existing, true);
            })
        .orElseGet(
            () ->
                new SaveResult(
                    cards.save(
                        new CardEntity(
                            UUID.randomUUID(), userId, senseRowId, sense, clock.instant())),
                    true));
  }

  /** Soft-deletes a card, keeping its row for review history. */
  @Transactional
  public void delete(UUID userId, UUID cardId) {
    CardEntity card =
        cards
            .findByIdAndUserIdAndDeletedAtIsNull(cardId, userId)
            .orElseThrow(() -> new NotFoundException("Card not found"));
    card.markDeleted(clock.instant());
  }

  /** Which of the given public sense ids the user has an active card for. */
  @Transactional(readOnly = true)
  public Set<String> savedSenseIds(UUID userId, Collection<String> senseIds) {
    if (senseIds.isEmpty()) {
      return Set.of();
    }
    return cards.findByUserIdAndSenseKeyInAndDeletedAtIsNull(userId, senseIds).stream()
        .map(CardEntity::getSenseKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
