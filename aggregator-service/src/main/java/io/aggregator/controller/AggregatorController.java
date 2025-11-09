package io.aggregator.controller;

import io.aggregator.dto.ProjectDTO;
import io.aggregator.dto.RepositoryDTO;
import io.aggregator.dto.DeveloperDTO;
import io.aggregator.service.AggregatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AggregatorController {

    private final AggregatorService aggregatorService;

    // -----------------------------
    // ПРОЕКТЫ
    // -----------------------------

    /** Получить список всех проектов */
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectDTO>> getProjects() {
        List<ProjectDTO> projects = aggregatorService.getAllProjects();
        if (projects.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 No Content
        }
        return ResponseEntity.ok(projects); // 200 OK
    }

    /** Получить конкретный проект по имени (вместе с репозиториями) */
    @GetMapping("/projects/{projectName}")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable String projectName) {
        try {
            ProjectDTO project = aggregatorService.getProjectByName(projectName);
            return ResponseEntity.ok(project);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }

    // -----------------------------
    // РЕПОЗИТОРИИ ПРОЕКТА
    // -----------------------------

    /** Получить все репозитории проекта */
    @GetMapping("/projects/{projectName}/repos")
    public ResponseEntity<List<RepositoryDTO>> getRepositoriesByProject(@PathVariable String projectName) {
        try {
            ProjectDTO project = aggregatorService.getProjectByName(projectName);
            List<RepositoryDTO> repos = project.getRepositories();
            if (repos.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(repos);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // -----------------------------
    // РАЗРАБОТЧИКИ В РАМКАХ РЕПОЗИТОРИЯ
    // -----------------------------

    /** Получить всех разработчиков, работающих над конкретным репозиторием */
    @GetMapping("/projects/{projectName}/repos/{repoName}/developers")
    public ResponseEntity<List<DeveloperDTO>> getDevelopersByRepository(
            @PathVariable String projectName,
            @PathVariable String repoName
    ) {
        try {
            List<DeveloperDTO> developers = aggregatorService.getDevelopersByRepository(projectName, repoName);
            if (developers.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(developers);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Получить статистику конкретного разработчика в рамках репозитория */
    @GetMapping("/projects/{projectName}/repos/{repoName}/developers/{developerEmail}")
    public ResponseEntity<DeveloperDTO> getDeveloperStatsInRepository(
            @PathVariable String projectName,
            @PathVariable String repoName,
            @PathVariable String developerEmail
    ) {
        try {
            DeveloperDTO developer = aggregatorService.getDeveloperStatsInRepository(projectName, repoName, developerEmail);
            return ResponseEntity.ok(developer);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}