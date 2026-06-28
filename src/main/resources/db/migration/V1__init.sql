CREATE TABLE pipeline_runs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_url             VARCHAR(500)  NOT NULL,
    branch               VARCHAR(255)  NOT NULL,
    triggered_by         VARCHAR(50)   NOT NULL,
    status               VARCHAR(50)   NOT NULL,
    temporal_workflow_id VARCHAR(255),
    pr_url               VARCHAR(500),
    pr_branch            VARCHAR(255),
    total_issues_found   INTEGER,
    total_issues_fixed   INTEGER,
    total_false_positives INTEGER,
    started_at           TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT now(),
    completed_at         TIMESTAMP
);

CREATE TABLE findings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id  UUID          NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    file_path        VARCHAR(500)  NOT NULL,
    line_number      INTEGER,
    description      VARCHAR(2000) NOT NULL,
    issue_type       VARCHAR(100)  NOT NULL,
    severity         VARCHAR(20)   NOT NULL,
    confidence       INTEGER       NOT NULL,
    status           VARCHAR(50)   NOT NULL,
    original_code    TEXT,
    fixed_code       TEXT,
    commit_message   VARCHAR(500),
    critic_reasoning TEXT,
    detected_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pipeline_runs_status    ON pipeline_runs(status);
CREATE INDEX idx_pipeline_runs_started   ON pipeline_runs(started_at DESC);
CREATE INDEX idx_findings_run_id         ON findings(pipeline_run_id);
CREATE INDEX idx_findings_severity       ON findings(severity);
CREATE INDEX idx_findings_status         ON findings(status);
