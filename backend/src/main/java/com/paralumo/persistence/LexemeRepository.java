package com.paralumo.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LexemeRepository extends JpaRepository<LexemeEntity, Long> {

  Optional<LexemeEntity> findByLemmaAndPos(String lemma, String pos);
}
