package com.multiagent.review.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

@ActivityInterface
public interface NotificationActivities {

    @ActivityMethod
    void sendAnalysisCompleteEmail(UUID runId, String repoUrl, int issuesFound);

    @ActivityMethod
    void sendPrRaisedEmail(UUID runId, String repoUrl, String prUrl,
                           int issuesFixed, int issuesSkipped);

    @ActivityMethod
    void updateRunStatus(UUID runId, String status);

    @ActivityMethod
    void updateRunWithPrDetails(UUID runId, String prUrl, String prBranch,
                                int totalFixed, int totalFalsePositives);

    @ActivityMethod
    void saveFinding(UUID runId, String filePath, int lineNumber, String description,
                     String issueType, String severity, int confidence,
                     String status, String originalCode, String fixedCode,
                     String commitMessage, String criticReasoning);
}
