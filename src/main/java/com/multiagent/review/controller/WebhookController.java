package com.multiagent.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiagent.review.domain.dto.TriggerRequest;
import com.multiagent.review.domain.dto.WebhookPayload;
import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.service.PipelineService;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WorkflowClient workflowClient;
    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/github")
    public ResponseEntity<String> handlePushEvent(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String rawBody) {

        if (!verifySignature(rawBody, signature)) {
            log.warn("Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        if (payload.getRepository() == null) {
            return ResponseEntity.badRequest().body("Missing repository in payload");
        }

        String repoUrl = payload.getRepository().getCloneUrl().replace(".git", "");
        String branch = payload.getBranchName();

        log.info("GitHub push event received. repo={} branch={}", repoUrl, branch);

        String workflowId = "code-review-webhook-" + UUID.randomUUID();
        TriggerRequest request = new TriggerRequest();
        request.setRepoUrl(repoUrl);
        request.setBranch(branch);

        PipelineRun run = pipelineService.createRun(repoUrl, branch, "webhook", workflowId);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(CodeReviewWorkflow.TASK_QUEUE)
                .build();

        CodeReviewWorkflow workflow = workflowClient.newWorkflowStub(CodeReviewWorkflow.class, options);
        WorkflowClient.start(workflow::execute, run.getId(), request);

        return ResponseEntity.accepted().body("Pipeline started: " + run.getId());
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }
}
