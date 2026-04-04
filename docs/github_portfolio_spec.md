# GitHub Portfolio Intelligence Platform
## Finalized System Specification

> **FAANG New Grad SWE Portfolio Project**

---

## What This Document Is

This is the finalized system specification for the GitHub Portfolio Intelligence Platform. Read it once to internalize the architecture, then refer back to specific sections while building. For the day-to-day build sequence, use the Action Plan document.

---

## 1. Project Overview

The GitHub Portfolio Intelligence Platform is a full-stack system that analyzes GitHub repositories and automatically generates professional portfolio and resume content for developers.

Users authenticate via GitHub OAuth, select repositories to analyze, and receive AI-generated outputs including portfolio summaries, resume bullets, tech stack detection, project tags, interview narratives, and one-sentence pitches. All outputs are persisted in a user dashboard for editing and reuse.

**Core problem being solved:** Developers build projects but struggle to communicate them effectively. READMEs are outdated. Manual rewriting is repetitive. Project presentation is inconsistent across applications. This system transforms raw GitHub repositories into structured professional narratives automatically.

---

## 2. MVP Definition

Lock this definition. Do not expand it until every item is complete.

### MVP Must Do These Things

1. User signs in with GitHub OAuth
2. User sees and can sync their public repositories
3. User selects 1–5 repos for analysis
4. Backend analyzes selected repos asynchronously
5. System generates: portfolio summary, resume bullets, tech stack, project tags
6. Generated content is saved to the database
7. User can revisit, edit, and export saved content

### Explicitly NOT Required for MVP

- Private repository support
- Kafka event pipeline
- Quality scoring and ranking
- Webhook auto-sync
- Recruiter analytics
- Advanced commit intelligence
- Interview story mode (Phase 2 feature)
- README improvement suggestions

---

## 3. System Architecture

### 3.1 High-Level Architecture

The system follows a standard three-tier architecture with async job processing layered on top.

| Layer | Responsibility |
|---|---|
| Frontend | React + TypeScript, communicates with backend via REST API |
| Backend | Spring Boot, handles auth, GitHub integration, orchestration, LLM calls |
| Database | PostgreSQL (Neon), stores users, repos, jobs, and generated content |
| Cache / Jobs | Redis, used for caching GitHub API responses and job state coordination |
| LLM | External LLM API, called by backend for content generation |
| Event Pipeline | Kafka — Phase 2 only. Do not build before async Redis jobs are working |

### 3.2 Technology Stack

| Technology | Purpose |
|---|---|
| React + TypeScript | Frontend framework |
| Vite | Build tooling |
| Tailwind CSS | Styling |
| TanStack Query | Server state management and polling |
| Java 21 + Spring Boot | Backend framework |
| Spring Security + OAuth2 | Authentication layer |
| Spring Data JPA + Hibernate | ORM and persistence |
| PostgreSQL (Neon) | Primary database |
| Redis | Caching and async job coordination |
| Docker + Docker Compose | Local dev environment and containerization |
| GitHub Actions | CI/CD pipelines |
| Kafka | Phase 2 only — event-driven pipeline upgrade |

---

## 4. Core Components

### 4.1 Evidence Extraction Engine

This is the most technically interesting part of the system. Do not skip it. This is what you will talk about in interviews.

Instead of relying solely on README files, the extractor pulls signals from multiple repository sources and builds a structured intelligence model before generation.

| Evidence Source | What It Reveals |
|---|---|
| Repository Metadata | Name, description, topics, stars, forks, language breakdown, last commit timestamp |
| README / Docs | Primary documentation content and folder structure |
| Config Files | package.json, requirements.txt, pom.xml, build.gradle, Cargo.toml — reveals frameworks and dependencies |
| Infrastructure Files | Dockerfile, docker-compose.yml, GitHub Actions workflows — signals deployment readiness |
| Directory Structure | src/, controllers/, services/, models/ — infers architectural patterns |
| Commit History | Recent commit messages parsed for feature signals: auth, real-time, caching, refactors, CI/CD |

The extractor outputs a structured `RepoSnapshot` model:

```json
{
  "projectName": "Restaurant POS",
  "projectType": "Full Stack Web Application",
  "stack": ["React", "TypeScript", "FastAPI", "PostgreSQL", "Docker"],
  "features": ["authentication", "real-time notifications", "order management API"],
  "signals": {
    "hasDocker": true,
    "hasCI": true,
    "hasTests": false
  }
}
```

