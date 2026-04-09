-- Phase 9: Production hardening — additional indexes

-- generated_content: filtering by type is a common query pattern
CREATE INDEX idx_generated_content_content_type ON generated_content(content_type);

-- generated_content: sorting by created_at on the workspace query
CREATE INDEX idx_generated_content_created_at ON generated_content(created_at DESC);

-- repo_snapshots: look up by analyzed_at for freshness checks
CREATE INDEX idx_repo_snapshots_analyzed_at ON repo_snapshots(analyzed_at DESC);
