package io.aggregator.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitDTO {
    private UUID id;
    private String hash;
    private String message;
    private LocalDateTime createdAt;
    private String branchName;
    private int linesAdded;
    private int linesDeleted;

    private UUID developerId;
    private UUID projectId;
}