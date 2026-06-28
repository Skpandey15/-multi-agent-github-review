package com.multiagent.review.domain.entity;

import com.multiagent.review.domain.enums.FindingStatus;
import com.multiagent.review.domain.enums.Severity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_run_id", nullable = false)
    private PipelineRun pipelineRun;

    @Column(nullable = false)
    private String filePath;

    private Integer lineNumber;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private String issueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private Integer confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingStatus status;

    @Column(length = 4000)
    private String originalCode;

    @Column(length = 4000)
    private String fixedCode;

    private String commitMessage;

    @Column(length = 2000)
    private String criticReasoning;

    @CreationTimestamp
    private LocalDateTime detectedAt;
}
