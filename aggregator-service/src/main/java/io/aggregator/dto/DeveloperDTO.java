package io.aggregator.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeveloperDTO {
    private UUID id;
    private String name;
    private String email;

    private int totalCommits;
    private int linesAdded;
    private int linesDeleted;
    private double commitFrequency; // коммитов в день
    private LocalDateTime firstCommitAt;
    private LocalDateTime lastCommitAt;
    private int smallCommits;
    private int largeCommits;
    private double kpi;
}