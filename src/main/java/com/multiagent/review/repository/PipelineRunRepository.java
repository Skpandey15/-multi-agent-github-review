package com.multiagent.review.repository;

import com.multiagent.review.domain.entity.PipelineRun;
import com.multiagent.review.domain.enums.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

    List<PipelineRun> findAllByOrderByStartedAtDesc();

    List<PipelineRun> findByStatus(RunStatus status);

    Optional<PipelineRun> findByTemporalWorkflowId(String workflowId);
}
