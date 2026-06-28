package com.multiagent.review.temporal.workflow;

import com.multiagent.review.agent.model.*;
import com.multiagent.review.domain.dto.TriggerRequest;
import com.multiagent.review.domain.enums.FindingStatus;
import com.multiagent.review.domain.enums.RunStatus;
import com.multiagent.review.domain.enums.Severity;
import com.multiagent.review.temporal.activity.GitHubActivities;
import com.multiagent.review.temporal.activity.LLMActivities;
import com.multiagent.review.temporal.activity.NotificationActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CodeReviewWorkflowImpl implements CodeReviewWorkflow {

    private static final Logger log = Workflow.getLogger(CodeReviewWorkflowImpl.class);

    private static final int HIGH_CONFIDENCE_THRESHOLD = 75;
    private static final int MAX_FILES_TO_ANALYSE      = 50;

    // ── Activity stubs ────────────────────────────────────────────────────────

    private final GitHubActivities gitHub = Workflow.newActivityStub(
            GitHubActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    private final LLMActivities llm = Workflow.newActivityStub(
            LLMActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(2)
                            .setInitialInterval(Duration.ofSeconds(5))
                            .setBackoffCoefficient(1.5)
                            .build())
                    .build());

    private final NotificationActivities notify = Workflow.newActivityStub(
            NotificationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    // ── Workflow ──────────────────────────────────────────────────────────────

    @Override
    public String execute(UUID runId, TriggerRequest request) {
        log.info("[Agent-0] Pipeline started. runId={} repo={} branch={}",
                runId, request.getRepoUrl(), request.getBranch());

        try {
            return runPipeline(runId, request);
        } catch (Exception e) {
            log.error("[Pipeline] Unhandled failure. runId={} error={}", runId, e.getMessage());
            notifyFailureSilently(runId);
            throw e;
        }
    }

    private String runPipeline(UUID runId, TriggerRequest request) {

        // ── Agent 1: Clone & map ──────────────────────────────────────────────
        log.info("[Agent-1] Cloning repository. runId={}", runId);
        notify.updateRunStatus(runId, RunStatus.CLONING.name());

        CodeMap codeMap = gitHub.cloneAndMapRepository(request.getRepoUrl(), request.getBranch());
        List<CodeFile> filesToReview = limitFiles(codeMap.getFiles());

        log.info("[Agent-1] Clone complete. totalFiles={} reviewable={} language={} framework={}",
                codeMap.getTotalFiles(), filesToReview.size(),
                codeMap.getDetectedLanguage(), codeMap.getDetectedFramework());

        // ── Agent 2: Analyse ──────────────────────────────────────────────────
        log.info("[Agent-2] Starting analysis. runId={} files={}", runId, filesToReview.size());
        notify.updateRunStatus(runId, RunStatus.ANALYSING.name());

        List<IssueFound> allIssues = new ArrayList<>();
        for (CodeFile file : filesToReview) {
            try {
                AnalysisResult result = llm.analyseFile(
                        file.getPath(), file.getLanguage(), file.getContent());
                if (result.getIssues() != null) {
                    allIssues.addAll(result.getIssues());
                    log.info("[Agent-2] Analysed file={}  issues={}", file.getPath(), result.getIssues().size());
                }
            } catch (Exception e) {
                log.warn("[Agent-2] Skipped file={} reason={}", file.getPath(), e.getMessage());
            }
        }

        log.info("[Agent-2] Analysis complete. totalIssues={}", allIssues.size());
        notify.sendAnalysisCompleteEmail(runId, request.getRepoUrl(), allIssues.size());

        // ── Agent 3: Critic ───────────────────────────────────────────────────
        log.info("[Agent-3] Starting verification. runId={} issueCount={}", runId, allIssues.size());
        notify.updateRunStatus(runId, RunStatus.VERIFYING.name());

        List<IssueFound> confirmedIssues = new ArrayList<>();
        int falsePositives = 0;

        for (IssueFound issue : allIssues) {
            String fullCode = gitHub.getFileContent(
                    request.getRepoUrl(), request.getBranch(), issue.getFilePath());

            CriticVerdict verdict = llm.verifyFinding(
                    issue.getFilePath(), issue.getLineNumber(), issue.getIssueType(),
                    issue.getSeverity().name(), issue.getConfidence(), issue.getDescription(),
                    issue.getCodeSnippet(), codeMap.getDetectedLanguage(), fullCode);

            log.info("[Agent-3] file={}:{} verdict={} reason={}",
                    issue.getFilePath(), issue.getLineNumber(),
                    verdict.getVerdict(), verdict.getReasoning());

            if (FindingStatus.CONFIRMED == verdict.getVerdict()) {
                confirmedIssues.add(issue);
            } else {
                falsePositives++;
            }

            notify.saveFinding(runId, issue.getFilePath(), issue.getLineNumber(),
                    issue.getDescription(), issue.getIssueType(), issue.getSeverity().name(),
                    issue.getConfidence(), verdict.getVerdict().name(),
                    issue.getCodeSnippet(), null, null, verdict.getReasoning());
        }

        log.info("[Agent-3] Verification complete. confirmed={} falsePositives={}",
                confirmedIssues.size(), falsePositives);

        if (confirmedIssues.isEmpty()) {
            log.info("[Agent-3] No confirmed issues — skipping fix stage. runId={}", runId);
            notify.updateRunStatus(runId, RunStatus.COMPLETED.name());
            notify.updateRunWithPrDetails(runId, null, null, 0, falsePositives);
            return "NO_ISSUES";
        }

        // ── Agent 4: Fixer ────────────────────────────────────────────────────
        log.info("[Agent-4] Starting fixes. runId={} eligible={}", runId, confirmedIssues.size());
        notify.updateRunStatus(runId, RunStatus.FIXING.name());

        List<IssueFound> fixable = confirmedIssues.stream()
                .filter(i -> i.getConfidence() >= HIGH_CONFIDENCE_THRESHOLD
                        && i.getSeverity() != Severity.LOW)
                .toList();

        log.info("[Agent-4] Fixable (confidence≥{}% and severity≠LOW)={}", HIGH_CONFIDENCE_THRESHOLD, fixable.size());

        String fixBranch = "fix/auto-" + Instant.now().getEpochSecond();
        gitHub.createFixBranch(request.getRepoUrl(), request.getBranch(), fixBranch);
        log.info("[Agent-4] Created branch={}", fixBranch);

        int totalFixed = 0;
        StringBuilder prBody = buildPrBodyHeader();

        for (IssueFound issue : fixable) {
            String fullCode = gitHub.getFileContent(request.getRepoUrl(), fixBranch, issue.getFilePath());

            FixResult fix = llm.generateFix(
                    issue.getFilePath(), issue.getLineNumber(), issue.getIssueType(),
                    issue.getSeverity().name(), issue.getDescription(), issue.getCodeSnippet(),
                    "", codeMap.getDetectedLanguage(), fullCode);

            if (fix.isFixApplied()) {
                String updatedContent = fullCode.replace(fix.getOriginalCode(), fix.getFixedCode());
                gitHub.commitFileChange(request.getRepoUrl(), fixBranch,
                        issue.getFilePath(), updatedContent, fix.getCommitMessage());

                prBody.append(String.format("| `%s` | %d | %s | %s |\n",
                        issue.getFilePath(), issue.getLineNumber(),
                        issue.getDescription(), issue.getSeverity()));
                totalFixed++;

                log.info("[Agent-4] Fixed file={}:{} commit='{}'",
                        issue.getFilePath(), issue.getLineNumber(), fix.getCommitMessage());

                notify.saveFinding(runId, issue.getFilePath(), issue.getLineNumber(),
                        issue.getDescription(), issue.getIssueType(), issue.getSeverity().name(),
                        issue.getConfidence(), FindingStatus.FIXED.name(),
                        fix.getOriginalCode(), fix.getFixedCode(), fix.getCommitMessage(), null);
            } else {
                log.warn("[Agent-4] Skipped fix for file={}:{} reason={}",
                        issue.getFilePath(), issue.getLineNumber(), fix.getSkipReason());

                notify.saveFinding(runId, issue.getFilePath(), issue.getLineNumber(),
                        issue.getDescription(), issue.getIssueType(), issue.getSeverity().name(),
                        issue.getConfidence(), FindingStatus.SKIPPED.name(),
                        issue.getCodeSnippet(), null, null, fix.getSkipReason());
            }
        }

        log.info("[Agent-4] Fix stage complete. fixed={} skipped={}", totalFixed, fixable.size() - totalFixed);

        // ── Agent 5: Notifier ─────────────────────────────────────────────────
        log.info("[Agent-5] Raising PR and notifying. runId={}", runId);
        notify.updateRunStatus(runId, RunStatus.NOTIFYING.name());

        String prUrl = null;
        if (totalFixed > 0) {
            appendNeedsHumanSection(prBody, confirmedIssues, HIGH_CONFIDENCE_THRESHOLD);
            prUrl = gitHub.raisePullRequest(
                    request.getRepoUrl(), fixBranch, request.getBranch(),
                    String.format("[Auto-Review] %d issue(s) fixed", totalFixed),
                    prBody.toString());
            log.info("[Agent-5] PR raised. prUrl={}", prUrl);
        } else {
            log.info("[Agent-5] No fixes applied — skipping PR creation.");
        }

        int skipped = fixable.size() - totalFixed;
        notify.sendPrRaisedEmail(runId, request.getRepoUrl(), prUrl, totalFixed, skipped);
        notify.updateRunStatus(runId, RunStatus.COMPLETED.name());
        notify.updateRunWithPrDetails(runId, prUrl, fixBranch, totalFixed, falsePositives);

        log.info("[Pipeline] Completed. runId={} fixed={} falsePositives={} prUrl={}",
                runId, totalFixed, falsePositives, prUrl);

        return prUrl != null ? prUrl : "COMPLETED_NO_PR";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void notifyFailureSilently(UUID runId) {
        try {
            notify.updateRunStatus(runId, RunStatus.FAILED.name());
        } catch (Exception ex) {
            log.error("[Pipeline] Could not mark run as FAILED. runId={}", runId);
        }
    }

    private List<CodeFile> limitFiles(List<CodeFile> files) {
        return files.stream()
                .filter(f -> !f.getPath().contains("test") && !f.getPath().contains("generated"))
                .filter(f -> f.getSizeBytes() < 50_000)
                .limit(MAX_FILES_TO_ANALYSE)
                .toList();
    }

    private StringBuilder buildPrBodyHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Automated Code Review Fixes\n\n");
        sb.append("| File | Line | Issue | Severity |\n");
        sb.append("|------|------|-------|----------|\n");
        return sb;
    }

    private void appendNeedsHumanSection(StringBuilder prBody,
                                         List<IssueFound> confirmed,
                                         int threshold) {
        List<IssueFound> needsHuman = confirmed.stream()
                .filter(i -> i.getConfidence() < threshold || i.getSeverity() == Severity.LOW)
                .toList();

        if (!needsHuman.isEmpty()) {
            prBody.append("\n\n### Requires Human Review\n");
            needsHuman.forEach(i -> prBody.append(String.format(
                    "- [ ] `%s:%d` — %s (%s, confidence=%d%%)\n",
                    i.getFilePath(), i.getLineNumber(),
                    i.getDescription(), i.getSeverity(), i.getConfidence())));
        }
    }
}
