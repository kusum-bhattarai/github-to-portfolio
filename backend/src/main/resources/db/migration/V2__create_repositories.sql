CREATE TABLE repositories (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_repo_id   BIGINT NOT NULL,
    name             VARCHAR(255) NOT NULL,
    full_name        VARCHAR(512) NOT NULL,
    description      TEXT,
    visibility       VARCHAR(50) NOT NULL DEFAULT 'public',
    primary_language VARCHAR(100),
    stars            INTEGER NOT NULL DEFAULT 0,
    forks            INTEGER NOT NULL DEFAULT 0,
    topics           TEXT[],
    html_url         VARCHAR(1024),
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_repositories_user_github UNIQUE (user_id, github_repo_id)
);

CREATE INDEX idx_repositories_user_id ON repositories(user_id);
CREATE INDEX idx_repositories_github_repo_id ON repositories(github_repo_id);
