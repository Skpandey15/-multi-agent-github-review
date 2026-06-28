package com.multiagent.review.temporal.workflow;

import com.multiagent.review.domain.dto.TriggerRequest;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

@WorkflowInterface
public interface CodeReviewWorkflow {

    String TASK_QUEUE = "code-review-queue";

    @WorkflowMethod
    String execute(UUID runId, TriggerRequest request);
}
