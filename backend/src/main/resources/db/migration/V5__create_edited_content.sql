-- Phase 5: Persistent Workspace
-- Stores user-edited versions of generated content blocks

CREATE TABLE edited_content (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generated_content_id UUID NOT NULL REFERENCES generated_content(id) ON DELETE CASCADE,
    edited_text          TEXT NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_edited_content_generated_content UNIQUE (generated_content_id)
);

CREATE INDEX idx_edited_content_generated_content_id ON edited_content(generated_content_id);
