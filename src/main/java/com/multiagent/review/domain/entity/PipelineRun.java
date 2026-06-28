package com.multiagent.review.domain.entity;

import com.multiagent.review.domain.enums.RunStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pipeline_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String repoUrl;

    @Column(nullable = false)
    private String branch;

    @Column(nullable = false)
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    private String temporalWorkflowId;

    private String prUrl;

    private String prBranch;

    private Integer totalIssuesFound;

    private Integer totalIssuesFixed;

    private Integer totalFalsePositives;

    @CreationTimestamp
    private LocalDateTime startedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "pipelineRun", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Finding> findings = new ArrayList<>();
}
