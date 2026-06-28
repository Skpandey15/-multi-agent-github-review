package com.multiagent.review.temporal.activity;

import com.multiagent.review.agent.AnalyserAgent;
import com.multiagent.review.agent.CriticAgent;
import com.multiagent.review.agent.FixerAgent;
import com.multiagent.review.agent.model.AnalysisResult;
import com.multiagent.review.agent.model.CriticVerdict;
import com.multiagent.review.agent.model.FixResult;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ActivityImpl(taskQueues = CodeReviewWorkflow.TASK_QUEUE)
public class LLMActivitiesImpl implements LLMActivities {

    private final AnalyserAgent analyserAgent;
    private final CriticAgent criticAgent;
    private final FixerAgent fixerAgent;

    @Override
    public AnalysisResult analyseFile(String filePath, String language, String code) {
        log.debug("Analyser invoking GPT-4o for file={}", filePath);
        return analyserAgent.analyseFile(filePath, language, code);
    }

    @Override
    public CriticVerdict verifyFinding(String filePath, int lineNumber, String issueType,
                                       String severity, int confidence, String description,
                                       String codeSnippet, String language, String fullCode) {
        log.debug("Critic verifying finding at {}:{}", filePath, lineNumber);
        return criticAgent.verify(filePath, lineNumber, issueType, severity, confidence,
                description, codeSnippet, language, fullCode);
    }

    @Override
    public FixResult generateFix(String filePath, int lineNumber, String issueType,
                                 String severity, String description, String codeSnippet,
                                 String criticReasoning, String language, String fullCode) {
        log.debug("Fixer generating fix for {}:{}", filePath, lineNumber);
        return fixerAgent.generateFix(filePath, lineNumber, issueType, severity, description,
                codeSnippet, criticReasoning, language, fullCode);
    }
}
