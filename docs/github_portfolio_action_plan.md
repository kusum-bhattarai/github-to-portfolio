# GitHub Portfolio Intelligence Platform
## Build Action Plan

> **FAANG New Grad SWE Portfolio Project**

---

## The Prime Directive

> First make it work. Then make it smart. Then make it scalable.

- Do not introduce Kafka before async Redis jobs are fully working
- Do not build interview story mode before basic analysis is solid
- One visible working deliverable per week — no exceptions

---

## Build Strategy — Priority Order

When in doubt about what to work on next, follow this order:

1. **Core user value** — auth, sync, analysis, saved outputs
2. **Quality of generation** — evidence extraction, commit signals, editing controls
3. **Infrastructure** — Redis jobs, Docker, CI/CD pipelines
4. **Advanced architecture** — Kafka, observability, ranking system

---

## Phase-by-Phase Plan

### Phase 0 — Setup

**Goal:** Dev environment up and running

**Key Tasks:**
- Create Spring Boot backend and React + TypeScript frontend projects
- Write `docker-compose.yml` with containers for backend, frontend, postgres, redis
- Set up branch strategy: `main` / `dev` / feature branches
- Configure environment variable strategy

**Done When:** One command boots the full local stack and both services start

---

### Phase 1 — GitHub Auth

**Goal:** GitHub login works end-to-end

**Key Tasks:**
- Configure Spring Security OAuth2 with GitHub provider
- Create `User` and `GitHubConnection` entities
- Encrypt and store GitHub access token on first login
- Expose `GET /api/me`
- Build frontend login flow and session handling

**Done When:** User can log in, refresh the page, and remain recognized

> Note: Do not attempt private repo access at this stage.

---

### Phase 2 — Repository Sync

**Goal:** User can browse their repositories

**Key Tasks:**
- Integrate GitHub repo listing API
- Create `Repository` entity and persistence layer
- Implement `POST /api/repos/sync` and `GET /api/repos`
- Build dashboard UI with repo cards, search, language filter, and multi-select
- Add "Analyze selected repos" button

**Done When:** User can log in, sync repos, and select a set to analyze

---

### Phase 3 — Basic Analysis (Synchronous First)

**Goal:** One complete end-to-end analysis loop

**Key Tasks:**
- Fetch README and repo metadata for selected repo
- Build a simple LLM prompt that produces: portfolio summary, resume bullets, tech stack, tags
- Create `GeneratedContent` entity and persistence
- Expose `GET /api/projects/{repoId}/content`
- Display results on the frontend

**Done When:** A GitHub repo goes in, generated content comes out, and it is saved to the database

> Important: Keep this synchronous for now. You are proving the concept, not building the final architecture.

---

### Phase 4 — Evidence Extraction Engine

**Goal:** Smarter summaries that don't depend on README quality

**Key Tasks:**
- Create `RepoSnapshot` entity
- Build config file parser for: `package.json`, `requirements.txt`, `pom.xml`, `build.gradle`, `Dockerfile`, GitHub Actions workflows
- Build stack detector, project type classifier, and infrastructure signal extractor
- Persist structured evidence snapshot before every generation pass

**Done When:** You can inspect any repo and produce a structured `RepoSnapshot` before calling the LLM

> This is the phase that separates this project from a naive LLM wrapper. Invest time here.

---

### Phase 5 — Persistent Workspace

**Goal:** The app feels like a product, not a demo

**Key Tasks:**
- Build results dashboard separating analyzed repos from raw synced repos
- Add inline editing UI for generated content blocks
- Create `EditedContent` entity and persistence
- Add copy-to-clipboard, last-analyzed timestamps, and reanalyze button

**Done When:** A user can log in, see previously analyzed repos, edit content, save edits, and return later to find them

---

### Phase 6 — Async Job Processing

**Goal:** Analysis runs in the background without blocking the UI

**Key Tasks:**
- Create `AnalysisJob` entity with full state machine: PENDING → QUEUED → PROCESSING → COMPLETED / FAILED / RETRYING
- Move repo analysis into background workers
- Use Redis for job coordination and progress state
- Update `POST /api/repos/analyze` to return job IDs
- Build frontend polling against `GET /api/jobs/{jobId}`

**Done When:** Multiple repos can be submitted simultaneously and processed in the background with visible progress

> This is the point where the project starts feeling production-level.

---

### Phase 7 — Commit History Intelligence

**Goal:** Better feature detection using project evolution signals

**Key Tasks:**
- Fetch last N commits per selected repo via GitHub API
- Parse commit messages for feature signals: auth, real-time, Docker, caching, CI/CD, refactors, optimizations
- Merge commit-derived evidence into the structured `RepoSnapshot`

**Done When:** The system can describe a project's history and evolution, not just its current file state

> This is a strong differentiator. Commit signals catch things config files miss.

---

### Phase 8 — Interview Story Mode

**Goal:** Each repo produces both recruiter-facing and interview-facing content

**Key Tasks:**
- Add Interview Story output type covering: purpose, architecture, challenge, tradeoff, future improvements
- Add One-Sentence Pitch output type
- Add Talking Points list output type
- Surface all output types on the results page with toggle controls

**Done When:** A user can click any analyzed repo and get content suitable for both a resume and a live interview

---

### Phase 9 — Production Hardening

**Goal:** System is reliable enough for real users

**Key Tasks:**
- Retry logic with exponential backoff for GitHub API and LLM failures
- Idempotency handling for analysis jobs
- Structured logging throughout the backend
- Rate limit handling using Redis counters
- DB indexes on `userId` and `repositoryId` foreign keys
- API pagination on all list endpoints
- Environment-specific config profiles: local / staging / prod