### 4.2 AI Content Generation

The generation engine uses the structured `RepoSnapshot` as its prompt context — not raw README text. This is what produces better results than a naive LLM wrapper.

| Output Type | Description |
|---|---|
| Portfolio Summary | 2–3 sentence professional description of the project and its impact |
| Resume Bullets | 3–5 action-verb bullets quantifying technical decisions and scope |
| Tech Stack | Automatically detected technologies with no hallucination (pulled from signals) |
| Project Tags | Full Stack / Backend / DevOps / ML etc — for filtering and categorization |
| Interview Story | Phase 2 — narrative covering purpose, architecture, challenge, tradeoff, future work |
| One-Sentence Pitch | Phase 2 — single-line project summary for LinkedIn / cold outreach |

### 4.3 Async Job Processing

Repository analysis is resource-intensive and must not block the request thread. Use Redis for job coordination before introducing Kafka.

| Job State | Meaning |
|---|---|
| PENDING | Job created, not yet queued |
| QUEUED | Submitted to background worker |
| PROCESSING | Worker actively running extraction and generation |
| COMPLETED | All outputs saved to database |
| FAILED | Error encountered — expose error message to frontend |
| RETRYING | Transient failure, worker retrying with backoff |

---

## 5. Database Schema

| Entity | Key Fields |
|---|---|
| User | id, githubId, username, email, avatarUrl, createdAt |
| GitHubConnection | id, userId, encryptedAccessToken, scopes, connectedAt |
| Repository | id, userId, githubRepoId, name, description, visibility, stars, forks, primaryLanguage |
| RepoSnapshot | id, repositoryId, readmeContent, detectedStack, extractedSignals, analyzedAt |
| AnalysisJob | id, userId, repositoryId, status, createdAt, startedAt, completedAt, errorMessage |
| GeneratedContent | id, repositoryId, contentType, generatedText, createdAt |
| EditedContent | id, generatedContentId, editedText, updatedAt |

---

## 6. API Reference

### Authentication

| Endpoint | Purpose |
|---|---|
| GET /auth/github/login | Redirect to GitHub OAuth consent screen |
| GET /auth/github/callback | Handle OAuth callback, create/update user, store token |
| POST /auth/logout | Invalidate session |
| GET /api/me | Return authenticated user profile |

### Repositories

| Endpoint | Purpose |
|---|---|
| GET /api/repos | List synced repos for authenticated user |
| POST /api/repos/sync | Fetch repos from GitHub and refresh database |
| POST /api/repos/analyze | Submit repos for analysis, returns array of job IDs |
| GET /api/repos/{repoId} | Get details for a single repo |

### Jobs

| Endpoint | Purpose |
|---|---|
| GET /api/jobs/{jobId} | Poll job status and progress |
| GET /api/jobs | List all jobs for authenticated user |

### Projects / Generated Content

| Endpoint | Purpose |
|---|---|
| GET /api/projects | List all analyzed projects with summaries |
| GET /api/projects/{repoId}/content | Get all generated content for a repo |
| PUT /api/projects/{repoId}/content/{contentId} | Save edited version of a content block |
| POST /api/projects/{repoId}/reanalyze | Trigger fresh analysis of a previously analyzed repo |

---

## 7. Frontend Pages

| Page | Contents |
|---|---|
| Landing Page | Product intro, GitHub login button, value proposition |
| Repository Dashboard | Synced repo cards with search, language filter, multi-select, Analyze button |
| Analysis Status | Job progress cards, real-time polling, success/failure states |
| Project Results | Generated portfolio summary, bullets, stack, tags with inline editing controls |
| Saved Workspace | All previously analyzed projects, re-analyze and export options |

---

## 8. Security & Production Requirements

- Store GitHub access tokens encrypted at rest — never in plaintext
- All GitHub API calls are server-side only — token never sent to frontend
- Rate limit GitHub API calls using Redis to track usage per user
- Validate and sanitize all inputs before passing to LLM prompts
- Use environment-specific config profiles: local / staging / prod
- Add retry logic with exponential backoff for GitHub API and LLM failures
- Paginate all list endpoints — never return unbounded result sets
- Add DB indexes on userId and repositoryId foreign keys
