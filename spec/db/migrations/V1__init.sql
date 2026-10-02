-- SnapLink schema v1 (shared by all tracks).
-- Applied in order by each track's tiny migrator; applied versions are recorded in schema_migrations.

CREATE TABLE links (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    long_url    TEXT         NOT NULL CHECK (char_length(long_url) <= 2048),
    is_custom   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    click_count BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_links_code UNIQUE (code)
);

-- Only rows that can expire need this index (partial index keeps it small).
CREATE INDEX idx_links_expires_at ON links (expires_at) WHERE expires_at IS NOT NULL;

-- One row per link per UTC day. Small, pre-aggregated, cheap to read for charts.
CREATE TABLE click_daily (
    link_id BIGINT NOT NULL REFERENCES links (id) ON DELETE CASCADE,
    day     DATE   NOT NULL,
    clicks  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (link_id, day)
);
