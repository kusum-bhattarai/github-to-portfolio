CREATE TABLE repo_snapshots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id     UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    readme_content    TEXT,
    detected_stack    TEXT[],
    extracted_signals JSONB NOT NULL DEFAULT '{}',
    analyzed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_repo_snapshots_repository UNIQUE (repository_id)
);

CREATE TABLE generated_content (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    content_type  VARCHAR(100) NOT NULL,
    generated_text TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repo_snapshots_repository_id ON repo_snapshots(repository_id);
CREATE INDEX idx_generated_content_repository_id ON generated_content(repository_id);
