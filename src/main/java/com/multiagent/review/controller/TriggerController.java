package com.multiagent.review.controller;

import com.multiagent.review.domain.dto.TriggerRequest;
import com.multiagent.review.domain.dto.TriggerResponse;
import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.service.PipelineService;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TriggerController {

    private final WorkflowClient workflowClient;
    private final PipelineService pipelineService;

    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TriggerResponse trigger(@Valid @RequestBody TriggerRequest request) {
        String workflowId = "code-review-" + UUID.randomUUID();

        MDC.put("workflowId", workflowId);
        MDC.put("repoUrl", request.getRepoUrl());
        MDC.put("branch", request.getBranch());

        try {
            log.info("Manual trigger received");

            PipelineRun run = pipelineService.createRun(
                    request.getRepoUrl(), request.getBranch(), "manual", workflowId);

            MDC.put("runId", run.getId().toString());

            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(CodeReviewWorkflow.TASK_QUEUE)
                    .build();

            CodeReviewWorkflow workflow = workflowClient.newWorkflowStub(
                    CodeReviewWorkflow.class, options);
            WorkflowClient.start(workflow::execute, run.getId(), request);

            log.info("Workflow started successfully");

            return TriggerResponse.builder()
                    .runId(run.getId())
                    .workflowId(workflowId)
                    .message("Pipeline started successfully")
                    .statusUrl("/api/v1/status/" + run.getId())
                    .build();
        } finally {
            MDC.remove("workflowId");
            MDC.remove("repoUrl");
            MDC.remove("branch");
            MDC.remove("runId");
        }
    }
}
