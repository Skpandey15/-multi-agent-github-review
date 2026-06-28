package com.multiagent.review.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TriggerRequest {

    @NotBlank(message = "repoUrl is required")
    @Pattern(
        regexp = "https://github\\.com/[\\w.-]+/[\\w.-]+",
        message = "repoUrl must be a valid GitHub repository URL"
    )
    private String repoUrl;

    @NotBlank(message = "branch is required")
    private String branch;
}
