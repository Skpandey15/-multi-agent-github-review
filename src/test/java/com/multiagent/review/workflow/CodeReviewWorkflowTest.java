package com.multiagent.review.workflow;

import com.multiagent.review.agent.model.*;
import com.multiagent.review.domain.dto.TriggerRequest;
import com.multiagent.review.domain.enums.FindingStatus;
import com.multiagent.review.domain.enums.Severity;
import com.multiagent.review.temporal.activity.GitHubActivities;
import com.multiagent.review.temporal.activity.LLMActivities;
import com.multiagent.review.temporal.activity.NotificationActivities;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflowImpl;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CodeReviewWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testWorkflow =
            TestWorkflowExtension.newBuilder()
                    .setWorkflowTypes(CodeReviewWorkflowImpl.class)
                    .setDoNotStart(true)
                    .build();

    // ── Stub implementations (Temporal rejects Mockito mocks on @ActivityInterface) ──

    static class StubGitHub implements GitHubActivities {
        private final CodeMap codeMap;
        private final String fileContent;

        StubGitHub(CodeMap codeMap, String fileContent) {
            this.codeMap = codeMap;
            this.fileContent = fileContent;
        }

        @Override public CodeMap cloneAndMapRepository(String r, String b) { return codeMap; }
        @Override public String createFixBranch(String r, String b, String n) { return n; }
        @Override public void commitFileChange(String r, String b, String p, String c, String m) {}
        @Override public String raisePullRequest(String r, String f, String b, String t, String body) {
            return "https://github.com/test/repo/pull/1";
        }
        @Override public String getFileContent(String r, String b, String p) { return fileContent; }
    }

    static class StubLLM implements LLMActivities {
        private final AnalysisResult analysisResult;
        private final CriticVerdict verdict;
        private final FixResult fix;

        StubLLM(AnalysisResult analysisResult, CriticVerdict verdict, FixResult fix) {
            this.analysisResult = analysisResult;
            this.verdict = verdict;
            this.fix = fix;
        }

        @Override public AnalysisResult analyseFile(String f, String l, String c) { return analysisResult; }
        @Override public CriticVerdict verifyFinding(String f, int ln, String it, String s,
                                                     int conf, String d, String cs, String l, String fc) { return verdict; }
        @Override public FixResult generateFix(String f, int ln, String it, String s,
                                               String d, String cs, String cr, String l, String fc) { return fix; }
    }

    static class StubNotify implements NotificationActivities {
        @Override public void sendAnalysisCompleteEmail(UUID r, String repo, int n) {}
        @Override public void sendPrRaisedEmail(UUID r, String repo, String pr, int f, int s) {}
        @Override public void updateRunStatus(UUID r, String s) {}
        @Override public void updateRunWithPrDetails(UUID r, String pr, String b, int f, int fp) {}
        @Override public void saveFinding(UUID r, String fp, int ln, String d, String it, String s,
                                          int c, String st, String oc, String fc, String cm, String cr) {}
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void shouldCompleteWorkflowAndReturnPrUrl(
            TestWorkflowEnvironment env, Worker worker, CodeReviewWorkflow workflow) {

        String code = "String q = \"SELECT * FROM users WHERE id = \" + id;";

        CodeFile file = CodeFile.builder()
                .path("src/UserService.java")
                .content(code)
                .language("java")
                .sizeBytes(1000)
                .build();

        CodeMap codeMap = CodeMap.builder()
                .repoUrl("https://github.com/test/repo")
                .branch("main")
                .detectedLanguage("java")
                .detectedFramework("Spring Boot")
                .files(List.of(file))
                .totalFiles(1)
                .build();

        IssueFound issue = new IssueFound();
        issue.setFilePath("src/UserService.java");
        issue.setLineNumber(5);
        issue.setDescription("SQL injection via string concatenation");
        issue.setIssueType("SECURITY");
        issue.setSeverity(Severity.HIGH);
        issue.setConfidence(92);
        issue.setCodeSnippet("\"SELECT * FROM users WHERE id = \" + id");

        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setIssues(List.of(issue));
        analysisResult.setSummary("1 security issue found");

        CriticVerdict verdict = new CriticVerdict();
        verdict.setFilePath("src/UserService.java");
        verdict.setLineNumber(5);
        verdict.setVerdict(FindingStatus.CONFIRMED);
        verdict.setReasoning("User input flows directly into SQL query.");

        FixResult fix = new FixResult();
        fix.setFilePath("src/UserService.java");
        fix.setOriginalCode("\"SELECT * FROM users WHERE id = \" + id");
        fix.setFixedCode("\"SELECT * FROM users WHERE id = ?\"");
        fix.setCommitMessage("Fix SQL injection — use parameterised query");
        fix.setFixApplied(true);

        worker.registerActivitiesImplementations(
                new StubGitHub(codeMap, code),
                new StubLLM(analysisResult, verdict, fix),
                new StubNotify());
        env.start();

        TriggerRequest request = new TriggerRequest();
        request.setRepoUrl("https://github.com/test/repo");
        request.setBranch("main");

        String result = workflow.execute(UUID.randomUUID(), request);
        assertThat(result).isEqualTo("https://github.com/test/repo/pull/1");
    }

    @Test
    void shouldReturnNoIssuesWhenAnalyserFindsNothing(
            TestWorkflowEnvironment env, Worker worker, CodeReviewWorkflow workflow) {

        CodeMap emptyMap = CodeMap.builder()
                .repoUrl("https://github.com/test/repo")
                .branch("main")
                .detectedLanguage("java")
                .files(List.of())
                .totalFiles(0)
                .build();

        AnalysisResult empty = new AnalysisResult();
        empty.setIssues(List.of());
        empty.setSummary("No issues");

        worker.registerActivitiesImplementations(
                new StubGitHub(emptyMap, ""),
                new StubLLM(empty, null, null),
                new StubNotify());
        env.start();

        TriggerRequest request = new TriggerRequest();
        request.setRepoUrl("https://github.com/test/repo");
        request.setBranch("main");

        String result = workflow.execute(UUID.randomUUID(), request);
        assertThat(result).isEqualTo("NO_ISSUES");
    }
}
