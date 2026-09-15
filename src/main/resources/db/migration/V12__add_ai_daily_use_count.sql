ALTER TABLE ai_daily_usage
    ADD COLUMN use_count BIGINT NOT NULL DEFAULT 0 CHECK (use_count >= 0);
