package com.multiagent.review.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TriggerResponse {
    private UUID runId;
    private String workflowId;
    private String message;
    private String statusUrl;
}
