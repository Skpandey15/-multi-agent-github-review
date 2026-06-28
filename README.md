# Multi-Agent GitHub Code Review & Auto-Fix System

A production-ready autonomous code review system built on a **6-agent pipeline** that clones a GitHub repository, analyses every file with GPT-4o, adversarially challenges its own findings, generates code fixes, raises a Pull Request, and persists every decision to a database — all orchestrated by Temporal.io for durability and fault tolerance.

---

## Table of Contents

- [High Level Design (HLD)](#high-level-design-hld)
- [Low Level Design (LLD)](#low-level-design-lld)
- [Architecture Overview](#architecture-overview)
- [6-Agent Pipeline](#6-agent-pipeline)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Environment Variables](#environment-variables)
- [Running with Docker Compose](#running-with-docker-compose)
- [API Reference](#api-reference)
- [GraphQL API](#graphql-api)
- [Useful SQL Queries](#useful-sql-queries)
- [Observability](#observability)
- [Design Decisions](#design-decisions)
- [Real Test Results](#real-test-results)

---

## High Level Design (HLD)

### System Goals

| Goal | How achieved |
|---|---|
| Autonomous code review | 6-agent LLM pipeline with no human needed until PR |
| No false positive patches | Adversarial Critic agent eliminates hallucinations |
| Fault tolerance | Temporal.io durable workflows — survives restarts |
| Human safety | System raises PR, never auto-merges |
| Full auditability | Every decision persisted to PostgreSQL |
| Observability | MDC correlation IDs, Temporal UI, structured logs |

---

### HLD — Component Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                              │
│                                                                  │
│   Developer / CI      GitHub Webhook       Scheduler (cron)      │
│        │                    │                     │              │
└────────┼────────────────────┼─────────────────────┼─────────────┘
         │                    │                     │
         ▼                    ▼                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                      API LAYER (Spring Boot :8081)               │
│                                                                  │
│   PipelineController        WebhookController                    │
│   POST /api/v1/trigger      POST /api/v1/webhook (HMAC verify)   │
│   GET  /api/v1/status/:id   GET  /api/v1/runs                    │
│   GraphQL /graphql          GraphiQL /graphiql                   │
└──────────────────────────┬───────────────────────────────────────┘
                           │  starts Temporal workflow
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                   ORCHESTRATION LAYER (Temporal :7233)           │
│                                                                  │
│   CodeReviewWorkflow                                             │
│   ┌──────────────────────────────────────────────────────────┐  │
│   │  Agent-1    Agent-2    Agent-3    Agent-4    Agent-5/6   │  │
│   │  Reader  →  Analyser → Critic  → Fixer   → Notifier     │  │
│   └──────────────────────────────────────────────────────────┘  │
│   • Durable execution — survives pod/server restarts             │
│   • Per-activity retry with exponential backoff                  │
│   • MDC context propagated across all activity threads           │
└──────┬────────────────┬───────────────────────────┬─────────────┘
       │                │                           │
       ▼                ▼                           ▼
┌────────────┐  ┌───────────────┐        ┌──────────────────┐
│  GitHub    │  │  OpenAI API   │        │  PostgreSQL :5432 │
│  API       │  │  GPT-4o       │        │                  │
│            │  │               │        │  pipeline_runs   │
│  • clone   │  │  • analyse    │        │  findings        │
│  • branch  │  │  • critique   │        │                  │
│  • commit  │  │  • fix        │        └──────────────────┘
│  • PR      │  └───────────────┘
└────────────┘
```

---

### HLD — Data Flow

```
1. TRIGGER
   User calls POST /api/v1/trigger { repoUrl, branch }
   → Spring Boot creates PipelineRun record (status=PENDING)
   → Starts Temporal workflow asynchronously
   → Returns { runId, statusUrl } immediately

2. CLONE  (Agent-1)
   Temporal calls GitHubActivities.cloneAndMapRepository()
   → GitHub API fetches all files
   → Filters: no tests, no generated, < 50 KB, max 50 files
   → Returns CodeMap { files[], language, framework }
   → DB: status = CLONING

3. ANALYSE  (Agent-2)
   For each file → LLMActivities.analyseFile()
   → GPT-4o prompt: "find bugs, security issues, code smells"
   → Returns List<IssueFound> per file
   → DB: status = ANALYSING, totalIssuesFound updated

4. CRITIC  (Agent-3)
   For each issue → LLMActivities.verifyFinding()
   → GPT-4o prompt: "try to REFUTE this finding"
   → Returns CriticVerdict: CONFIRMED / FALSE_POSITIVE / NEEDS_HUMAN
   → FALSE_POSITIVE issues are discarded, never patched

5. FIX  (Agent-4)
   Filter: CONFIRMED + severity HIGH/MEDIUM + confidence ≥ 75%
   For each eligible → LLMActivities.generateFix()
   → GPT-4o returns fixed code + commit message
   → DB: status = FIXING

6. PR  (Agent-5)
   GitHubActivities.createFixBranch()
   For each fix → GitHubActivities.commitFileChange()
   GitHubActivities.raisePullRequest()
   → DB: status = PR_RAISED, prUrl saved

7. NOTIFY  (Agent-6)
   NotificationActivities.saveFinding() for all findings
   NotificationActivities.sendPrRaisedEmail()
   → DB: status = COMPLETED
```

---

### HLD — Key Design Patterns

| Pattern | Where used | Why |
|---|---|---|
| **Adversarial Critic** | Agent-3 vs Agent-2 | Eliminates LLM hallucinations before patching |
| **Human-in-the-loop** | PR raised, never merged | Safety — humans approve AI changes |
| **Durable Workflow** | Temporal orchestration | Fault tolerance across all 6 agents |
| **Activity Retry** | Every Temporal activity | Transient failures (rate limits, network) auto-recovered |
| **Correlation ID** | MDC + ContextPropagator | Full trace across HTTP → Temporal threads |
| **RFC 7807** | GlobalExceptionHandler | Consistent machine-readable error responses |
| **Confidence Threshold** | Fixer filter ≥ 75% | Only high-confidence findings get patched |

---

## Low Level Design (LLD)

### LLD — Database Schema

```sql
-- Pipeline run lifecycle record
CREATE TABLE pipeline_runs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_url             VARCHAR(500)  NOT NULL,
    branch               VARCHAR(100)  NOT NULL DEFAULT 'main',
    triggered_by         VARCHAR(100),
    status               VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    pr_url               VARCHAR(500),
    pr_branch            VARCHAR(200),
    total_issues_found   INTEGER,
    total_issues_fixed   INTEGER,
    total_false_positives INTEGER,
    started_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMP
);

-- Individual finding per file per issue
CREATE TABLE findings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id  UUID          NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    file_path        VARCHAR(500)  NOT NULL,
    line_number      INTEGER,
    description      VARCHAR(2000) NOT NULL,
    issue_type       VARCHAR(100)  NOT NULL,  -- SECURITY / BUG / CODE_SMELL / PERFORMANCE
    severity         VARCHAR(20)   NOT NULL,  -- HIGH / MEDIUM / LOW
    confidence       INTEGER       NOT NULL,  -- 0-100
    status           VARCHAR(50)   NOT NULL,  -- CONFIRMED / FALSE_POSITIVE / NEEDS_HUMAN / FIXED
    original_code    TEXT,
    fixed_code       TEXT,
    commit_message   VARCHAR(500),
    critic_reasoning TEXT,
    detected_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_findings_run_id  ON findings(pipeline_run_id);
CREATE INDEX idx_findings_severity ON findings(severity);
CREATE INDEX idx_findings_status   ON findings(status);
```

---

### LLD — Temporal Workflow Internals

```java
// CodeReviewWorkflowImpl — activity options per stage

// GitHub activities: longer timeout, fewer retries (external API)
ActivityOptions githubOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(5))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(3)
        .setInitialInterval(Duration.ofSeconds(2))
        .build())
    .build();

// LLM activities: longer timeout (GPT-4o latency), more retries (rate limits)
ActivityOptions llmOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(3))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(5)
        .setInitialInterval(Duration.ofSeconds(5))
        .build())
    .build();

// Notification activities: short timeout, non-fatal
ActivityOptions notifyOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(30))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(2)
        .build())
    .build();
```

---

### LLD — LangChain4j Agent Interface Design

```java
// Analyser — structured JSON output via response_format: json_object
@AiService
public interface AnalyserAgent {
    @SystemMessage("""
        You are a senior code reviewer. Analyse the provided source file.
        Return ONLY valid JSON matching AnalysisResult schema.
        Identify: SECURITY vulnerabilities, BUG risks, CODE_SMELL patterns,
        UNUSED_IMPORT, PERFORMANCE issues.
        For each issue provide: filePath, lineNumber, description,
        issueType, severity (HIGH/MEDIUM/LOW), confidence (0-100), codeSnippet.
        """)
    AnalysisResult analyse(@UserMessage String fileContext);
}

// Critic — adversarial prompt, tries to REFUTE
@AiService
public interface CriticAgent {
    @SystemMessage("""
        You are an adversarial code reviewer. Your job is to CHALLENGE findings.
        For each finding: try hard to prove it is a FALSE_POSITIVE.
        Only return CONFIRMED if the evidence is undeniable.
        Return NEEDS_HUMAN if context is insufficient to decide.
        """)
    CriticVerdict verify(@UserMessage String findingContext);
}

// Fixer — generates targeted patch only
@AiService
public interface FixerAgent {
    @SystemMessage("""
        You are an expert Java developer. Generate a minimal, targeted fix.
        Return ONLY the fixed code for the specific lines affected.
        Do NOT rewrite surrounding code. Include a concise commit message.
        """)
    FixResult fix(@UserMessage String issueContext);
}
```

---

### LLD — File Filtering Logic

```
Files EXCLUDED from review:
  path contains: /test/, /Test, Spec.java, Mock, Generated, target/, build/
  size > 50 KB
  extension not in: .java, .kt, .py, .js, .ts, .go

Max files per run: 50 (sorted by size ascending — smallest first)

Language detection: majority file extension in the filtered set
Framework detection: presence of Spring Boot / Quarkus / Micronaut imports
```

---

### LLD — Fixer Eligibility Filter

```
A finding is eligible for auto-fix if ALL conditions are true:
  ✓ Critic verdict = CONFIRMED
  ✓ Severity = HIGH or MEDIUM  (LOW issues logged but not patched)
  ✓ Confidence ≥ 75
  ✓ Issue type ≠ NEEDS_HUMAN

Rationale:
  LOW severity + auto-patch = noisy diff with minimal value
  Confidence < 75 = LLM not sure enough to touch production code
```

---

### LLD — Correlation ID & MDC Propagation

```
HTTP Request arrives
  │
  ▼
CorrelationIdFilter (OncePerRequestFilter)
  → reads X-Correlation-Id header (or generates UUID)
  → puts into MDC: correlationId
  → adds to response header

Temporal workflow starts
  │
  ▼
MdcContextPropagator (implements ContextPropagator)
  → serialize(): reads MDC map, encodes to Temporal header
  → deserialize(): reads Temporal header, restores MDC
  → Runs on EVERY activity thread

Result: every log line from every agent thread carries:
  [corrId=abc123] [wfId=code-review-...] [runId=a1de...]
```

---

### LLD — Error Handling Strategy

```
Layer               Exception                   Handler
─────────────────────────────────────────────────────────
Controller          MethodArgumentNotValid      GlobalExceptionHandler → 400
Controller          MethodArgumentTypeMismatch  GlobalExceptionHandler → 400
Service             GitHubApiException          GlobalExceptionHandler → 502
Service             EmailDeliveryException      Caught in activity → WARN log (non-fatal)
Workflow            Any unhandled exception     Temporal retry → then FAILED status
Activity            GHException (GitHub)        Wrapped in GitHubApiException
Activity            OpenAI timeout              Temporal retries up to maxAttempts
All                 Exception                   GlobalExceptionHandler → 500

All error responses use RFC 7807 ProblemDetail:
{
  "type": "about:blank",
  "title": "GitHub API Error",
  "status": 502,
  "detail": "Repository not found: org/repo",
  "instance": "/api/v1/trigger"
}
```

---

### LLD — Sequence Diagram (Happy Path)

```
Client          App             Temporal        GitHub API      OpenAI
  │               │                 │               │              │
  │─POST trigger─▶│                 │               │              │
  │               │─start workflow─▶│               │              │
  │◀─{runId}──────│                 │               │              │
  │               │                 │               │              │
  │               │           [Agent-1 Clone]        │              │
  │               │                 │──getRepo()───▶│              │
  │               │                 │◀──CodeMap─────│              │
  │               │                 │               │              │
  │               │           [Agent-2 Analyse]                     │
  │               │                 │──analyseFile()───────────────▶│
  │               │                 │◀──AnalysisResult──────────────│
  │               │                 │  (per file, parallel)         │
  │               │                 │               │              │
  │               │           [Agent-3 Critic]                      │
  │               │                 │──verifyFinding()─────────────▶│
  │               │                 │◀──CriticVerdict───────────────│
  │               │                 │  (per issue)                  │
  │               │                 │               │              │
  │               │           [Agent-4 Fix]                         │
  │               │                 │──generateFix()───────────────▶│
  │               │                 │◀──FixResult───────────────────│
  │               │                 │               │              │
  │               │           [Agent-5 PR]           │              │
  │               │                 │──createBranch()─▶│            │
  │               │                 │──commitFile()───▶│            │
  │               │                 │──raisePR()──────▶│            │
  │               │                 │◀──prUrl──────────│            │
  │               │                 │               │              │
  │               │           [Agent-6 Notify]       │              │
  │               │                 │──updateDB()     │              │
  │               │                 │──sendEmail()    │              │
  │               │                 │               │              │
  │─GET status───▶│                 │               │              │
  │◀─COMPLETED────│                 │               │              │
```

---

## Architecture Overview

```
User / Webhook
      │
      ▼
┌─────────────────┐
│  REST API        │  POST /api/v1/trigger
│  Spring Boot     │  GET  /api/v1/status/:id
└────────┬────────┘
         │  starts workflow
         ▼
┌─────────────────┐
│  Temporal.io     │  Durable orchestration
│  Workflow Engine │  Auto-retry on failure
└────────┬────────┘
         │
    ┌────▼──────────────────────────────────────┐
    │           6-Agent Pipeline                 │
    │                                            │
    │  Agent-1  Reader     → clone & map repo   │
    │  Agent-2  Analyser   → GPT-4o scan        │
    │  Agent-3  Critic     → adversarial verify │
    │  Agent-4  Fixer      → generate patches   │
    │  Agent-5  PR Raiser  → commit & PR        │
    │  Agent-6  Notifier   → email + DB update  │
    └────────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────────┐
│   PostgreSQL     │     │   GitHub API     │
│   findings       │     │   Pull Request   │
│   pipeline_runs  │     │   (human review) │
└─────────────────┘     └──────────────────┘
```

**Human-in-the-loop**: The system raises a PR but **never auto-merges**. A human always reviews and approves.

---

## 6-Agent Pipeline

### Agent-1 — Reader (GitHub Activity)
- Connects to GitHub API using `kohsuke/github-api`
- Reads every file in the repository
- Filters out: test files, generated code, files > 50 KB, max 50 files
- Detects primary language and framework
- Returns a `CodeMap` with all reviewable `CodeFile` objects

### Agent-2 — Analyser (LangChain4j + GPT-4o)
- Sends each file to GPT-4o with a structured prompt
- Returns issues typed as: `SECURITY`, `BUG`, `CODE_SMELL`, `UNUSED_IMPORT`, `PERFORMANCE`
- Each issue has: file path, line number, severity (HIGH/MEDIUM/LOW), confidence (0–100), code snippet

### Agent-3 — Critic (LangChain4j + GPT-4o — Adversarial)
- Independently challenges every finding from Agent-2
- Tries to **refute** each issue, not confirm it
- Returns: `CONFIRMED`, `FALSE_POSITIVE`, or `NEEDS_HUMAN`
- Eliminates hallucinated issues before any code is touched

### Agent-4 — Fixer (LangChain4j + GPT-4o)
- Only processes issues that are `CONFIRMED` + severity `HIGH` or `MEDIUM` + confidence ≥ 75%
- Generates the fixed version of each code snippet
- Produces a meaningful commit message per fix

### Agent-5 — PR Raiser (GitHub Activity)
- Creates a new branch: `fix/auto-{timestamp}`
- Commits each fix individually with its commit message
- Raises a Pull Request against `main` with a full summary

### Agent-6 — Notifier (Notification Activity)
- Persists all findings to PostgreSQL
- Updates run status
- Sends email notification via Gmail SMTP (non-fatal if email fails)

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.3.4 |
| Agent Interface | LangChain4j | 0.35.0 |
| LLM | OpenAI GPT-4o | — |
| Workflow Engine | Temporal.io | 1.24.2 |
| GitHub Integration | kohsuke/github-api | 1.321 |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | — |
| API | REST + GraphQL | — |
| Build | Gradle (Groovy DSL) | 8.10 |
| Container | Docker Compose | — |
| Email | Gmail SMTP | — |
| Logging | Logback + MDC | — |
| Error Responses | RFC 7807 ProblemDetail | — |

---

## Project Structure

```
src/main/java/com/multiagent/review/
│
├── agent/
│   ├── AnalyserAgent.java          # LangChain4j @AiService interface
│   ├── CriticAgent.java            # Adversarial critic interface
│   ├── FixerAgent.java             # Code fix generator interface
│   └── model/
│       ├── AnalysisResult.java
│       ├── CodeFile.java
│       ├── CodeMap.java
│       ├── CriticVerdict.java
│       ├── FixResult.java
│       └── IssueFound.java
│
├── config/
│   ├── CorrelationIdFilter.java    # MDC correlation ID per request
│   ├── LangChainConfig.java        # AiServices bean registration
│   ├── MdcContextPropagator.java   # Carries MDC into Temporal threads
│   └── TemporalConfig.java         # WorkflowClient with context propagators
│
├── controller/
│   ├── PipelineController.java     # REST: trigger, status, history
│   └── WebhookController.java      # GitHub webhook with HMAC verification
│
├── domain/
│   ├── entity/
│   │   ├── Finding.java
│   │   └── PipelineRun.java
│   ├── dto/
│   │   ├── TriggerRequest.java
│   │   ├── TriggerResponse.java
│   │   └── RunStatusResponse.java
│   └── enums/
│       ├── FindingStatus.java      # CONFIRMED / FALSE_POSITIVE / NEEDS_HUMAN / FIXED
│       ├── RunStatus.java          # PENDING / CLONING / ANALYSING / FIXING / PR_RAISED / COMPLETED / FAILED
│       └── Severity.java           # HIGH / MEDIUM / LOW
│
├── exception/
│   ├── GlobalExceptionHandler.java # RFC 7807 ProblemDetail for all errors
│   ├── GitHubApiException.java
│   ├── EmailDeliveryException.java
│   └── PipelineException.java
│
├── graphql/
│   └── PipelineQueryResolver.java  # allRuns / pipelineRun(id)
│
├── service/
│   ├── EmailService.java           # Gmail SMTP notifications
│   ├── GitHubService.java          # GitHub API operations
│   └── PipelineService.java        # DB persistence
│
└── temporal/
    ├── workflow/
    │   ├── CodeReviewWorkflow.java         # @WorkflowInterface
    │   └── CodeReviewWorkflowImpl.java     # Full 6-agent pipeline logic
    └── activity/
        ├── GitHubActivities.java
        ├── GitHubActivitiesImpl.java
        ├── LLMActivities.java
        ├── LLMActivitiesImpl.java
        ├── NotificationActivities.java
        └── NotificationActivitiesImpl.java
```

---

## Prerequisites

| Requirement | Notes |
|---|---|
| Java 17 | Microsoft OpenJDK 17 or Eclipse Temurin 17 |
| Docker + Docker Compose | Rancher Desktop or Docker Desktop |
| GitHub Personal Access Token | Needs `Contents: Read & Write` + `Pull requests: Read & Write` |
| OpenAI API Key | GPT-4o access required |
| Gmail App Password | Google Account → Security → 2-Step → App Passwords |

---

## Environment Variables

Create a `.env` file in the project root:

```env
# GitHub
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
GITHUB_WEBHOOK_SECRET=your_webhook_secret

# OpenAI
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx

# Gmail SMTP
GMAIL_USERNAME=your@gmail.com
GMAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx

# Email recipient
APP_EMAIL_TO=recipient@email.com

# Database (used by docker-compose, do not change)
DB_HOST=postgres
DB_NAME=github_review
DB_USER=postgres
DB_PASSWORD=postgres

# Temporal (used by docker-compose, do not change)
TEMPORAL_HOST=temporal

# Scheduler (off by default)
SCHEDULER_ENABLED=false
```

> **Gmail App Password**: Go to [myaccount.google.com](https://myaccount.google.com) → Security → 2-Step Verification → App Passwords → Generate one for "Mail".

> **GitHub Token**: Go to GitHub → Settings → Developer Settings → Fine-grained tokens → set `Contents` and `Pull requests` to **Read and write**.

---

## Running with Docker Compose

```bash
# 1. Clone the repo
git clone https://github.com/your-org/github-review.git
cd github-review

# 2. Create the .env file (see above)
cp .env.example .env
# edit .env with your real values

# 3. Build the jar locally first
./gradlew bootJar -x test

# 4. Start all services
docker compose up -d --build

# 5. Check all containers are running
docker compose ps
```

Expected output:
```
github_review_app          Up    0.0.0.0:8081->8081/tcp
github_review_postgres     Up    0.0.0.0:5432->5432/tcp (healthy)
github_review_temporal     Up    0.0.0.0:7233->7233/tcp
github_review_temporal_ui  Up    0.0.0.0:8080->8080/tcp
```

```bash
# 6. Verify the app started
curl http://localhost:8081/actuator/health
```

---

## API Reference

### Trigger a pipeline run

```http
POST /api/v1/trigger
Content-Type: application/json

{
  "repoUrl": "https://github.com/your-org/your-repo.git",
  "branch": "main"
}
```

Response:
```json
{
  "runId": "a1de0398-c514-4571-9841-46840624984b",
  "workflowId": "code-review-504005b1-...",
  "message": "Pipeline started successfully",
  "statusUrl": "/api/v1/status/a1de0398-c514-4571-9841-46840624984b"
}
```

---

### Poll run status

```http
GET /api/v1/status/{runId}
```

Response:
```json
{
  "runId": "a1de0398-c514-4571-9841-46840624984b",
  "repoUrl": "https://github.com/Skpandey15/auth-service-jwt.git",
  "branch": "main",
  "status": "COMPLETED",
  "prUrl": "https://github.com/Skpandey15/auth-service-jwt/pull/5",
  "totalIssuesFound": 54,
  "totalIssuesFixed": 15,
  "totalFalsePositives": 17,
  "startedAt": "2026-06-28T11:08:13",
  "completedAt": "2026-06-28T11:11:37"
}
```

Status values: `PENDING` → `CLONING` → `ANALYSING` → `FIXING` → `PR_RAISED` → `COMPLETED` / `FAILED`

---

### All runs

```http
GET /api/v1/runs
```

### GitHub Webhook

```http
POST /api/v1/webhook
X-Hub-Signature-256: sha256=...
X-GitHub-Event: push
```

HMAC-SHA256 signature is verified against `GITHUB_WEBHOOK_SECRET`.

---

## GraphQL API

GraphiQL UI (no CDN — fully self-hosted): `http://localhost:8081/graphiql`

### Query all runs with findings

```graphql
query AllRuns {
  allRuns {
    id
    repoUrl
    branch
    status
    totalIssuesFound
    totalIssuesFixed
    totalFalsePositives
    prUrl
    startedAt
    completedAt
    findings {
      filePath
      lineNumber
      issueType
      severity
      status
      description
      fixedCode
      commitMessage
      criticReasoning
    }
  }
}
```

### Query a single run by ID

```graphql
query SingleRun {
  pipelineRun(id: "a1de0398-c514-4571-9841-46840624984b") {
    id
    status
    totalIssuesFound
    prUrl
    findings {
      filePath
      severity
      status
      description
    }
  }
}
```

---

## Useful SQL Queries

Connect: `host=localhost port=5432 db=github_review user=postgres password=postgres`

```sql
-- 1. All runs with duration
SELECT id, status, total_issues_found, total_issues_fixed,
       total_false_positives, pr_url,
       ROUND(EXTRACT(EPOCH FROM (completed_at - started_at))) AS duration_secs
FROM pipeline_runs ORDER BY started_at DESC;

-- 2. All FIXED issues
SELECT file_path, line_number, severity, issue_type,
       description, commit_message
FROM findings
WHERE pipeline_run_id = '<run-id>' AND status = 'FIXED'
ORDER BY severity, file_path;

-- 3. Issue breakdown by severity and status
SELECT severity, status, COUNT(*) AS total
FROM findings
WHERE pipeline_run_id = '<run-id>'
GROUP BY severity, status
ORDER BY CASE severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, status;

-- 4. False positives with critic reasoning
SELECT file_path, line_number, description, critic_reasoning
FROM findings
WHERE pipeline_run_id = '<run-id>' AND status = 'FALSE_POSITIVE'
ORDER BY severity DESC;

-- 5. Needs human review
SELECT file_path, line_number, severity, description, critic_reasoning
FROM findings
WHERE pipeline_run_id = '<run-id>' AND status = 'NEEDS_HUMAN';

-- 6. Before vs after — original and fixed code side by side
SELECT file_path, line_number, description,
       original_code, fixed_code, commit_message
FROM findings
WHERE pipeline_run_id = '<run-id>'
  AND status = 'FIXED' AND original_code IS NOT NULL
ORDER BY file_path;

-- 7. Security issues only
SELECT file_path, line_number, severity, status, description
FROM findings
WHERE pipeline_run_id = '<run-id>' AND issue_type = 'SECURITY'
ORDER BY CASE severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END;

-- 8. Full summary joined
SELECT r.id, r.status, r.total_issues_found, r.total_issues_fixed,
       COUNT(f.id) FILTER (WHERE f.status = 'CONFIRMED')    AS confirmed,
       COUNT(f.id) FILTER (WHERE f.status = 'NEEDS_HUMAN')  AS needs_human,
       COUNT(f.id) FILTER (WHERE f.issue_type = 'SECURITY') AS security_issues,
       r.pr_url,
       ROUND(EXTRACT(EPOCH FROM (r.completed_at - r.started_at))) AS secs
FROM pipeline_runs r
LEFT JOIN findings f ON f.pipeline_run_id = r.id
GROUP BY r.id ORDER BY r.started_at DESC;
```

---

## Observability

| Tool | URL | Purpose |
|---|---|---|
| **Temporal UI** | `http://localhost:8080` | Workflow execution trace, agent timings, retry history |
| **GraphiQL** | `http://localhost:8081/graphiql` | Query runs and findings interactively |
| **Actuator** | `http://localhost:8081/actuator/health` | App health |
| **Actuator Metrics** | `http://localhost:8081/actuator/metrics` | JVM, HTTP, DB pool metrics |
| **PostgreSQL** | `localhost:5432` | Direct DB access |

**Structured logging** — every log line includes:
- `correlationId` — unique per HTTP request, propagated into Temporal threads via `MdcContextPropagator`
- `workflowId` — Temporal workflow ID
- `runId` — pipeline run UUID

**Log format** (dev):
```
2026-06-28 11:08:13.235 [workflow-method-...] INFO  [corrId=abc] [wfId=code-review-...] [runId=a1de...] CodeReviewWorkflowImpl - [Agent-1] Clone complete. totalFiles=20
```

---

## Design Decisions

### Why Temporal.io?
Temporal provides durable execution — if the server restarts mid-pipeline, the workflow resumes exactly where it left off. No lost work, no re-analysis from scratch. Each activity (GitHub clone, LLM call, commit) is automatically retried on transient failures.

### Why the Adversarial Critic pattern?
LLMs hallucinate. Agent-2 (Analyser) will flag false positives. Agent-3 (Critic) is explicitly prompted to try to **refute** each finding. Only issues that survive the Critic's challenge are passed to the Fixer. In our test run: 17 of 54 issues were false positives — the Critic prevented 17 incorrect code patches.

### Why Human-in-the-loop?
The system raises a PR but never merges. An auto-merge of AI-generated code into production without human review would be irresponsible regardless of LLM confidence. The PR is the handoff point.

### Why only HIGH/MEDIUM with confidence ≥ 75% get fixed?
LOW severity issues and low-confidence findings are noted in the database but not patched. Fixing a LOW issue (e.g. unused import) risks introducing a diff that touches unrelated code. The threshold keeps patches targeted and reviewable.

### Why non-fatal email?
Email delivery failure should never abort a code review. The pipeline's primary job is to find bugs and raise a PR — notification is secondary. Email errors are logged as WARN and the workflow continues.

---

## Real Test Results

Tested against: `https://github.com/Skpandey15/auth-service-jwt`

| Metric | Result |
|---|---|
| Files reviewed | 19 Java files |
| Total issues found | 54 |
| False positives eliminated | 17 |
| Issues auto-fixed | 15 |
| Pipeline duration | 3 min 24 sec |
| PR raised | [pull/5](https://github.com/Skpandey15/auth-service-jwt/pull/5) |

**Sample HIGH severity fixes applied:**
- `SecurityConfig.java` — CSRF protection was disabled → enabled
- `LoginRequest.java` / `RegisterRequest.java` — passwords stored as `String` → `char[]`
- `KeyUtils.java` — path traversal vulnerability in file loading → fixed

**Sample false positives correctly eliminated by Critic:**
- Analyser flagged 6 imports in `JwtTokenGenerator.java` as unused — Critic correctly identified all 6 were actually used (`KeyFactory`, `RSAPrivateKey`, `PKCS8EncodedKeySpec`, `Instant`, `Base64`, `Date`)
- Analyser flagged `passwordHash` field as storing plain-text passwords — Critic correctly noted the field name implies hashing happens in the service layer

---

## Stopping the System

```bash
# Stop all containers
docker compose down

# Stop and remove all data (full reset)
docker compose down -v
```

---

*Built with Java 17 · Spring Boot 3.3.4 · LangChain4j 0.35.0 · Temporal.io 1.24.2 · GPT-4o*