**Done When:** You would be comfortable letting real users try it without babysitting it

---

### Phase 10 — CI/CD and Containerization

**Goal:** Build pipeline is professional and automatic

**Key Tasks:**
- Write backend and frontend Dockerfiles
- Finalize `docker-compose.yml` with all services
- GitHub Actions — backend: test + build + Docker image
- GitHub Actions — frontend: typecheck + test + build
- Optional: deploy workflow to cloud provider

**Done When:** Every push to `main` is automatically validated without manual intervention

---

### Phase 11 — Kafka Event-Driven Upgrade

**Goal:** Analysis pipeline becomes fully decoupled and scalable

**Key Tasks:**
- Define Kafka topics: `repo.analysis.requested`, `repo.evidence.extracted`, `repo.generation.completed`, `repo.generation.failed`
- Split workers by responsibility: ingestion / extraction / generation / persistence
- Implement dead letter queue handling for repeated failures

**Done When:** Analysis stages are fully decoupled and communicate only through message streams

> Do this after Phase 10. It is impressive but not required for portfolio purposes.

---

## Weekly Timeline

### Option A — Strong MVP (6 Weeks)

*Recommended if building 15–20 hrs/week alongside job hunting.*

| Week | Focus | Deliverable |
|---|---|---|
| Week 1 | Project setup + GitHub OAuth + User entities | docker-compose boots, GitHub login works end-to-end |
| Week 2 | Repository sync + dashboard UI | User can see repos, search/filter, and multi-select for analysis |
| Week 3 | Basic synchronous analysis + persistence | One repo can be fully analyzed and results saved to DB |
| Week 4 | Evidence extraction engine v1 | Structured RepoSnapshot built from config files and metadata |
| Week 5 | Editing workspace + Redis async jobs | Outputs editable and persistent, analysis runs in background |
| Week 6 | CI/CD + Docker polish + deployment | GitHub Actions passes, app runs fully containerized |

### Option B — Full Version (10 Weeks)

*Recommended if this is your flagship portfolio project.*

| Week | Focus | Deliverable |
|---|---|---|
| Weeks 1–2 | Core setup, auth, repo sync | Full authentication and dashboard working |
| Weeks 3–4 | Basic analysis + persistence | End-to-end analysis loop with saved results |
| Weeks 5–6 | Evidence extraction + async jobs | Structured extraction + Redis background processing |
| Weeks 7–8 | Commit intelligence + interview mode | History-based signals + narrative content outputs |
| Weeks 9–10 | Production hardening + CI/CD + Docker | Reliable, tested, containerized, deployable system |

> Add Kafka (Phase 11) only after Week 10 if time permits.

---

## First 10 Concrete Steps

If you're sitting down right now wondering where to start, do this in order:

1. Create the Spring Boot backend project with Web, Security, JPA, and OAuth2 Client dependencies
2. Create the React + TypeScript frontend project with Vite and Tailwind
3. Write `docker-compose.yml` with containers for backend, frontend, postgres, and redis
4. Confirm the full local stack boots with one command
5. Register a GitHub OAuth App and configure Spring Security with client ID and secret
6. Implement GitHub login and store the `User` entity to Postgres on first login
7. Expose `GET /api/me` and confirm the frontend can display the logged-in user
8. Implement `POST /api/repos/sync` to fetch repos from the GitHub API and persist them
9. Build the repository dashboard with repo cards, search, and multi-select
10. Build `POST /api/repos/analyze` (synchronous first) and display the generated results

---

## GitHub Project Board Setup

Set this up before writing any code. One hour of setup pays off every week.

| Epic | Scope |
|---|---|
| Epic 1 — Auth & Onboarding | GitHub OAuth, session management, user persistence, GET /api/me |
| Epic 2 — Repo Ingestion | Repo sync API, dashboard listing, search / filter / select UI |
| Epic 3 — Analysis Pipeline | README fetch, metadata fetch, evidence extraction, LLM generation |
| Epic 4 — Persistence & Workspace | Generated content, edited content, saved dashboard, reanalyze |
| Epic 5 — Async Processing | Job state machine, Redis coordination, polling UI, retry logic |
| Epic 6 — Developer Storytelling | Interview mode, one-liner, talking points, narrative outputs |
| Epic 7 — Productionization | Docker, CI/CD, structured logging, retries, rate limiting, indexes |
| Epic 8 — Event-Driven Scale | Kafka topics, decoupled workers, failure handling, dead letter queues |

---

## Portfolio-Ready Checklist

Your project is portfolio-ready when every item below is true.

- [ ] GitHub OAuth login works reliably
- [ ] Repository dashboard loads and syncs correctly
- [ ] Evidence extraction engine runs and persists a structured RepoSnapshot
- [ ] AI-generated content is useful — not generic or hallucinated
- [ ] Async job processing works — analysis does not block the UI
- [ ] Generated content persists across sessions and is editable
- [ ] Application is fully containerized with Docker Compose
- [ ] CI pipeline runs automatically on every push to main
- [ ] You can explain every architectural decision and the tradeoffs you made
- [ ] You have one interview story ready from the evidence extraction phase

---

## The One Rule That Will Make or Break This Project

The evidence extraction engine is your interview story. Everything else is infrastructure.

When an interviewer asks *"tell me about a technically interesting decision you made,"* your answer comes from how you modeled repository signals, weighted evidence sources, and designed the structured context passed to the LLM. Invest here. Do it carefully. Know every tradeoff you made and why.

---

*Build it. Ship it. Tell the story.*
