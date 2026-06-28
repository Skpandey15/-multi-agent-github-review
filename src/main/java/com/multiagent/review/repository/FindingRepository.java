package com.multiagent.review.repository;

import com.multiagent.review.domain.entity.Finding;
import com.multiagent.review.domain.enums.FindingStatus;
import com.multiagent.review.domain.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByPipelineRunId(UUID pipelineRunId);

    List<Finding> findByPipelineRunIdAndStatus(UUID pipelineRunId, FindingStatus status);

    List<Finding> findByPipelineRunIdAndSeverity(UUID pipelineRunId, Severity severity);
}
