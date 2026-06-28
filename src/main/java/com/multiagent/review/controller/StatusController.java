package com.multiagent.review.controller;

import com.multiagent.review.domain.dto.StatusResponse;
import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatusController {

    private final PipelineService pipelineService;

    @GetMapping("/status/{runId}")
    public StatusResponse getStatus(@PathVariable UUID runId) {
        PipelineRun run = pipelineService.getRun(runId);
        return toResponse(run);
    }

    @GetMapping("/runs")
    public List<StatusResponse> listRuns() {
        return pipelineService.getAllRuns().stream()
                .map(this::toResponse)
                .toList();
    }

    private StatusResponse toResponse(PipelineRun run) {
        return StatusResponse.builder()
                .runId(run.getId())
                .repoUrl(run.getRepoUrl())
                .branch(run.getBranch())
                .status(run.getStatus())
                .prUrl(run.getPrUrl())
                .totalIssuesFound(run.getTotalIssuesFound())
                .totalIssuesFixed(run.getTotalIssuesFixed())
                .totalFalsePositives(run.getTotalFalsePositives())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }
}
