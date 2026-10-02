-- V2 (Phase 7 maintenance change, release v1.1.0)
-- Backward compatible: a nullable column, no default rewrite, old code keeps working.
ALTER TABLE links ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN links.last_accessed_at IS 'UTC time of the most recent successful redirect (NULL = never clicked)';
