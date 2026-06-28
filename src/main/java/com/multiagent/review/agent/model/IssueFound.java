package com.multiagent.review.agent.model;

import com.multiagent.review.domain.enums.Severity;
import lombok.Data;

@Data
public class IssueFound {
    private String filePath;
    private int lineNumber;
    private String description;
    private String issueType;
    private Severity severity;
    private int confidence;
    private String codeSnippet;
}
