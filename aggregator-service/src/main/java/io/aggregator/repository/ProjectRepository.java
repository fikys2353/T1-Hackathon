package io.aggregator.repository;

import io.aggregator.entity.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @EntityGraph(attributePaths = {"repositories"})
    Optional<Project> findByName(String name);

    boolean existsByName(String name);
}