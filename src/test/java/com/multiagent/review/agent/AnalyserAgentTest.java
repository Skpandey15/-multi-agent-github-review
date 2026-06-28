package com.multiagent.review.agent;

import com.multiagent.review.agent.model.AnalysisResult;
import com.multiagent.review.agent.model.IssueFound;
import com.multiagent.review.domain.enums.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyserAgentTest {

    @Mock
    private AnalyserAgent analyserAgent;

    @Test
    void shouldDetectSqlInjectionIssue() {
        IssueFound sqlInjection = new IssueFound();
        sqlInjection.setFilePath("src/main/java/UserRepository.java");
        sqlInjection.setLineNumber(42);
        sqlInjection.setDescription("SQL injection via string concatenation in query");
        sqlInjection.setIssueType("SECURITY");
        sqlInjection.setSeverity(Severity.HIGH);
        sqlInjection.setConfidence(92);
        sqlInjection.setCodeSnippet("String query = \"SELECT * FROM users WHERE id = \" + userId;");

        AnalysisResult mockResult = new AnalysisResult();
        mockResult.setIssues(List.of(sqlInjection));
        mockResult.setSummary("1 high-severity security issue found");

        when(analyserAgent.analyseFile(anyString(), anyString(), anyString()))
                .thenReturn(mockResult);

        AnalysisResult result = analyserAgent.analyseFile(
                "src/main/java/UserRepository.java", "java",
                "String query = \"SELECT * FROM users WHERE id = \" + userId;");

        assertThat(result.getIssues()).hasSize(1);
        assertThat(result.getIssues().get(0).getIssueType()).isEqualTo("SECURITY");
        assertThat(result.getIssues().get(0).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.getIssues().get(0).getConfidence()).isGreaterThanOrEqualTo(75);
    }

    @Test
    void shouldReturnEmptyListForCleanCode() {
        AnalysisResult mockResult = new AnalysisResult();
        mockResult.setIssues(List.of());
        mockResult.setSummary("No issues found");

        when(analyserAgent.analyseFile(anyString(), anyString(), anyString()))
                .thenReturn(mockResult);

        AnalysisResult result = analyserAgent.analyseFile(
                "src/main/java/HealthCheck.java", "java",
                "public class HealthCheck { public String status() { return \"OK\"; } }");

        assertThat(result.getIssues()).isEmpty();
    }
}
