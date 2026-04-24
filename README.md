# GitHub Portfolio Intelligence Platform

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)
![React](https://img.shields.io/badge/React-19-61DAFB.svg)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-336791.svg)
![Redis](https://img.shields.io/badge/Redis-Upstash-DC382D.svg)
![Kafka](https://img.shields.io/badge/Kafka-Confluent-231F20.svg)
![Docker](https://img.shields.io/badge/build-Docker-2496ED.svg)
![GCP](https://img.shields.io/badge/deploy-Cloud%20Run-4285F4.svg)
![Vercel](https://img.shields.io/badge/frontend-Vercel-000000.svg)
![OpenAI](https://img.shields.io/badge/LLM-GPT--4o--mini-412991.svg)

A developer tool that analyzes GitHub repositories and generates recruiter-ready portfolio content — resume bullets, portfolio summaries, and interview narratives — by extracting structured evidence from build files, commit history, and GitHub API signals before calling the LLM.

**Live:** [github-to-portfolio-neon.vercel.app](https://github-to-portfolio-neon.vercel.app) · Backend: GCP Cloud Run · DB: Neon · Cache: Upstash Redis

---

## The Core Idea

Most portfolio generators pass the README to an LLM and hope for the best. This system does something different: before any AI call, it fetches and parses `pom.xml`, `package.json`, `Dockerfile`, GitHub Actions workflows, commit messages, contributor counts, and language bytes — then builds a structured `RepoSnapshot` with typed fields. The LLM receives a prompt filled with concrete facts, not vibes.

---

## System Architecture

```mermaid
graph TD
    User["User (Browser)"]
    FE["React + TypeScript<br/>Frontend<br/>Vercel"]
    BE["Spring Boot 3<br/>Backend API<br/>GCP Cloud Run"]
    PG["PostgreSQL<br/>Neon (serverless)"]
    Redis["Redis<br/>Upstash"]
    Kafka["Apache Kafka<br/>Confluent Cloud"]
    GitHub["GitHub API"]
    LLM["OpenAI GPT-4o-mini"]

    User --> FE
    FE -->|"REST /api/*"| BE
    BE -->|"OAuth2 + repo data"| GitHub
    BE -->|"structured prompt"| LLM
    BE -->|"entities + content"| PG
    BE -->|"job status cache"| Redis
    BE -.->|"analysis events<br/>(horizontal scale)"| Kafka
```

---

## Analysis Pipeline

Analysis jobs run on a Spring `@Async` thread pool (`analysisExecutor`, core=3, max=6). Each job executes three phases in sequence — evidence extraction, LLM generation, and persistence — with automatic retry (up to 3 attempts, exponential backoff).

```mermaid
sequenceDiagram
    participant C as Controller
    participant W as AsyncAnalysisWorker
    participant EE as EvidenceExtractor
    participant LLM as LlmService
    participant DB as PostgreSQL

    C->>W: run(jobId, repoId, userId) [@Async]
    W->>DB: transition → PROCESSING
    W->>EE: extract(repo, token)
    note over EE: Parallel GitHub API calls:<br/>file tree · README · config files<br/>commit count · contributors<br/>language bytes · recent commits
    EE-->>W: ExtractionResult
    W->>LLM: generate(repo, evidence)
    LLM-->>W: GeneratedPortfolioContent
    W->>DB: save RepoSnapshot + GeneratedContent
    W->>DB: transition → COMPLETED
```

A Kafka pipeline (`ExtractionStageConsumer` → `GenerationStageConsumer` → `PersistenceStageConsumer`) is implemented for horizontal scaling scenarios where multiple Cloud Run instances process jobs independently. On the current single-instance deployment, the `@Async` path is used to avoid Kafka consumer group rebalancing overhead on cold starts.

---

## Evidence Extraction Engine

The extraction engine collects signals from seven GitHub API endpoints concurrently and parses them into a typed `RepoSnapshot` before any LLM call.

```mermaid
graph LR
    subgraph "GitHub API (parallel)"
        A["File tree<br/>(structure signals)"]
        B["README<br/>(up to 4KB)"]
        C["Raw config files<br/>pom.xml · package.json<br/>Dockerfile · GH Actions"]
        D["Commit history<br/>(last 30 messages)"]
        E["Language bytes<br/>(breakdown %)"]
        F["Contributors list"]
        G["Repo metadata<br/>(stars, forks, dates)"]
    end

    subgraph "RepoSnapshot"
        H["Boolean signals<br/>hasDocker · hasCI<br/>hasMigrations · hasTests"]
        I["Parsed dependencies<br/>frameworks · versions<br/>test tools"]
        J["Quantitative metrics<br/>commit count · class count<br/>coverage % · throughput"]
        K["Commit signals<br/>feature detection from<br/>message patterns"]
        L["Project type<br/>Full Stack · Backend<br/>Frontend · CLI · etc."]
    end

    A --> H
    B --> J
    C --> I
    D --> K
    E --> J
    F --> J
    G --> J
    H --> L
    I --> L
```

---

## Data Model

```mermaid
erDiagram
    users {
        uuid id PK
        bigint github_id
        varchar login
        varchar name
        varchar email
        timestamp created_at
    }
    github_connections {
        uuid id PK
        uuid user_id FK
        text encrypted_access_token
        timestamp connected_at
    }
    repositories {
        uuid id PK
        uuid user_id FK
        bigint github_id
        varchar full_name
        varchar primary_language
        int stars
        int forks
        timestamp synced_at
    }
    repo_snapshots {
        uuid id PK
        uuid repository_id FK
        text readme_content
        jsonb detected_stack
        jsonb extracted_signals
        varchar project_type
        jsonb parsed_dependencies
        jsonb quantitative_metrics
        timestamp analyzed_at
    }
    generated_content {
        uuid id PK
        uuid repository_id FK
        varchar content_type
        text generated_text
        timestamp created_at
    }
    edited_content {
        uuid id PK
        uuid generated_content_id FK
        text edited_text
        timestamp updated_at
    }
    analysis_jobs {
        uuid id PK
        uuid user_id FK
        uuid repository_id FK
        varchar status
        int attempt
        timestamp created_at
        timestamp completed_at
        text error_message
    }

    users ||--o{ github_connections : "has"
    users ||--o{ repositories : "owns"
    repositories ||--o| repo_snapshots : "has"
    repositories ||--o{ generated_content : "has"
    repositories ||--o{ analysis_jobs : "has"
    generated_content ||--o| edited_content : "has"
```

---

## Job State Machine

```mermaid
stateDiagram-v2
    [*] --> QUEUED : job submitted
    QUEUED --> PROCESSING : worker picks up
    PROCESSING --> COMPLETED : content saved to DB
    PROCESSING --> RETRYING : transient failure, backoff
    RETRYING --> PROCESSING : retry attempt
    PROCESSING --> FAILED : 3 attempts exhausted
    RETRYING --> FAILED : 3 attempts exhausted
    COMPLETED --> [*]
    FAILED --> [*]
```

Job status is written to **both Redis and PostgreSQL** on every transition:
- **Redis** — O(1) reads for the frontend polling loop (24h TTL). Terminal states (COMPLETED/FAILED) always read from DB to prevent stale cache overrides.
- **PostgreSQL** — durable history, source of truth after Redis eviction

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query |
| Backend | Spring Boot 3.5, Java 21, Spring Security (OAuth2), Spring Data JPA |
| Database | PostgreSQL (Neon serverless), Flyway (7 migrations) |
| Cache / Job State | Redis (Upstash) |
| Messaging | Apache Kafka (Confluent Cloud, SASL/SSL) |
| LLM | OpenAI GPT-4o-mini via openai-java SDK |
| Containerization | Docker (multi-stage build), Docker Compose (local dev) |
| Infrastructure | GCP Cloud Run, Artifact Registry, Terraform |
| Frontend Hosting | Vercel |
| CI/CD | GitHub Actions (backend: test + build; frontend: typecheck + lint + build) |

---

## Production Deployment

The app runs on free-tier infrastructure:

| Service | Provider | Notes |
|---|---|---|
| Backend | GCP Cloud Run | Scale-to-zero, 1 GiB RAM, `prod` Spring profile |
| Frontend | Vercel | Static SPA, SPA catch-all rewrite |
| Database | Neon | Serverless Postgres, connection pooling |
| Redis | Upstash | Session store + job state cache, TLS |
| Kafka | Confluent Cloud | SASL PLAIN over SSL |

### Deploy from scratch

**Prerequisites:** GCP project, Neon DB, Upstash Redis, Confluent Cloud Kafka, GitHub OAuth App, OpenAI API key.

```bash
# 1. Configure
cp infra/terraform.tfvars.example infra/terraform.tfvars
# fill in all values

# 2. Auth + init
make setup

# 3. Create Artifact Registry, build image, deploy Cloud Run
make first-deploy

# 4. Copy the Cloud Run URL into terraform.tfvars as backend_url, then:
make deploy
```

### Redeploy after code changes

```bash
make push    # build + push :latest to Artifact Registry
gcloud run deploy portfolio-backend \
  --image us-central1-docker.pkg.dev/<project>/portfolio-backend/backend:latest \
  --region us-central1 --project <project>
```

---

## Local Development

### Prerequisites

- Docker + Docker Compose
- A GitHub OAuth App ([create one](https://github.com/settings/developers))
  - Homepage URL: `http://localhost:3000`
  - Callback URL: `http://localhost:8080/login/oauth2/code/github`
- An OpenAI API key

### 1. Configure environment

Create `backend/.env`:

```env
GITHUB_CLIENT_ID=your_github_oauth_app_client_id
GITHUB_CLIENT_SECRET=your_github_oauth_app_secret
TOKEN_ENCRYPTION_KEY=<run: openssl rand -base64 32>
LLM_API_KEY=your_openai_api_key
POSTGRES_PASSWORD=portfolio_dev
```

### 2. Start infrastructure + run services

```bash
# Start postgres + redis + kafka
docker compose up -d

# Terminal 1 — backend
cd backend && ./mvnw spring-boot:run

# Terminal 2 — frontend
cd frontend && npm install && npm run dev
```

Open [http://localhost:3000](http://localhost:3000)

### 3. Full Docker stack

```bash
docker compose --profile full up --build
```

---

## API Reference

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/me` | Current authenticated user |
| `POST` | `/api/repos/sync` | Sync GitHub repositories |
| `GET` | `/api/repos` | List synced repositories |
| `POST` | `/api/repos/analyze/batch` | Submit batch analysis |
| `POST` | `/api/repos/{repoId}/analyze` | Reanalyze a single repo |
| `GET` | `/api/jobs` | Paginated job list |
| `GET` | `/api/jobs/{jobId}` | Poll single job status |
| `GET` | `/api/projects` | All analyzed projects (workspace) |
| `GET` | `/api/projects/{repoId}/content` | Generated content for a repo |
| `PUT` | `/api/projects/{repoId}/content/{id}` | Save inline edit |

---

## Key Engineering Decisions

**Why separate evidence extraction from LLM generation?**
Without structured evidence, the LLM hallucinates specifics and produces generic bullets. By parsing `pom.xml` for dependency counts, commits for feature signals, and the GitHub API for contributor and language data, the prompt contains concrete facts. The model's job becomes formatting, not guessing.

**Why parallelize GitHub API calls?**
The extraction phase makes 8–12 GitHub API calls (README, file tree, config files, commit count, contributors, language bytes, recent commits). Running them sequentially adds 3–5 seconds of pure wait time. Using `CompletableFuture.allOf()` for the independent calls (everything except file tree, which gates config parsing) cuts extraction to ~1–2 seconds.

**Why `@Async` instead of the Kafka pipeline on Cloud Run?**
The Kafka pipeline was designed for horizontal scaling: independent consumer groups let each stage (extraction, generation, persistence) scale and retry separately. On a single Cloud Run instance with scale-to-zero, though, all four consumer groups rebalance with Confluent Cloud on every cold start — each taking 30–60 seconds. That overhead makes the first analysis after an idle period take 2–4 minutes before any work starts. The `@Async` worker runs the full pipeline on a local thread pool (no network round-trips for job dispatch), and the Kafka infrastructure remains in place for when horizontal scaling is needed.

**Why dual-write to Redis and PostgreSQL for job state?**
Redis makes frontend polling cheap (no DB query per poll, O(1) key lookup). PostgreSQL ensures job history survives Redis eviction. Terminal states (COMPLETED/FAILED) always read from the DB so that manual resets via SQL are immediately reflected without waiting for Redis TTL expiry.

**Why cross-origin session cookies instead of JWTs?**
The frontend (Vercel) and backend (Cloud Run) are on different origins. JWTs would require storing the token client-side and managing rotation. Instead, the backend uses `SameSite=None; Secure` session cookies backed by Redis (`spring-session-data-redis`), which survive Cloud Run scale-to-zero restarts and work naturally with Spring Security's existing OAuth2 session management.

---

## Project Structure

```
github-to-portfolio/
├── backend/
│   ├── src/main/java/com/portfolio/backend/
│   │   ├── config/          # Security, async executor, app config
│   │   ├── controller/      # REST endpoints
│   │   ├── entity/          # JPA entities + enums
│   │   ├── kafka/           # Topics, publisher, stage consumers, events
│   │   ├── repository/      # Spring Data JPA repositories
│   │   └── service/         # Analysis, evidence extraction, LLM, job state
│   └── src/main/resources/
│       ├── application.yml  # local / docker / staging / prod profiles
│       └── db/migration/    # Flyway V1–V7
├── frontend/
│   ├── src/                 # React + TypeScript
│   └── vercel.json          # SPA catch-all rewrite
├── infra/
│   ├── main.tf              # GCP Cloud Run + Artifact Registry
│   ├── variables.tf
│   ├── outputs.tf
│   └── terraform.tfvars.example
├── Makefile                 # setup · push · deploy · url targets
├── .github/workflows/       # Backend + frontend CI
└── docker-compose.yml       # Full local stack
```
