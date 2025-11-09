package io.aggregator.repository;

import io.aggregator.entity.Developer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeveloperRepository extends JpaRepository<Developer, UUID> {

    Optional<Developer> findByEmail(String email);

    boolean existsByEmail(String email);
}