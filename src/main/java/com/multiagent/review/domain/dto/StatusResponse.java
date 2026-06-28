package com.multiagent.review.domain.dto;

import com.multiagent.review.domain.enums.RunStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class StatusResponse {
    private UUID runId;
    private String repoUrl;
    private String branch;
    private RunStatus status;
    private String prUrl;
    private Integer totalIssuesFound;
    private Integer totalIssuesFixed;
    private Integer totalFalsePositives;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
