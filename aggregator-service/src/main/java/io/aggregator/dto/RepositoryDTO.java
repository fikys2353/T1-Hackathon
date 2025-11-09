package io.aggregator.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryDTO {
    private UUID id;
    private String name;
    private String description;
    private int activeBranches;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}