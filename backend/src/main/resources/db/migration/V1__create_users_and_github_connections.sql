CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_id   BIGINT  NOT NULL UNIQUE,
    username    VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    avatar_url  VARCHAR(1024),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE github_connections (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    encrypted_access_token  TEXT NOT NULL,
    scopes                  VARCHAR(512),
    connected_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_github_connections_user UNIQUE (user_id)
);

CREATE INDEX idx_users_github_id ON users(github_id);
CREATE INDEX idx_github_connections_user_id ON github_connections(user_id);
