package com.multiagent.review.temporal.activity;

import com.multiagent.review.agent.model.*;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface LLMActivities {

    @ActivityMethod
    AnalysisResult analyseFile(String filePath, String language, String code);

    @ActivityMethod
    CriticVerdict verifyFinding(String filePath, int lineNumber, String issueType,
                                String severity, int confidence, String description,
                                String codeSnippet, String language, String fullCode);

    @ActivityMethod
    FixResult generateFix(String filePath, int lineNumber, String issueType,
                          String severity, String description, String codeSnippet,
                          String criticReasoning, String language, String fullCode);
}
