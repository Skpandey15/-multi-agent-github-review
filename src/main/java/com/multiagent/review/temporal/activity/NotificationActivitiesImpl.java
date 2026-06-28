package com.multiagent.review.temporal.activity;

import com.multiagent.review.domain.enums.FindingStatus;
import com.multiagent.review.domain.enums.RunStatus;
import com.multiagent.review.domain.enums.Severity;
import com.multiagent.review.service.EmailService;
import com.multiagent.review.service.PipelineService;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ActivityImpl(taskQueues = CodeReviewWorkflow.TASK_QUEUE)
public class NotificationActivitiesImpl implements NotificationActivities {

    private final PipelineService pipelineService;
    private final EmailService emailService;

    @Override
    public void sendAnalysisCompleteEmail(UUID runId, String repoUrl, int issuesFound) {
        log.info("Sending analysis-complete email. runId={} issuesFound={}", runId, issuesFound);
        try {
            emailService.sendAnalysisComplete(runId, repoUrl, issuesFound);
        } catch (Exception e) {
            log.warn("Email notification failed (non-fatal). runId={} error={}", runId, e.getMessage());
        }
    }

    @Override
    public void sendPrRaisedEmail(UUID runId, String repoUrl, String prUrl,
                                  int issuesFixed, int issuesSkipped) {
        log.info("Sending PR-raised email. runId={} prUrl={}", runId, prUrl);
        try {
            emailService.sendPrRaised(runId, repoUrl, prUrl, issuesFixed, issuesSkipped);
        } catch (Exception e) {
            log.warn("Email notification failed (non-fatal). runId={} error={}", runId, e.getMessage());
        }
    }

    @Override
    public void updateRunStatus(UUID runId, String status) {
        pipelineService.updateStatus(runId, RunStatus.valueOf(status));
    }

    @Override
    public void updateRunWithPrDetails(UUID runId, String prUrl, String prBranch,
                                       int totalFixed, int totalFalsePositives) {
        pipelineService.updatePrDetails(runId, prUrl, prBranch, totalFixed, totalFalsePositives);
    }

    @Override
    public void saveFinding(UUID runId, String filePath, int lineNumber, String description,
                            String issueType, String severity, int confidence,
                            String status, String originalCode, String fixedCode,
                            String commitMessage, String criticReasoning) {
        pipelineService.saveFinding(runId, filePath, lineNumber, description, issueType,
                Severity.valueOf(severity), confidence, FindingStatus.valueOf(status),
                originalCode, fixedCode, commitMessage, criticReasoning);
    }
}
