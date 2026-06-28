package com.multiagent.review.scheduler;

import com.multiagent.review.domain.dto.TriggerRequest;
import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.service.PipelineService;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class NightlyReviewScheduler {

    private final WorkflowClient workflowClient;
    private final PipelineService pipelineService;

    @Value("${app.scheduler.repos}")
    private List<String> reposToReview;

    @Value("${app.scheduler.branch:main}")
    private String branch;

    // Runs every night at 02:00 — configurable via app.scheduler.cron
    @Scheduled(cron = "${app.scheduler.cron:0 0 2 * * *}")
    public void runNightlyReview() {
        log.info("Nightly review scheduler triggered. repos={}", reposToReview.size());

        for (String repoUrl : reposToReview) {
            try {
                String workflowId = "code-review-cron-" + UUID.randomUUID();

                TriggerRequest request = new TriggerRequest();
                request.setRepoUrl(repoUrl);
                request.setBranch(branch);

                PipelineRun run = pipelineService.createRun(repoUrl, branch, "cron", workflowId);

                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(CodeReviewWorkflow.TASK_QUEUE)
                        .build();

                CodeReviewWorkflow workflow = workflowClient.newWorkflowStub(
                        CodeReviewWorkflow.class, options);
                WorkflowClient.start(workflow::execute, run.getId(), request);

                log.info("Nightly review started. runId={} repo={}", run.getId(), repoUrl);
            } catch (Exception e) {
                log.error("Failed to start nightly review for repo={}", repoUrl, e);
            }
        }
    }
}
