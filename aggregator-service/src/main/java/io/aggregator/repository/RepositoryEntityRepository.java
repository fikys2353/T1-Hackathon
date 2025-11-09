package io.aggregator.repository;

import io.aggregator.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, UUID> {

    @EntityGraph(attributePaths = {"project"})
    List<RepositoryEntity> findAll();

    @EntityGraph(attributePaths = {"project"})
    List<RepositoryEntity> findByProject_Id(UUID projectId);

    @EntityGraph(attributePaths = {"project"})
    Optional<RepositoryEntity> findByProject_IdAndName(UUID projectId, String name);
}