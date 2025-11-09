package io.aggregator.repository;

import io.aggregator.entity.Commit;
import io.aggregator.entity.Developer;
import io.aggregator.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitRepository extends JpaRepository<Commit, UUID> {

    @Query("SELECT DISTINCT c.developer FROM Commit c WHERE c.repository = :repository")
    List<Developer> findDistinctAuthorsByRepository(@Param("repository") RepositoryEntity repository);

    @Query("SELECT MAX(c.createdAt) FROM Commit c WHERE c.developer = :developer AND c.repository = :repository")
    Optional<LocalDateTime> findLastCommitDateByAuthorAndRepository(
            @Param("developer") Developer developer,
            @Param("repository") RepositoryEntity repository
    );

    @Query("""
SELECT COUNT(c), 
       COALESCE(SUM(c.linesAdded),0), 
       COALESCE(SUM(c.linesDeleted),0),
       MIN(c.createdAt), 
       MAX(c.createdAt)
FROM Commit c
WHERE c.developer.id = :developerId 
  AND c.repository.id = :repositoryId
""")
    Object[] aggregateMetricsByDeveloperAndRepository(@Param("developerId") UUID developerId,
                                                      @Param("repositoryId") UUID repositoryId);

    @Query(value = """
    SELECT
        SUM(CASE WHEN (lines_added + lines_deleted) <= :smallThreshold THEN 1 ELSE 0 END) AS smallCommits,
        SUM(CASE WHEN (lines_added + lines_deleted) >= :largeThreshold THEN 1 ELSE 0 END) AS largeCommits
    FROM commits
    WHERE developer_id = :developerId
      AND repository_id = :repositoryId
    """, nativeQuery = true)
    Object[] countSmallAndLargeCommits(
            @Param("developerId") UUID developerId,
            @Param("repositoryId") UUID repositoryId,
            @Param("smallThreshold") int smallThreshold,
            @Param("largeThreshold") int largeThreshold
    );

    @Query(value = """
        SELECT 
            COUNT(*) AS maxCommits,
            COALESCE(MAX(lines_added),0) AS maxLinesAdded,
            COALESCE(MAX(lines_deleted),0) AS maxLinesDeleted,
            SUM(CASE WHEN (lines_added + lines_deleted) <= :smallThreshold THEN 1 ELSE 0 END) AS maxSmallCommits,
            SUM(CASE WHEN (lines_added + lines_deleted) >= :largeThreshold THEN 1 ELSE 0 END) AS maxLargeCommits,
            COALESCE(EXTRACT(EPOCH FROM MAX(created_at) - MIN(created_at)) / 86400, 0) AS maxCommitFreq
        FROM commits
        WHERE repository_id = :repositoryId
        """, nativeQuery = true)
    Object[] findMaxMetricsByRepository(
            @Param("repositoryId") java.util.UUID repositoryId,
            @Param("smallThreshold") int smallThreshold,
            @Param("largeThreshold") int largeThreshold
    );
}