-- Phase 4: Evidence Extraction Engine
-- Add structured evidence fields to repo_snapshots

ALTER TABLE repo_snapshots
    ADD COLUMN IF NOT EXISTS project_type        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS parsed_dependencies JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS quantitative_metrics JSONB NOT NULL DEFAULT '{}';

COMMENT ON COLUMN repo_snapshots.project_type          IS 'Classified type: Full Stack Web App, Backend API, Frontend SPA, ML/AI, DevOps, CLI Tool, Library';
COMMENT ON COLUMN repo_snapshots.parsed_dependencies   IS 'Structured deps parsed from package.json, pom.xml, requirements.txt, build.gradle, etc.';
COMMENT ON COLUMN repo_snapshots.quantitative_metrics  IS 'Numeric signals: commitCount, contributorCount, testFileCount, totalFiles, languageBytes, etc.';
