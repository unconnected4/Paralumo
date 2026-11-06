package com.paralumo.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Raw access to {@code user_cards}, soft-deleted rows included. Only {@code CardService} uses it,
 * so the active-card filter ({@code deleted_at IS NULL}) is applied in one place.
 */
public interface CardRepository extends JpaRepository<CardEntity, UUID> {

  List<CardEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

  Optional<CardEntity> findByUserIdAndSenseId(UUID userId, Long senseId);

  Optional<CardEntity> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  List<CardEntity> findByUserIdAndSenseKeyInAndDeletedAtIsNull(
      UUID userId, Collection<String> senseKeys);
}
