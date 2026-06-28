package com.multiagent.review.agent.model;

import lombok.Data;

import java.util.List;

@Data
public class AnalysisResult {
    private List<IssueFound> issues;
    private String summary;
}
