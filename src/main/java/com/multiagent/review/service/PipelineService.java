package com.multiagent.review.service;

import com.multiagent.review.domain.entity.Finding;
import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.domain.enums.FindingStatus;
import com.multiagent.review.domain.enums.RunStatus;
import com.multiagent.review.domain.enums.Severity;
import com.multiagent.review.exception.PipelineException;
import com.multiagent.review.repository.FindingRepository;
import com.multiagent.review.repository.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRunRepository runRepository;
    private final FindingRepository findingRepository;

    @Transactional
    public PipelineRun createRun(String repoUrl, String branch, String triggeredBy,
                                 String workflowId) {
        PipelineRun run = PipelineRun.builder()
                .repoUrl(repoUrl)
                .branch(branch)
                .triggeredBy(triggeredBy)
                .status(RunStatus.PENDING)
                .temporalWorkflowId(workflowId)
                .build();
        return runRepository.save(run);
    }

    @Transactional
    public void updateStatus(UUID runId, RunStatus status) {
        PipelineRun run = findRunOrThrow(runId);
        run.setStatus(status);
        if (status == RunStatus.COMPLETED || status == RunStatus.FAILED) {
            run.setCompletedAt(LocalDateTime.now());
        }
        runRepository.save(run);
    }

    @Transactional
    public void updatePrDetails(UUID runId, String prUrl, String prBranch,
                                int totalFixed, int totalFalsePositives) {
        PipelineRun run = findRunOrThrow(runId);
        run.setPrUrl(prUrl);
        run.setPrBranch(prBranch);
        run.setTotalIssuesFixed(totalFixed);
        run.setTotalFalsePositives(totalFalsePositives);
        runRepository.save(run);
    }

    @Transactional
    public void saveFinding(UUID runId, String filePath, int lineNumber, String description,
                            String issueType, Severity severity, int confidence,
                            FindingStatus status, String originalCode, String fixedCode,
                            String commitMessage, String criticReasoning) {
        PipelineRun run = findRunOrThrow(runId);

        Finding finding = Finding.builder()
                .pipelineRun(run)
                .filePath(filePath)
                .lineNumber(lineNumber)
                .description(description)
                .issueType(issueType)
                .severity(severity)
                .confidence(confidence)
                .status(status)
                .originalCode(originalCode)
                .fixedCode(fixedCode)
                .commitMessage(commitMessage)
                .criticReasoning(criticReasoning)
                .build();

        findingRepository.save(finding);

        run.setTotalIssuesFound(
                (run.getTotalIssuesFound() == null ? 0 : run.getTotalIssuesFound()) + 1);
        runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public PipelineRun getRun(UUID runId) {
        return findRunOrThrow(runId);
    }

    @Transactional(readOnly = true)
    public List<PipelineRun> getAllRuns() {
        return runRepository.findAllByOrderByStartedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Finding> getFindings(UUID runId) {
        return findingRepository.findByPipelineRunId(runId);
    }

    private PipelineRun findRunOrThrow(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new PipelineException("Pipeline run not found: " + runId));
    }
}
