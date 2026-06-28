package com.multiagent.review.agent.model;

import lombok.Data;

@Data
public class FixResult {
    private String filePath;
    private String originalCode;
    private String fixedCode;
    private String commitMessage;
    private boolean fixApplied;
    private String skipReason;
}
