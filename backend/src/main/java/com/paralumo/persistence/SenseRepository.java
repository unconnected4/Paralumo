package com.paralumo.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenseRepository extends JpaRepository<SenseEntity, Long> {

  @EntityGraph(attributePaths = "lexeme")
  Optional<SenseEntity> findBySourceKey(String sourceKey);
}
