package io.aggregator.service;

import io.aggregator.dto.DeveloperDTO;
import io.aggregator.dto.ProjectDTO;
import io.aggregator.dto.RepositoryDTO;
import io.aggregator.entity.Developer;
import io.aggregator.entity.Project;
import io.aggregator.entity.RepositoryEntity;
import io.aggregator.repository.CommitRepository;
import io.aggregator.repository.DeveloperRepository;
import io.aggregator.repository.ProjectRepository;
import io.aggregator.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AggregatorService {

    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;
    private final CommitRepository commitRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DeveloperRepository developerRepository;

    // -----------------------------
    // Все проекты (без репозиториев)
    // -----------------------------
    @Transactional(readOnly = true)
    public List<ProjectDTO> getAllProjects() {
        List<Project> projects = projectRepository.findAll();
        return projects.stream()
                .map(this::toProjectDTO)
                .collect(Collectors.toList());
    }

    // -----------------------------
    // Конкретный проект с репозиториями
    // -----------------------------
    @Transactional(readOnly = true)
    public ProjectDTO getProjectByName(String projectName) {
        Project project = projectRepository.findByName(projectName)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

        List<RepositoryEntity> repos = repositoryEntityRepository.findByProject_Id(project.getId());

        return toProjectDTOWithRepos(project, repos);
    }

    @Transactional(readOnly = true)
    public List<DeveloperDTO> getDevelopersByRepository(String projectName, String repoName) {
        // 1. Получаем проект по имени
        Project project = projectRepository.findByName(projectName)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

        // 2. Получаем репозиторий по имени и projectId
        RepositoryEntity repository = repositoryEntityRepository.findByProject_IdAndName(project.getId(), repoName)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoName));

        // 3. Получаем всех разработчиков через коммиты этого репозитория
        List<Developer> developers = commitRepository.findDistinctAuthorsByRepository(repository);

        // 4. Преобразуем в DTO и добавляем дату последнего коммита
        return developers.stream()
                .map(dev -> toDeveloperDTOWithLastCommit(dev, repository))
                .collect(Collectors.toList());
    }

    private DeveloperDTO toDeveloperDTOWithLastCommit(Developer developer, RepositoryEntity repository) {
        LocalDateTime lastCommitAt = commitRepository.findLastCommitDateByAuthorAndRepository(developer, repository)
                .orElse(null);

        return DeveloperDTO.builder()
                .id(developer.getId())
                .name(developer.getName())
                .email(developer.getEmail())
                .lastCommitAt(lastCommitAt)
                .build();
    }

    @Transactional(readOnly = true)
    public DeveloperDTO getDeveloperStatsInRepository(String projectName, String repoName, String developerEmail) {


        // 1. Получаем проект
        Map<String, Object> projectRow = getProjectRow(projectName);
        UUID projectId = (UUID) projectRow.get("id");

        // 2. Получаем репозиторий
        Map<String, Object> repoRow = getRepositoryRow(repoName, projectId);
        UUID repositoryId = (UUID) repoRow.get("id");

        // 3. Получаем разработчика
        Map<String, Object> devRow = getDeveloperRow(developerEmail);
        UUID developerId = (UUID) devRow.get("id");

        // 4. Получаем метрики разработчика в репозитории
        Map<String, Object> metricsRow = getDeveloperMetrics(developerId, repositoryId);
        int totalCommits = ((Number) metricsRow.get("totalCommits")).intValue();
        int linesAdded = ((Number) metricsRow.get("linesAdded")).intValue();
        int linesDeleted = ((Number) metricsRow.get("linesDeleted")).intValue();
        LocalDateTime firstCommit = ((Timestamp) metricsRow.get("firstCommit")).toLocalDateTime();
        LocalDateTime lastCommit = ((Timestamp) metricsRow.get("lastCommit")).toLocalDateTime();

        // 5. Подсчет мелких и больших коммитов
        Map<String, Object> sizeRow = getDeveloperCommitSizes(developerId, repositoryId);
        int smallCommits = ((Number) sizeRow.get("smallCommits")).intValue();
        int largeCommits = ((Number) sizeRow.get("largeCommits")).intValue();

        // 6. Частота коммитов
        double commitFrequency = 0;
        if (firstCommit != null && lastCommit != null) {
            commitFrequency = totalCommits / (double) Math.max(Duration.between(firstCommit, lastCommit).toDays(), 1);
        }

        // 7. Максимумы по репозиторию
        Map<String, Object> maxRow = getRepositoryMaxMetrics(repositoryId);
        int maxCommits = ((Number) maxRow.get("maxCommits")).intValue();
        int maxLinesAdded = ((Number) maxRow.get("maxLinesAdded")).intValue();
        int maxLinesDeleted = ((Number) maxRow.get("maxLinesDeleted")).intValue();
        int maxSmallCommits = ((Number) maxRow.get("maxSmallCommits")).intValue();
        int maxLargeCommits = ((Number) maxRow.get("maxLargeCommits")).intValue();
        double maxCommitFreq = ((Number) maxRow.get("maxCommitFreq")).doubleValue();

        // 8. Рассчитываем KPI
        int normalCommits = totalCommits - smallCommits - largeCommits;
        double kpi = calculateKpi(normalCommits, linesAdded, linesDeleted,
                smallCommits, largeCommits, commitFrequency,
                maxCommits, maxLinesAdded, maxLinesDeleted,
                maxSmallCommits, maxLargeCommits, maxCommitFreq);

        // 9. Собираем DTO
        return DeveloperDTO.builder()
                .id(developerId)
                .name((String) devRow.get("name"))
                .email((String) devRow.get("email"))
                .totalCommits(totalCommits)
                .linesAdded(linesAdded)
                .linesDeleted(linesDeleted)
                .commitFrequency(commitFrequency)
                .lastCommitAt(lastCommit)
                .kpi(kpi)
                .smallCommits(smallCommits)
                .largeCommits(largeCommits)
                .build();
    }

    private Map<String, Object> getProjectRow(String projectName) {
        String projectQuery = "SELECT id, name FROM projects WHERE name = ?";
        return jdbcTemplate.queryForMap(projectQuery, projectName);
    }

    private Map<String, Object> getRepositoryRow(String repoName, UUID projectId) {
        String repoQuery = "SELECT id, name FROM repositories WHERE name = ? AND project_id = ?";
        return jdbcTemplate.queryForMap(repoQuery, repoName, projectId);
    }

    private Map<String, Object> getDeveloperRow(String developerEmail) {
        String devQuery = "SELECT id, name, email FROM developers WHERE email = ?";
        return jdbcTemplate.queryForMap(devQuery, developerEmail);
    }

    private Map<String, Object> getDeveloperMetrics(UUID developerId, UUID repositoryId) {
        String metricsSql = """
            SELECT 
                COUNT(*) AS totalCommits,
                COALESCE(SUM(lines_added),0) AS linesAdded,
                COALESCE(SUM(lines_deleted),0) AS linesDeleted,
                MIN(created_at) AS firstCommit,
                MAX(created_at) AS lastCommit
            FROM commits
            WHERE developer_id = ? AND repository_id = ?
        """;
        return jdbcTemplate.queryForMap(metricsSql, developerId, repositoryId);
    }

    private Map<String, Object> getDeveloperCommitSizes(UUID developerId, UUID repositoryId) {
        String sizeSql = """
            SELECT
                SUM(CASE WHEN (lines_added + lines_deleted) <= 5 THEN 1 ELSE 0 END) AS smallCommits,
                SUM(CASE WHEN (lines_added + lines_deleted) >= 50 THEN 1 ELSE 0 END) AS largeCommits
            FROM commits
            WHERE developer_id = ? AND repository_id = ?
        """;
        return jdbcTemplate.queryForMap(sizeSql, developerId, repositoryId);
    }

    private Map<String, Object> getRepositoryMaxMetrics(UUID repositoryId) {
        String maxSql = """
            SELECT 
                COUNT(*) AS maxCommits,
                COALESCE(MAX(lines_added),0) AS maxLinesAdded,
                COALESCE(MAX(lines_deleted),0) AS maxLinesDeleted,
                SUM(CASE WHEN (lines_added + lines_deleted) <= 5 THEN 1 ELSE 0 END) AS maxSmallCommits,
                SUM(CASE WHEN (lines_added + lines_deleted) >= 50 THEN 1 ELSE 0 END) AS maxLargeCommits,
                COALESCE(EXTRACT(EPOCH FROM MAX(created_at) - MIN(created_at)) / 86400, 0) AS maxCommitFreq
            FROM commits
            WHERE repository_id = ?
        """;
        return jdbcTemplate.queryForMap(maxSql, repositoryId);
    }

    private double calculateKpi(
            int normalCommits,
            int linesAdded,
            int linesDeleted,
            int smallCommits,
            int largeCommits,
            double commitFrequency,
            int maxNormalCommitsInRepo,
            int maxLinesAddedInRepo,
            int maxLinesDeletedInRepo,
            int maxSmallCommitsInRepo,
            int maxLargeCommitsInRepo,
            double maxCommitFrequencyInRepo
    ) {
        // нормализация каждой метрики по максимуму в репозитории
        double normalizedNormalCommits = Math.min(1.0, (double) normalCommits / Math.max(maxNormalCommitsInRepo, 1));
        double normalizedLinesAdded = Math.min(1.0, (double) linesAdded / Math.max(maxLinesAddedInRepo, 1));
        double normalizedLinesDeleted = Math.min(1.0, (double) linesDeleted / Math.max(maxLinesDeletedInRepo, 1));
        double normalizedSmallCommits = Math.min(1.0, (double) smallCommits / Math.max(maxSmallCommitsInRepo, 1));
        double largeCommitsEffect = Math.min(1.0, (double) largeCommits / Math.max(maxLargeCommitsInRepo, 1));
        double normalizedCommitFreq = Math.min(1.0, commitFrequency / Math.max(maxCommitFrequencyInRepo, 1));

        // формула KPI с весами, результат уже от 0 до 1
        return 0.3 * normalizedNormalCommits
                + 0.25 * normalizedLinesAdded
                + 0.25 * normalizedLinesDeleted
                + 0.1  * (1.0 - normalizedSmallCommits)
                + 0.05 * largeCommitsEffect
                + 0.05 * normalizedCommitFreq;
    }

    // -----------------------------
    // Маппинг Project → ProjectDTO без репозиториев
    // -----------------------------
    private ProjectDTO toProjectDTO(Project project) {
        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .fullName(project.getFullName())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    // -----------------------------
    // Маппинг Project → ProjectDTO с репозиториями
    // -----------------------------
    private ProjectDTO toProjectDTOWithRepos(Project project, List<RepositoryEntity> repos) {
        List<RepositoryDTO> repoDTOs = repos.stream()
                .map(this::toRepositoryDTO)
                .collect(Collectors.toList());

        return ProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .fullName(project.getFullName())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .repositories(repoDTOs)
                .build();
    }

    // -----------------------------
    // Маппинг Repository → RepositoryDTO
    // -----------------------------
    private RepositoryDTO toRepositoryDTO(RepositoryEntity repo) {
        return RepositoryDTO.builder()
                .id(repo.getId())
                .name(repo.getName())
                .description(repo.getDescription())
                .activeBranches(repo.getActiveBranches())
                .createdAt(repo.getCreatedAt())
                .updatedAt(repo.getUpdatedAt())
                .build();
    }

    // -----------------------------
    // Маппинг Developer → DeveloperDTO
    // -----------------------------
    private DeveloperDTO toDeveloperDTO(Developer developer) {
        return DeveloperDTO.builder()
                .id(developer.getId())
                .name(developer.getName())
                .email(developer.getEmail())
                .build();
    }
}