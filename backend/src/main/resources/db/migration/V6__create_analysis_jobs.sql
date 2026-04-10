-- Phase 6: Async Job Processing
-- Full job state machine: PENDING → QUEUED → PROCESSING → COMPLETED / FAILED / RETRYING

CREATE TABLE analysis_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    error_message TEXT,
    attempt       INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_analysis_jobs_user_id ON analysis_jobs(user_id);
CREATE INDEX idx_analysis_jobs_repository_id ON analysis_jobs(repository_id);
CREATE INDEX idx_analysis_jobs_status ON analysis_jobs(status);
