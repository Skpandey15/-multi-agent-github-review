package com.multiagent.review.graphql;

import com.multiagent.review.domain.entity.Finding;
import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ReportQueryResolver {

    private final PipelineService pipelineService;

    @QueryMapping
    public PipelineRun pipelineRun(@Argument String runId) {
        return pipelineService.getRun(UUID.fromString(runId));
    }

    @QueryMapping
    public List<PipelineRun> allRuns() {
        return pipelineService.getAllRuns();
    }

    @SchemaMapping(typeName = "PipelineRun", field = "findings")
    public List<Finding> findings(PipelineRun run) {
        return pipelineService.getFindings(run.getId());
    }
}
